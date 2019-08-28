package com.emc.mongoose.base.svc.http.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;

public abstract class MatchingRequestHandlerBase
extends ChannelInboundHandlerAdapter {

	@Override
	public final void channelRead(final ChannelHandlerContext ctx, final Object msg) {
		final var req = (FullHttpRequest) msg;
		if(match(req)) {
			handle(ctx, req);
		}
		ctx.fireChannelRead(msg);
	}

	protected abstract boolean match(final FullHttpRequest req);

	protected abstract void handle(final ChannelHandlerContext ctx, final FullHttpRequest req);
}
