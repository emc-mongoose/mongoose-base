package com.emc.mongoose.base.svc.http.handler;

import com.emc.mongoose.base.logging.LogUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.logging.log4j.Level;

public abstract class MatchingRequestHandlerBase
extends ChannelInboundHandlerAdapter {

	@Override
	public final void channelRead(final ChannelHandlerContext ctx, final Object msg)
	throws Exception {
		final var req = (FullHttpRequest) msg;
		if(match(req)) {
			handle(ctx, req);
		}
		super.channelRead(ctx, msg);
	}

	@Override
	public final void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
	throws Exception {
		LogUtil.exception(Level.WARN, cause, "Request handler failure");
		super.exceptionCaught(ctx, cause);
	}

	protected abstract boolean match(final FullHttpRequest req);

	protected abstract void handle(final ChannelHandlerContext ctx, final FullHttpRequest req);
}
