package com.emc.mongoose.base.svc.http.handler;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.FullHttpRequest;

@ChannelHandler.Sharable
public abstract class UriMatchingRequestHandlerBase
extends MatchingRequestHandlerBase {

	@Override
	protected final boolean match(final FullHttpRequest req) {
		final var reqUri = req.uri();
		return reqUri.startsWith(uriStartsWith());
	}

	protected abstract String uriStartsWith();
}