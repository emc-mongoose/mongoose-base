package com.emc.mongoose.base.control.run;

import static com.emc.mongoose.base.config.ConfigFormat.JSON;
import static com.emc.mongoose.base.config.ConfigFormat.YAML;
import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON;
import static org.eclipse.jetty.http.MimeTypes.Type.MULTIPART_FORM_DATA;
import static org.eclipse.jetty.http.MimeTypes.Type.TEXT_JSON;

import com.emc.mongoose.base.concurrent.SingleTaskExecutor;
import com.emc.mongoose.base.concurrent.SingleTaskExecutorImpl;
import com.emc.mongoose.base.config.ConfigFormat;
import com.emc.mongoose.base.config.ConfigUtil;
import com.emc.mongoose.base.env.Extension;
import com.emc.mongoose.base.load.step.LoadStepManagerService;
import com.emc.mongoose.base.load.step.ScenarioUtil;
import com.emc.mongoose.base.load.step.service.LoadStepService;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.metrics.MetricsManager;
import com.fasterxml.jackson.core.JsonParseException;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.script.ScriptEngine;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.github.akurilov.confuse.impl.BasicConfig;
import org.apache.logging.log4j.Level;
import org.eclipse.jetty.http.HttpHeader;

/** @author veronika K. on 08.11.18 */
public class RunServlet extends HttpServlet {

	private static final String PART_KEY_DEFAULTS = "defaults";
	private static final String PART_KEY_SCENARIO = "scenario";

	private final ScriptEngine scriptEngine;
	private final List<Extension> extensions;
	private final MetricsManager metricsMgr;
	private final Config aggregatedConfigWithArgs;
	private final Path appHomePath;
	private final SingleTaskExecutor scenarioExecutor = new SingleTaskExecutorImpl();
	private final LoadStepManagerService scenarioStepSvc;

	public RunServlet(
					final ClassLoader clsLoader,
					final List<Extension> extensions,
					final MetricsManager metricsMgr,
					final Config aggregatedConfigWithArgs,
					final Path appHomePath,
					final LoadStepManagerService scenarioStepSvc) {
		this.scriptEngine = ScenarioUtil.scriptEngineByDefault(clsLoader);
		this.extensions = extensions;
		this.metricsMgr = metricsMgr;
		this.aggregatedConfigWithArgs = aggregatedConfigWithArgs;
		this.appHomePath = appHomePath;
		this.scenarioStepSvc = scenarioStepSvc;
	}

