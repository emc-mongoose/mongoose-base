package com.emc.mongoose.base.svc.http.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public interface CorsResponseUtil {

	String HEADER_NAME_PREFIX_AC = "Access-Control";
	String HEADER_NAME_PREFIX_ACA = HEADER_NAME_PREFIX_AC + "-Allow";
	String HEADER_VALUE_ACA_METHODS = "DELETE,GET,HEAD,POST,PUT,OPTIONS";

	String HEADER_NAME_ACA_ORIGIN = HEADER_NAME_PREFIX_ACA + "-Origin";
	String HEADER_VALUE_ACA_ORIGIN = "*";

	String HEADER_NAME_ACA_HEADERS = HEADER_NAME_PREFIX_ACA + "-Headers";
	String HEADER_NAME_ACA_METHODS = HEADER_NAME_PREFIX_ACA + "-Methods";
	String HEADER_NAME_AC_EXPOSE_HEADERS = HEADER_NAME_PREFIX_AC + "-Expose-Headers";
	String ACA_HEADERS = "Origin, X-Requested-With, Content-Type, Accept, ETag, If-Match";


	static void writeEmptyContentResponse(final ChannelHandlerContext ctx, final FullHttpResponse resp) {
		final var respHeaders = resp.headers();
		respHeaders.add(CONTENT_LENGTH, 0);
		appendCorsHeaders(respHeaders);
		ctx.writeAndFlush(resp);
	}

	static void respondEmptyContent(final ChannelHandlerContext ctx, final HttpResponseStatus status) {
		final FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status);
		writeEmptyContentResponse(ctx, resp);
	}

	static void respondContent(
		final ChannelHandlerContext ctx, final HttpResponseStatus status, final ByteBuf content,
		final String contentType
	) {
		final FullHttpResponse resp = new DefaultFullHttpResponse(HTTP_1_1, status, content);
		final var respHeaders = resp.headers();
		respHeaders.add(CONTENT_LENGTH, content.readableBytes());
		respHeaders.add(CONTENT_TYPE, contentType);
		appendCorsHeaders(respHeaders);
		ctx.writeAndFlush(resp);
	}
	
	static void appendCorsHeaders(final HttpHeaders headers) {
		headers.set(HEADER_NAME_AC_EXPOSE_HEADERS, ACA_HEADERS);
		headers.set(HEADER_NAME_ACA_HEADERS, ACA_HEADERS);
		headers.set(HEADER_NAME_ACA_METHODS, HEADER_VALUE_ACA_METHODS);
		headers.set(HEADER_NAME_ACA_ORIGIN, HEADER_VALUE_ACA_ORIGIN);
	}
}
