package com.emc.mongoose.base.svc.http.handler.impl;

import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.svc.http.handler.UriMatchingRequestHandlerBase;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.akurilov.commons.collection.Range;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.async.AsyncLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.emc.mongoose.base.Constants.MIB;
import static com.emc.mongoose.base.svc.http.handler.ResponseUtil.respondContent;
import static com.emc.mongoose.base.svc.http.handler.ResponseUtil.respondEmptyContent;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import static java.nio.file.StandardOpenOption.READ;

@ChannelHandler.Sharable
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
		final var method = req.method();
		try {
			final var logFilePathOption = logFilePath(req);
			if(GET.equals(method)) {
				logFilePathOption.ifPresentOrElse(
					path -> respondFileContent(ctx, path, req),
					() -> writeLogNamesJson(ctx)
				);
			} else if(DELETE.equals(method)) {
				logFilePathOption.ifPresentOrElse(
					path -> {
						try {
							Files.delete(path);
							respondEmptyContent(ctx, OK);
						} catch (final IOException e) {
							respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
						}
					},
					() -> respondEmptyContent(ctx, BAD_REQUEST)
				);
			} else {
				respondEmptyContent(ctx, METHOD_NOT_ALLOWED);
			}
		} catch (final NoLoggerException e) {
			respondEmptyContent(ctx, BAD_REQUEST);
		} catch (final NoLogFileException e) {
			respondEmptyContent(ctx, NOT_FOUND);
		}
	}

	private Optional<Path> logFilePath(final FullHttpRequest req)
	throws NoLoggerException, NoLogFileException {
		final var reqUri = req.uri();
		final var matcher = PATTERN_URI_PATH.matcher(reqUri);
		if (matcher.find()) {
			final var stepId = matcher.group(KEY_STEP_ID);
			final var loggerName = matcher.group(KEY_LOGGER_NAME);
			final var logFileNamePattern = logFileNamePatternByName.get(loggerName);
			if (null == logFileNamePattern) {
				throw new NoLoggerException("No such logger: \"" + loggerName + "\"");
			} else if (logFileNamePattern.isEmpty()) {
				throw new NoLogFileException(
					"Unable to determine the log file for the logger \"" + loggerName + "\"");
			} else {
				final var logFile = logFileNamePattern.replace(PATTERN_STEP_ID_SUBST, stepId);
				return Optional.of(Paths.get(logFile));
			}
		} else {
			return Optional.empty();
		}
	}

	static void respondFileContent(
		final ChannelHandlerContext ctx, final Path filePath, final FullHttpRequest req
	) {
		if (Files.exists(filePath)) {
			final var byteRanges = req
				.headers()
				.getAll(HttpHeaderNames.RANGE)
				.stream()
				.map(Range::new)
				.collect(Collectors.toList());
			final long offset;
			final long size;
			if (byteRanges.isEmpty()) {
				offset = 0;
				size = LOG_PAGE_SIZE_LIMIT - 1;
			} else if (1 == byteRanges.size()) {
				final var byteRange = byteRanges.get(0);
				offset = byteRange.getBeg();
				size = byteRange.getEnd() + 1;
			} else {
				offset = size = -1;
			}
			//
			if(size > 0) {
				try {
					final var rangeContent = Unpooled.wrappedBuffer(readFileRange(filePath, offset, size));
					respondContent(ctx, OK, rangeContent, TEXT_PLAIN.toString());
				} catch(final IOException e) {
					respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
				}
			} else {
				respondEmptyContent(ctx, REQUESTED_RANGE_NOT_SATISFIABLE);
			}
		} else {
			respondEmptyContent(ctx, NOT_FOUND);
		}
	}

	static void writeLogNamesJson(final ChannelHandlerContext ctx) {
		final var jsonFactory = new JsonFactory();
		final var mapper = new ObjectMapper(jsonFactory);
		var content = (ByteBuf) null;
		try(final var out = new ByteArrayOutputStream(MIB)) {
			mapper
				.configure(SerializationFeature.INDENT_OUTPUT, true)
				.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
				.configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)
				.writerWithDefaultPrettyPrinter()
				.writeValue(out, Loggers.DESCRIPTIONS_BY_NAME);
			out.write(System.lineSeparator().getBytes());
			content = Unpooled.wrappedBuffer(out.toByteArray());
		} catch(final IOException e) {
			Loggers.ERR.warn("Failed to write the json content");
		}
		if(null == content) {
			respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
		} else {
			respondContent(ctx, OK, content, APPLICATION_JSON.toString());
		}
	}

	static ByteBuffer readFileRange(final Path filePath, final long offset, final long size)
	throws IOException {
		final int sizeLimit = (int) Math.min(size, LOG_PAGE_SIZE_LIMIT);
		final ByteBuffer logPageContent = ByteBuffer.allocate(sizeLimit);
		try (final var fileChan = Files.newByteChannel(filePath, READ)) {
			fileChan.position(offset);
			while(-1 != fileChan.read(logPageContent));
		}
		return logPageContent;
	}
}
