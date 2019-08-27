package com.emc.mongoose.base.svc.netty.handler.impl;

import com.emc.mongoose.base.concurrent.SingleTaskExecutor;
import com.emc.mongoose.base.concurrent.SingleTaskExecutorImpl;
import com.emc.mongoose.base.config.ConfigUtil;
import com.emc.mongoose.base.control.run.Run;
import com.emc.mongoose.base.control.run.RunImpl;
import com.emc.mongoose.base.env.Extension;
import com.emc.mongoose.base.load.step.ScenarioUtil;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.metrics.MetricsManager;
import com.emc.mongoose.base.svc.netty.handler.UriMatchingRequestHandlerBase;
import com.fasterxml.jackson.core.JsonParseException;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import com.github.akurilov.confuse.impl.BasicConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import org.apache.logging.log4j.Level;

import javax.script.ScriptEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;

import static com.emc.mongoose.base.config.ConfigFormat.JSON;
import static com.emc.mongoose.base.config.ConfigFormat.YAML;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.ETAG;
import static io.netty.handler.codec.http.HttpHeaderValues.MULTIPART_FORM_DATA;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.ACCEPTED;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class RunRequestHandler
extends UriMatchingRequestHandlerBase {

	private static final String PART_KEY_DEFAULTS = "defaults";
	private static final String PART_KEY_SCENARIO = "scenario";

	private final ScriptEngine scriptEngine;
	private final List<Extension> extensions;
	private final MetricsManager metricsMgr;
	private final Config aggregatedConfigWithArgs;
	private final Path appHomePath;
	private final SingleTaskExecutor scenarioExecutor = new SingleTaskExecutorImpl();

	public RunRequestHandler(
		final ClassLoader clsLoader,
		final List<Extension> extensions,
		final MetricsManager metricsMgr,
		final Config aggregatedConfigWithArgs,
		final Path appHomePath
	) {
		this.scriptEngine = ScenarioUtil.scriptEngineByDefault(clsLoader);
		this.extensions = extensions;
		this.metricsMgr = metricsMgr;
		this.aggregatedConfigWithArgs = aggregatedConfigWithArgs;
		this.appHomePath = appHomePath;
	}

	@Override
	protected final String uriStartsWith() {
		return "/run";
	}

	@Override
	protected final void handle(final ChannelHandlerContext ctx, final FullHttpRequest req) {
		final var method = req.method();
		if(POST.equals(method)) {
			startNewRun(ctx, req);
		} else if(HEAD.equals(method)) {
			checkRun(ctx, req);
		} else if(GET.equals(method)) {
			checkRunState(ctx, req);
		} else if(DELETE.equals(method)) {
			stopRun(ctx, req);
		} else {
			final var resp = new DefaultFullHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
			ctx.writeAndFlush(resp);
		}
	}

	void startNewRun(final ChannelHandlerContext ctx, final FullHttpRequest req) {
		// extract
		final var reqHeaders = req.headers();
		final var reqContentType = reqHeaders.get(CONTENT_TYPE);
		var incomingDefaults = (String) null;
		var incomingScenario = (String) null;
		if(reqContentType != null && reqContentType.startsWith(MULTIPART_FORM_DATA.toString())) {
			final var multipartReqDecoder = new HttpPostMultipartRequestDecoder(req);
			try {
				if(multipartReqDecoder.isMultipart()) {
					req.retain();
					final var defaultsPart = multipartReqDecoder.getBodyHttpData(PART_KEY_DEFAULTS).retain();
					if(null != defaultsPart) {
						incomingDefaults = ((HttpData) defaultsPart).getString();
					}
					final var scenarioPart = multipartReqDecoder.getBodyHttpData(PART_KEY_SCENARIO).retain();
					if(null != scenarioPart) {
						incomingScenario = ((HttpData) scenarioPart).getString();
					}
				} else {
					final var resp = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST);
					ctx.writeAndFlush(resp);
				}
			} catch(final IOException e) {
				final var resp = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR);
				ctx.writeAndFlush(resp);
			} finally {
				multipartReqDecoder.destroy();
			}
		}
		// try run
		final var resp = new DefaultFullHttpResponse(HTTP_1_1, ACCEPTED);
		resp.headers().add(CONTENT_LENGTH, 0);
		try {
			final var config = mergeIncomingWithLocalConfig(incomingDefaults, resp, aggregatedConfigWithArgs);
			final var scenario = incomingScenario == null ? ScenarioUtil.defaultScenario(appHomePath) : incomingScenario;
			if (config.longVal("run-id") == 0) {
				config.val("run-id", System.currentTimeMillis());
			}
			// expose the base configuration and the step types
			ScenarioUtil.configure(scriptEngine, extensions, config, metricsMgr);
			//
			final var comment = config.stringVal("run-comment");
			final var id = config.longVal("run-id");
			final var run = (Run) new RunImpl(comment, scenario, scriptEngine, id);
			try {
				scenarioExecutor.execute(run);
				setRunTimestampHeader(run, resp);
			} catch (final RejectedExecutionException e) {
				resp.setStatus(CONFLICT);
			}
		} catch (final NoSuchMethodException | IOException | RuntimeException e) {
			LogUtil.exception(Level.WARN, e, "Failed to run a scenario with the request {}", req);
			resp.setStatus(BAD_REQUEST);
		} finally {
			ctx.writeAndFlush(resp);
		}
	}

	void checkRun(final ChannelHandlerContext ctx, final FullHttpRequest req) {

	}

	void checkRunState(final ChannelHandlerContext ctx, final FullHttpRequest req) {

	}

	void stopRun(final ChannelHandlerContext ctx, final FullHttpRequest req) {

	}

	static Config mergeIncomingWithLocalConfig(
		final String defaultsPart,
		final FullHttpResponse resp,
		final Config aggregatedConfigWithArgs
	) throws IOException, NoSuchMethodException, InvalidValuePathException, InvalidValueTypeException {
		final Config configResult;
		if (defaultsPart == null) {
			// NOTE: If custom config hasn't been specified in POST request, set the default one
			configResult = new BasicConfig(aggregatedConfigWithArgs);
		} else {
			final var configIncoming = configFromString(defaultsPart, aggregatedConfigWithArgs.schema());
			// the load step id was set manually if it is set to some non-null/non-empty value in the incoming config
			try {
				final var loadStepIdIncoming = configIncoming.stringVal("load-step-id");
				if (null != loadStepIdIncoming && !loadStepIdIncoming.isEmpty()) {
					configIncoming.val("load-step-idAutoGenerated", false);
				}
			} catch (final NoSuchElementException ignored) {}
			configResult = ConfigUtil.merge(
				aggregatedConfigWithArgs.pathSep(),
				Arrays.asList(aggregatedConfigWithArgs, configIncoming)
			);
		}
		return configResult;
	}

	static Config configFromString(final String configStr, final Map<String, Object> configSchema)
	throws NoSuchMethodException, IOException {
		try {
			return ConfigUtil.loadConfig(configStr, YAML, configSchema);
		} catch (final JsonParseException e) {
			return ConfigUtil.loadConfig(configStr, JSON, configSchema);
		}
	}

	static void setRunTimestampHeader(final Run task, final FullHttpResponse resp) {
		resp.headers().add(ETAG, String.valueOf(task.runId()));
	}
}
