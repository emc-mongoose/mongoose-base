package com.emc.mongoose.base.svc.http.handler.impl;

import com.emc.mongoose.base.svc.http.handler.UriPrefixMatchingRequestHandlerBase;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

@ChannelHandler.Sharable
public class LoadStepRequestHandler
extends UriPrefixMatchingRequestHandlerBase {

	@Override
	protected final String uriPrefix() {
		return "/load/step";
	}

	@Override
	protected final void handle(final ChannelHandlerContext ctx, final FullHttpRequest req) {
	}
}