	@Override
	protected final void doPost(final HttpServletRequest req, final HttpServletResponse resp)
					throws IOException, ServletException {

		final Part defaultsPart;
		final Part scenarioPart;
		final var contentTypeHeaderValue = req.getHeader(HttpHeader.CONTENT_TYPE.toString());
		if (contentTypeHeaderValue != null
						&& contentTypeHeaderValue.startsWith(MULTIPART_FORM_DATA.toString())) {
			defaultsPart = req.getPart(PART_KEY_DEFAULTS);
			scenarioPart = req.getPart(PART_KEY_SCENARIO);
		} else {
			defaultsPart = null;
			scenarioPart = null;
		}
		try {
			final var config = mergeIncomingWithLocalConfig(defaultsPart, resp, aggregatedConfigWithArgs);
			final var scenario = getIncomingScenarioOrDefault(scenarioPart, appHomePath);
			if (config.longVal("run-id") == 0) {
				config.val("run-id", System.currentTimeMillis());
			}
			// expose the base configuration and the step types
			ScenarioUtil.configure(scriptEngine, extensions, config, metricsMgr);
			//
			final var run = (Run) new RunImpl(config.stringVal("run-comment"), scenario, scriptEngine, config.longVal("run-id"));
			try {
				scenarioExecutor.execute(run);
				resp.setStatus(HttpServletResponse.SC_ACCEPTED);
				setRunTimestampHeader(run, resp);
			} catch (final RejectedExecutionException e) {
				resp.setStatus(HttpServletResponse.SC_CONFLICT);
			}
		} catch (final NoSuchMethodException | RuntimeException e) {
			LogUtil.exception(Level.WARN, e, "Failed to run a scenario with the request {}", req);
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	@Override
	protected final void doHead(final HttpServletRequest req, final HttpServletResponse resp) throws RemoteException {
		applyForActiveRunIfAny(resp, RunServlet::setRunExistsResponse);
	}

	@Override
	protected final void doGet(final HttpServletRequest req, final HttpServletResponse resp)
					throws IOException {
		extractRequestTimestampAndApply(
						req, resp, (run, runId) -> setRunMatchesResponse(run, resp, runId));
	}

	@Override
	protected final void doDelete(final HttpServletRequest req, final HttpServletResponse resp)
					throws IOException {
		extractRequestTimestampAndApply(
						req,
						resp,
						(run, runId) -> stopRunIfMatchesAndSetResponse(run, resp, runId, scenarioExecutor));
	}

	static void setRunTimestampHeader(final Run task, final HttpServletResponse resp) {
		resp.setHeader(HttpHeader.ETAG.name(), String.valueOf(task.runId()));
	}

	void applyForActiveRunIfAny(
					final HttpServletResponse resp, final BiConsumer<Run, HttpServletResponse> action) throws RemoteException {
		final var activeTask = scenarioExecutor.task();
		final var activeService = scenarioStepSvc.getStepService();
		if (null == activeTask && activeService == null) {
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} else if (activeTask instanceof Run) {
			final var activeRun = (Run) activeTask;
			action.accept(activeRun, resp);
		} else if (activeService != null) {
			final var activeRun = new RunImpl("", "", null, activeService.runId());
			action.accept(activeRun, resp);
		} else {
			Loggers.ERR.warn("The scenario executor runs an alien task: {}", activeTask);
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		}
	}

	void extractRequestTimestampAndApply(
					final HttpServletRequest req,
					final HttpServletResponse resp,
					final BiConsumer<Run, Long> runRespRunIdConsumer)
					throws IOException {
		final var rawIncomingRunId = Collections.list(req.getHeaders(HttpHeader.IF_MATCH.toString())).stream()
						.findAny()
						.orElse(null);
		final var incomingRunId = Long.parseLong(rawIncomingRunId);
		if (rawIncomingRunId == null) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing header: " + HttpHeader.IF_MATCH);
		} else {
			try {
				applyForActiveRunIfAny(
								resp, (run, resp_) -> runRespRunIdConsumer.accept(run, incomingRunId));
			} catch (final NumberFormatException e) {
				resp.sendError(
								HttpServletResponse.SC_BAD_REQUEST, "Invalid start time: " + incomingRunId);
			}
		}
	}

	static void setRunExistsResponse(final Run run, final HttpServletResponse resp) {
		resp.setStatus(HttpServletResponse.SC_OK);
		setRunTimestampHeader(run, resp);
	}

	static void setRunMatchesResponse(
					final Run run, final HttpServletResponse resp, final long incomingRunId) {
		if (run.runId() == incomingRunId) {
			resp.setStatus(HttpServletResponse.SC_OK);
		} else {
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		}
	}

	static void stopRunIfMatchesAndSetResponse(
					final Run run,
					final HttpServletResponse resp,
					final long runId,
					final SingleTaskExecutor scenarioExecutor) {
		if (run.runId() == runId) {
			scenarioExecutor.stop(run);
			if (null != scenarioExecutor.task()) {
				throw new AssertionError("Run stopping failure");
			}
			resp.setStatus(HttpServletResponse.SC_OK);
		} else {
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	static Config mergeIncomingWithLocalConfig(
					final Part defaultsPart,
					final HttpServletResponse resp,
					final Config aggregatedConfigWithArgs)
					throws IOException, NoSuchMethodException, InvalidValuePathException,
					InvalidValueTypeException {
		final Config configResult;
		if (defaultsPart == null) {
			// NOTE: If custom config hasn't been specified in POST request, set the default one
			configResult = new BasicConfig(aggregatedConfigWithArgs);
		} else {
			final var configIncoming = configFromPart(defaultsPart, resp, aggregatedConfigWithArgs.schema());
			// the load step id was set manually if it is set to some non-null/non-empty value in the incoming config
			try {
				final var loadStepIdIncoming = configIncoming.stringVal("load-step-id");
				if (null != loadStepIdIncoming && !loadStepIdIncoming.isEmpty()) {
					configIncoming.val("load-step-idAutoGenerated", false);
				}
			} catch (final NoSuchElementException ignored) {}
			configResult = ConfigUtil.merge(
							aggregatedConfigWithArgs.pathSep(),
							Arrays.asList(aggregatedConfigWithArgs, configIncoming));
		}
		return configResult;
	}

	static Config configFromPart(
					final Part defaultsPart,
					final HttpServletResponse resp,
					final Map<String, Object> configSchema)
					throws IOException, NoSuchMethodException, InvalidValuePathException,
					InvalidValueTypeException {
		final String rawDefaultsData;
		try (final var br = new BufferedReader(new InputStreamReader(defaultsPart.getInputStream()))) {
			rawDefaultsData = br.lines().collect(Collectors.joining("\n"));
		}
		final var contentType = defaultsPart.getContentType();
		ConfigFormat format = YAML;
		if (contentType != null) {
			if (contentType.startsWith(APPLICATION_JSON.toString())) {
				format = JSON;
			} else if (contentType.startsWith(TEXT_JSON.toString())) {
				format = JSON;
			}
		}
		try {
			return ConfigUtil.loadConfig(rawDefaultsData, format, configSchema);
		} catch (final JsonParseException e) {
			if (YAML.equals(format)) {
				// was unable to detect format using content-type header (likely application/octet-stream),
				// fallback is YAML, but it may JSON actually
				return ConfigUtil.loadConfig(rawDefaultsData, JSON, configSchema);
			} else {
				throw e;
			}
		}
	}

	static String getIncomingScenarioOrDefault(final Part scenarioPart, final Path appHomePath)
					throws IOException {
		final String scenarioResult;
		if (scenarioPart == null) {
			scenarioResult = ScenarioUtil.defaultScenario(appHomePath);
		} else {
			try (final var br = new BufferedReader(new InputStreamReader(scenarioPart.getInputStream()))) {
				scenarioResult = br.lines().collect(Collectors.joining("\n"));
			}
		}
		return scenarioResult;
	}

	@Override
	public final void destroy() {
		scenarioExecutor.close();
		super.destroy();
	}
}
