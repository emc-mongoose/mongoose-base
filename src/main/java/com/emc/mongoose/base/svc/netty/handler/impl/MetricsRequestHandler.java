package com.emc.mongoose.base.svc.netty.handler.impl;

import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.svc.netty.handler.UriMatchingRequestHandlerBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.apache.logging.log4j.Level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashSet;

import static com.emc.mongoose.base.Constants.MIB;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public final class MetricsRequestHandler
extends UriMatchingRequestHandlerBase {

	private final CollectorRegistry registry;

	public MetricsRequestHandler() {
		this(CollectorRegistry.defaultRegistry);
	}

	public MetricsRequestHandler(final CollectorRegistry registry) {
		this.registry = registry;
	}

	@Override
	protected final String uriStartsWith() {
		return "/metrics";
	}

	@Override
	protected final void handle(final ChannelHandlerContext ctx, final FullHttpRequest req) {
		// parse request
		final var reqQueryDecoder = new QueryStringDecoder(req.uri());
		final var includedParam = reqQueryDecoder.parameters().getOrDefault("name[]", Collections.emptyList());
		final var filteredMetrics = registry.filteredMetricFamilySamples(new HashSet<>(includedParam));
		var respContent = (ByteBuf) null;
		try(
			final var out = new ByteArrayOutputStream(MIB);
			final var writer = new OutputStreamWriter(out);
		) {
			TextFormat.write004(writer, filteredMetrics);
			respContent = Unpooled.wrappedBuffer(out.toByteArray());
		} catch(final IOException e) {
			LogUtil.exception(Level.ERROR, e, "Unexpected failure");
		}
		// make response
		if(null == respContent) {
			final var resp = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR);
			writeEmptyContentResponse(ctx, resp);
		} else {
			final var resp = new DefaultFullHttpResponse(HTTP_1_1, OK, respContent);
			final var respHeaders = resp.headers();
			respHeaders.add(CONTENT_LENGTH, respContent.readableBytes());
			respHeaders.add(CONTENT_TYPE, TextFormat.CONTENT_TYPE_004);
			ctx.writeAndFlush(resp);
		}
	}
}
