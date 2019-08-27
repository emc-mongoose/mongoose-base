package com.emc.mongoose.base.svc.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;

public abstract class MatchingRequestHandlerBase
extends ChannelInboundHandlerAdapter {

	@Override
	public final void channelRead(final ChannelHandlerContext ctx, final Object msg) {
		final var req = (FullHttpRequest) msg;
		if(match(req)) {
			handle(ctx, req);
		} else {
			ctx.fireChannelRead(msg);
		}
		ReferenceCountUtil.release(msg);
	}

	protected abstract boolean match(final FullHttpRequest req);

	protected abstract void handle(final ChannelHandlerContext ctx, final FullHttpRequest req);

	protected static void writeEmptyContentResponse(final ChannelHandlerContext ctx, final FullHttpResponse resp) {
		resp.headers().add(CONTENT_LENGTH, 0);
		ctx.writeAndFlush(resp);
	}
}
