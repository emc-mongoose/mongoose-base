package com.emc.mongoose.base.svc.netty.handler.impl;

import com.emc.mongoose.base.Constants;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.svc.netty.handler.UriMatchingRequestHandlerBase;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.async.AsyncLogger;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.emc.mongoose.base.Constants.KEY_STEP_ID;
import static com.emc.mongoose.base.Constants.MIB;

public final class LogsRequestHandler
extends UriMatchingRequestHandlerBase {

	private static final String KEY_STEP_ID = "stepId";
	private static final String KEY_LOGGER_NAME = "loggerName";
	private static final Pattern PATTERN_URI_PATH = Pattern.compile(
		"/logs/(?<" + KEY_STEP_ID + ">[\\w\\-_.,;:~=+@]+)/(?<" + KEY_LOGGER_NAME + ">[\\w_.]+)");
	private static final String PATTERN_STEP_ID_SUBST = "${ctx:" + KEY_STEP_ID + "}";
	private static final int LOG_PAGE_SIZE_LIMIT = MIB;

	private final Map<String, String> logFileNamePatternByName;

	public LogsRequestHandler() {
		logFileNamePatternByName = Arrays.stream(Loggers.class.getFields())
			.map(
				field -> {
					try {
						return field.get(null);
					} catch (final Exception e) {
						throw new AssertionError(e);
					}
				}
			)
			.filter(fieldVal -> fieldVal instanceof Logger)
			.map(o -> (Logger) o)
			.filter(logger -> logger.getName().startsWith(Loggers.BASE))
			.collect(
				Collectors.toMap(
					logger -> logger.getName().substring(Loggers.BASE.length()),
					logger -> ((AsyncLogger) logger)
						.getAppenders().values().stream()
						.filter(appender -> appender instanceof RollingRandomAccessFileAppender)
						.map(appender -> ((RollingRandomAccessFileAppender) appender).getFilePattern())
						.findAny()
						.orElse("")
				)
			);
	}

	@Override
	protected final String uriStartsWith() {
		return "/logs";
	}

	@Override
	protected final void handle(final ChannelHandlerContext ctx, final FullHttpRequest req) {
	}
}
