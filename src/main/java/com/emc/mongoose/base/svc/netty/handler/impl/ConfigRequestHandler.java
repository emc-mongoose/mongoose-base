package com.emc.mongoose.base.svc.netty.handler.impl;

import com.emc.mongoose.base.config.ConfigFormat;
import com.emc.mongoose.base.config.ConfigUtil;
import com.emc.mongoose.base.svc.netty.handler.UriMatchingRequestHandlerBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.io.yaml.TypeNames;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;

import static com.emc.mongoose.base.config.ConfigFormat.JSON;
import static com.emc.mongoose.base.config.ConfigFormat.YAML;
import static com.emc.mongoose.base.config.ConfigUtil.writerWithPrettyPrinter;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public final class ConfigRequestHandler
extends UriMatchingRequestHandlerBase {

	private final Config config;

	public ConfigRequestHandler(final Config config) {
		this.config = config;
	}

	@Override
	protected final String uriStartsWith() {
		return "/config";
	}

	@Override
	protected final void handle(final ChannelHandlerContext ctx, final FullHttpRequest req) {
		final var schemaFlag = req.uri().equals(uriStartsWith() + "/schema");
		final var reqHeaders = req.headers();
		final var reqHeaderAccept = reqHeaders.get(ACCEPT);
		final ConfigFormat format;
		if(APPLICATION_JSON.toString().equals(reqHeaderAccept)) {
			format = JSON;
		} else {
			format = YAML;
		}
		final String respTxt;
		try {
			if(schemaFlag) {
				respTxt = schemaText(format);
			} else {
				respTxt = ConfigUtil.toString(config, format);
			}
			final var respContent = Unpooled.wrappedBuffer(respTxt.getBytes());
			final var respHeaders = new DefaultHttpHeaders();
			respHeaders.add(CONTENT_LENGTH, respContent.readableBytes());
			switch(format) {
				case JSON:
					respHeaders.add(CONTENT_TYPE, APPLICATION_JSON);
					break;
				case YAML:
					respHeaders.add(CONTENT_TYPE, "application/yaml");
					break;
			}
			final var resp = new DefaultFullHttpResponse(
				HTTP_1_1, OK, respContent, respHeaders, EmptyHttpHeaders.INSTANCE
			);
			ctx.writeAndFlush(resp);
		} catch(final Exception e) {
			final var resp = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR);
			ctx.writeAndFlush(resp);
		}
	}

	String schemaText(final ConfigFormat format)
	throws JsonProcessingException {
		final ObjectMapper mapper;
		switch (format) {
			case JSON:
				mapper = new ObjectMapper();
				break;
			case YAML:
				mapper = new YAMLMapper();
				break;
			default:
				throw new AssertionError();
		}
		var schemaTxt = writerWithPrettyPrinter(mapper).writeValueAsString(config.schema());
		for (final var k : TypeNames.MAP.keySet()) {
			final var v = TypeNames.MAP.get(k).getTypeName();
			schemaTxt = schemaTxt.replaceAll(v, k);
		}
		return schemaTxt;
	}
}