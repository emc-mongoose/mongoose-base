package com.emc.mongoose.base.svc.netty;

import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.util.ArrayList;
import java.util.List;

import static com.emc.mongoose.base.Constants.MIB;

public final class ServerChannelInitializerImpl
extends ChannelInitializer<SocketChannel>
implements ServerChannelInitializer {

	private final List<ChannelInboundHandler> handlers = new ArrayList<>();

	@Override
	protected final void initChannel(final SocketChannel socketChannel)
	throws Exception {
		final ChannelPipeline pipeline = socketChannel.pipeline();
		pipeline.addLast(new HttpServerCodec());
		pipeline.addLast(new HttpObjectAggregator(MIB));
		for(final ChannelInboundHandler handler: handlers) {
			pipeline.addLast(handler);
		}
	}

	@Override
	public final ServerChannelInitializerImpl appendHandler(final ChannelInboundHandler handler) {
		handlers.add(handler);
		return this;
	}

	@Override
	public final List<ChannelInboundHandler> handlers() {
		return handlers;
	}

	@Override
	public final void close()
	throws Exception {
		handlers.clear();
	}
}