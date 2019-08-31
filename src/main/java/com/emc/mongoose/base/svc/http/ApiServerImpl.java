package com.emc.mongoose.base.svc.http;

import com.emc.mongoose.base.env.Extension;
import com.emc.mongoose.base.load.step.file.FileManager;
import com.emc.mongoose.base.load.step.file.FileManagerImpl;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.metrics.MetricsManager;
import com.emc.mongoose.base.svc.Server;
import com.emc.mongoose.base.svc.http.handler.AddCorsHeadersResponseHandler;
import com.emc.mongoose.base.svc.http.handler.impl.ConfigRequestHandler;
import com.emc.mongoose.base.svc.http.handler.impl.FilesRequestHandler;
import com.emc.mongoose.base.svc.http.handler.impl.LoadStepRequestHandler;
import com.emc.mongoose.base.svc.http.handler.impl.logs.LogsRequestHandler;
import com.emc.mongoose.base.svc.http.handler.impl.MetricsRequestHandler;
import com.emc.mongoose.base.svc.http.handler.impl.RunRequestHandler;
import com.github.akurilov.commons.concurrent.AsyncRunnableBase;
import com.github.akurilov.confuse.Config;
import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ApiServerImpl
extends AsyncRunnableBase
implements Server {

	private final ServerChannelInitializer chanInitializer;
	private final Server httpServer;

	public ApiServerImpl(
		final ClassLoader clsLoader,
		final List<Extension> extensions,
		final FileManager fileMgr,
		final MetricsManager metricsMgr,
		final Config aggregatedConfigWithArgs,
		final Path appHomePath
	) {
		final var port = aggregatedConfigWithArgs.intVal("run-port");
		chanInitializer = new ServerChannelInitializerImpl();
		chanInitializer.appendHandlers(
			new AddCorsHeadersResponseHandler(),
			new ConfigRequestHandler(aggregatedConfigWithArgs),
			new FilesRequestHandler(fileMgr),
			new LoadStepRequestHandler(),
			new LogsRequestHandler(),
			new MetricsRequestHandler(),
			new RunRequestHandler(clsLoader, extensions, metricsMgr, aggregatedConfigWithArgs, appHomePath)
		);
		httpServer = new HttpServerImpl(port, chanInitializer);
	}

	@Override
	protected void doStart() {
		try {
			httpServer.start();
		} catch(final IOException e) {
			LogUtil.exception(Level.ERROR, e, "Failed to start the HTTP server");
		}
	}

	@Override
	protected void doShutdown() {
		try {
			httpServer.shutdown();
		} catch(final IOException e) {
			LogUtil.exception(Level.WARN, e, "Failed to shutdown the HTTP server");
		}
	}

	@Override
	protected void doStop() {
		try {
			httpServer.stop();
		} catch(final IOException e) {
			LogUtil.exception(Level.WARN, e, "Failed to stop the HTTP server");
		}
	}

	@Override
	protected void doClose() {
		try {
			httpServer.close();
		} catch(final IOException e) {
			LogUtil.exception(Level.ERROR, e, "Failed to close the HTTP server");
		} finally {
			try {
				chanInitializer.close();
			} catch(final Exception e) {
				LogUtil.exception(Level.ERROR, e, "Failed to close the channel initializer used by the HTTP server");
			}
		}
	}
}
