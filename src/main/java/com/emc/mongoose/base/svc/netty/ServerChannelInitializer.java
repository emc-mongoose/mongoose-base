package com.emc.mongoose.base.svc.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;

import java.util.List;

public interface ServerChannelInitializer
extends AutoCloseable, ChannelHandler {

	ServerChannelInitializer appendHandler(final ChannelInboundHandler handler);

	List<ChannelInboundHandler> handlers();
}
