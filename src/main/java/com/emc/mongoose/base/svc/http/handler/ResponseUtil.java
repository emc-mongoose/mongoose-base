package com.emc.mongoose.base.svc.http.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public interface ResponseUtil {

	static void writeEmptyContentResponse(final ChannelHandlerContext ctx, final FullHttpResponse resp) {
		final var respHeaders = resp.headers();
		respHeaders.add(CONTENT_LENGTH, 0);
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
		ctx.writeAndFlush(resp);
	}
}
