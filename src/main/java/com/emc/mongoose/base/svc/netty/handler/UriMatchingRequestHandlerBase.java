package com.emc.mongoose.base.svc.netty.handler;

import io.netty.handler.codec.http.FullHttpRequest;

public abstract class UriMatchingRequestHandlerBase
extends MatchingRequestHandlerBase {

	@Override
	protected final boolean match(final FullHttpRequest req) {
		final var reqUri = req.uri();
		return reqUri.startsWith(uriStartsWith());
	}

	protected abstract String uriStartsWith();
}
