package com.emc.mongoose.base.svc.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.emc.mongoose.base.Constants.MIB;

public final class ServerChannelInitializerImpl
extends ChannelInitializer<SocketChannel>
implements ServerChannelInitializer {

	private final List<ChannelHandler> handlers = new ArrayList<>();

	@Override
	protected final void initChannel(final SocketChannel socketChannel) {
		final ChannelPipeline pipeline = socketChannel.pipeline();
		pipeline.addLast(new HttpServerCodec());
		pipeline.addLast(new HttpObjectAggregator(MIB));
		pipeline.addLast(new HttpServerExpectContinueHandler());
		for(final ChannelHandler handler: handlers) {
			pipeline.addLast(handler);
		}
	}

	@Override
	public final ServerChannelInitializerImpl appendHandler(final ChannelHandler handler) {
		handlers.add(handler);
		return this;
	}

	@Override
	public final ServerChannelInitializerImpl appendHandlers(final ChannelHandler... handlers) {
		this.handlers.addAll(Arrays.asList(handlers));
		return this;
	}

	@Override
	public final List<ChannelHandler> handlers() {
		return handlers;
	}

	@Override
	public final void close() {
		handlers.clear();
	}
}
