package com.emc.mongoose.base.svc.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;

import java.util.List;

public interface ServerChannelInitializer
extends AutoCloseable, ChannelHandler {

	ServerChannelInitializer appendHandler(final ChannelInboundHandler handler);

	ServerChannelInitializer appendHandlers(final ChannelInboundHandler... handlers);

	List<ChannelInboundHandler> handlers();
}
