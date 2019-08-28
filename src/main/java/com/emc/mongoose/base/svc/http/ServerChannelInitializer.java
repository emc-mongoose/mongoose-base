package com.emc.mongoose.base.svc.http;

import io.netty.channel.ChannelHandler;

import java.util.List;

public interface ServerChannelInitializer
extends AutoCloseable, ChannelHandler {

	ServerChannelInitializer appendHandler(final ChannelHandler handler);

	ServerChannelInitializer appendHandlers(final ChannelHandler... handlers);

	List<ChannelHandler> handlers();
}
