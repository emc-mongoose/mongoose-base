package com.emc.mongoose.base.svc.http.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;

@ChannelHandler.Sharable
public final class AddCorsHeadersResponseHandler
extends ChannelOutboundHandlerAdapter {

	public static final String HEADER_NAME_PREFIX_AC = "Access-Control";
	public static final String HEADER_NAME_PREFIX_ACA = HEADER_NAME_PREFIX_AC + "-Allow";
	public static final String HEADER_VALUE_ACA_METHODS = "DELETE,GET,HEAD,POST,PUT,OPTIONS";

	public static final String HEADER_NAME_ACA_ORIGIN = HEADER_NAME_PREFIX_ACA + "-Origin";
	public static final String HEADER_VALUE_ACA_ORIGIN = "*";

	public static final String HEADER_NAME_ACA_HEADERS = HEADER_NAME_PREFIX_ACA + "-Headers";
	public static final String HEADER_NAME_ACA_METHODS = HEADER_NAME_PREFIX_ACA + "-Methods";
	public static final String HEADER_NAME_AC_EXPOSE_HEADERS = HEADER_NAME_PREFIX_AC + "-Expose-Headers";
	public static final String ACA_HEADERS = "Origin, X-Requested-With, Content-Type, Accept, ETag, If-Match";

	@Override
	public final void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise channelPromise)
	throws Exception {
		if(msg instanceof FullHttpResponse) {
			final var resp = (FullHttpResponse) msg;
			final var respHeaders = resp.headers();
			// NOTE: In order to reply to CORS preflight request with the appropriate CORS headers, ...
			// ... Access-Control-Allow-Headers should contain the same headers ...
			// ... as Access-Control-Expose-Headers or more.
			respHeaders.set(HEADER_NAME_AC_EXPOSE_HEADERS, ACA_HEADERS);
			respHeaders.set(HEADER_NAME_ACA_HEADERS, ACA_HEADERS);
			respHeaders.set(HEADER_NAME_ACA_METHODS, HEADER_VALUE_ACA_METHODS);
			respHeaders.set(HEADER_NAME_ACA_ORIGIN, HEADER_VALUE_ACA_ORIGIN);
		}
		super.write(ctx, msg, channelPromise);
	}
}
