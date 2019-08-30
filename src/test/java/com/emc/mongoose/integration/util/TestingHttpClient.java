package com.emc.mongoose.integration.util;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import org.glassfish.jersey.internal.guava.SettableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.emc.mongoose.base.Constants.MIB;

public class TestingHttpClient {

	private final EventLoopGroup evtLoopGroup = new NioEventLoopGroup();
	private final Bootstrap bootstrap = new Bootstrap()
		.group(evtLoopGroup)
		.channel(NioSocketChannel.class);

	@ChannelHandler.Sharable
	private static class ConsumingResponseHandler
	extends SimpleChannelInboundHandler<FullHttpResponse>
	implements Future<Void> {

		private final Consumer<FullHttpResponse> respConsumer;
		private final SettableFuture<Void> future = SettableFuture.create();

		public ConsumingResponseHandler(final Consumer<FullHttpResponse> respConsumer) {
			this.respConsumer = respConsumer;
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse resp) {
			respConsumer.accept(resp);
			future.set(null);
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
			throw new AssertionError(cause);
		}

		@Override
		public boolean cancel(final boolean b) {
			return future.cancel(b);
		}

		@Override
		public boolean isCancelled() {
			return future.isCancelled();
		}

		@Override
		public boolean isDone() {
			return future.isDone();
		}

		@Override
		public Void get()
		throws InterruptedException, ExecutionException {
			return future.get();
		}

		@Override
		public Void get(final long l, final TimeUnit timeUnit)
		throws InterruptedException, ExecutionException, TimeoutException {
			return future.get(l, timeUnit);
		}
	}

	private final String host;
	private final int port;
	private final long timeout;
	private final TimeUnit timeUnit;

	public TestingHttpClient(final String host, final int port, final long timeout, final TimeUnit timeUnit) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		this.timeUnit = timeUnit;
	}

	public void sendRequestAndConsumeResponse(
		final Supplier<FullHttpRequest> reqSupplier, final Consumer<FullHttpResponse> respConsumer
	) {
		final var respHandler = new ConsumingResponseHandler(respConsumer);
		bootstrap.handler(
			new ChannelInitializer<SocketChannel>() {
				@Override
				protected void initChannel(final SocketChannel ch) {
					final var pipeline = ch.pipeline();
					pipeline.addLast(new HttpClientCodec());
					pipeline.addLast(new HttpObjectAggregator(MIB));
					pipeline.addLast(respHandler);
				}
			}
		);
		final var connFuture = bootstrap.connect(host, port);
		try {
			connFuture.get(timeout, timeUnit);
		} catch(final Exception e) {
			throw new AssertionError(e);
		}
		final var chan = connFuture.channel();
		final var req = reqSupplier.get();
		final var reqSentFuture = chan.writeAndFlush(req);
		try {
			reqSentFuture.get(timeout, timeUnit);
		} catch(final Exception e) {
			throw new AssertionError(e);
		}
		try {
			respHandler.get(timeout, timeUnit);
		} catch(final Exception e) {
			throw new AssertionError(e);
		}
		chan.close();
	}
}
