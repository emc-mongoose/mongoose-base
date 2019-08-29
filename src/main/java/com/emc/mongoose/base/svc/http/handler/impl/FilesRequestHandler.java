package com.emc.mongoose.base.svc.http.handler.impl;

import com.emc.mongoose.base.load.step.file.FileManager;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.svc.http.handler.UriPrefixMatchingRequestHandlerBase;
import com.github.akurilov.commons.collection.Range;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.stream.Collectors;

import static com.emc.mongoose.base.svc.http.handler.ResponseUtil.respondContent;
import static com.emc.mongoose.base.svc.http.handler.ResponseUtil.respondEmptyContent;
import static com.emc.mongoose.base.svc.http.handler.ResponseUtil.writeEmptyContentResponse;
import static com.github.akurilov.commons.system.DirectMemUtil.REUSABLE_BUFF_SIZE_MAX;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpHeaderNames.RANGE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_OCTET_STREAM;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@ChannelHandler.Sharable
public final class FilesRequestHandler
extends UriPrefixMatchingRequestHandlerBase {

	private static final int MAX_BYTES_PER_READ = REUSABLE_BUFF_SIZE_MAX;

	private final FileManager fileMgr;

	public FilesRequestHandler(final FileManager fileMgr) {
		this.fileMgr = fileMgr;
	}

	@Override
	protected final String uriPrefix() {
		return "/files";
	}

	@Override
	protected final void handle(final ChannelHandlerContext ctx, final FullHttpRequest req) {
		final var reqUri = req.uri();
		final var method = req.method();
		final var tmpFileReq = reqUri.equals(uriPrefix() + "/tmp");
		if(tmpFileReq) {
			if(POST.equals(method)) {
				handleNewTmpFileNameRequest(ctx, method);
			} else {
				respondEmptyContent(ctx, METHOD_NOT_ALLOWED);
			}
		} else {
			final var filePath = reqUri.substring(uriPrefix().length() + 1);
			Loggers.MSG.debug("File request, method={}, file={}", method, filePath);
			if(DELETE.equals(method)) {
				handleDeleteFileRequest(ctx, filePath);
			} else if(HEAD.equals(method)) {
				handleGetFileSizeRequest(ctx, filePath);
			} else if(GET.equals(method)) {
				handleReadFileContentRequest(ctx, filePath, req);
			} else if(PUT.equals(method)) {
				handleWriteFileContentRequest(ctx, filePath, req.content());
			} else {
				respondEmptyContent(ctx, METHOD_NOT_ALLOWED);
			}
		}
	}

	void handleNewTmpFileNameRequest(final ChannelHandlerContext ctx, final HttpMethod method) {
		try {
			final var tmpFileName = fileMgr.newTmpFileName();
			final var resp = new DefaultFullHttpResponse(HTTP_1_1, OK);
			resp.headers().add(LOCATION, tmpFileName);
			writeEmptyContentResponse(ctx, resp);
		} catch(final IOException e) {
			respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	void handleDeleteFileRequest(final ChannelHandlerContext ctx, final String filePath) {
		try {
			fileMgr.deleteFile(filePath);
		} catch(final FileNotFoundException e) {
			respondEmptyContent(ctx, NOT_FOUND);
		} catch(final IOException e) {
			respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	void handleGetFileSizeRequest(final ChannelHandlerContext ctx, final String filePath) {
		try {
			final var size = fileMgr.fileSize(filePath);
			final var resp = new DefaultFullHttpResponse(HTTP_1_1, OK);
			resp.headers().add(CONTENT_LENGTH, size);
			writeEmptyContentResponse(ctx, resp);
		} catch(final FileNotFoundException e) {
			respondEmptyContent(ctx, NOT_FOUND);
		} catch(final IOException e) {
			respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	void handleReadFileContentRequest(
		final ChannelHandlerContext ctx, final String filePath, final FullHttpRequest req
	) {
		//
		final var byteRanges = req
			.headers()
			.getAll(RANGE)
			.stream()
			.map(Range::new)
			.collect(Collectors.toList());
		final long offset;
		final long size;
		if (byteRanges.isEmpty()) {
			offset = 0;
			size = MAX_BYTES_PER_READ - 1;
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
				final var content = fileMgr.readFromFile(filePath, offset);
				respondContent(ctx, OK, Unpooled.wrappedBuffer(content), APPLICATION_OCTET_STREAM.toString());
			} catch(final FileNotFoundException e) {
				respondEmptyContent(ctx, NOT_FOUND);
			} catch(final IOException e) {
				respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
			}
		} else {
			respondEmptyContent(ctx, REQUESTED_RANGE_NOT_SATISFIABLE);
		}
	}

	void handleWriteFileContentRequest(final ChannelHandlerContext ctx, final String filePath, final ByteBuf content) {
		final byte[] bytes;
		if(content.hasArray()) {
			bytes = content.array();
		} else {
			bytes = new byte[content.readableBytes()];
			content.readBytes(bytes);
		}
		try {
			fileMgr.writeToFile(filePath, bytes);
		} catch(final IOException e) {
			respondEmptyContent(ctx, INTERNAL_SERVER_ERROR);
		}
	}
}
