package com.emc.mongoose.base.svc.http;

import com.emc.mongoose.base.svc.Server;
import com.github.akurilov.commons.concurrent.AsyncRunnableBase;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.concurrent.TimeUnit;

import static com.github.akurilov.commons.lang.Exceptions.throwUnchecked;

public class HttpServerImpl
extends AsyncRunnableBase
implements Server {

	private final int port;
	private final EventLoopGroup dispatcherGroup;
	private final EventLoopGroup workerGroup;
	private final ServerBootstrap bootstrap;

	private Channel channel;

	public HttpServerImpl(final int port, final ServerChannelInitializer chanInitializer) {
		this.port = port;
		dispatcherGroup = new NioEventLoopGroup(1);
		workerGroup = new NioEventLoopGroup(1);
		bootstrap = new ServerBootstrap();
		bootstrap.group(dispatcherGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.childHandler(chanInitializer);
	}

	@Override
	public final void doStart() {
		final ChannelFuture bind = bootstrap.bind(port);
		try {
			bind.sync();
			channel = bind.sync().channel();
		} catch(final InterruptedException e) {
			throwUnchecked(e);
		}
	}

	@Override
	public final void doStop() {
		dispatcherGroup.shutdownGracefully(1, 1, TimeUnit.SECONDS);
		workerGroup.shutdownGracefully(1, 1, TimeUnit.SECONDS);
	}

	@Override
	public final void doClose() {
		channel.close();
	}
}
