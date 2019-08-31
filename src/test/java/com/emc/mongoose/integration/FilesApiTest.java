package com.emc.mongoose.integration;

import com.emc.mongoose.base.load.step.file.FileManagerImpl;
import com.emc.mongoose.base.svc.http.HttpServerImpl;
import com.emc.mongoose.base.svc.http.ServerChannelInitializer;
import com.emc.mongoose.base.svc.http.ServerChannelInitializerImpl;
import com.emc.mongoose.base.svc.http.handler.impl.FilesRequestHandler;
import com.emc.mongoose.integration.util.TestingHttpClient;
import com.github.akurilov.commons.concurrent.AsyncRunnable;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_OCTET_STREAM;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FilesApiTest {

	private static final int PORT = 9876;
	private static final TestingHttpClient client = new TestingHttpClient("localhost", PORT, 1, TimeUnit.SECONDS);

	private static ServerChannelInitializer chanInitializer;
	private static AsyncRunnable server;

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		final var fileMgr = new FileManagerImpl();
		chanInitializer = new ServerChannelInitializerImpl()
			.appendHandler(new FilesRequestHandler(fileMgr));
		server = new HttpServerImpl(PORT, chanInitializer);
		server.start();
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		server.close();
		chanInitializer.close();
	}

	@Test
	public void testNewTmpFileName() {
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				final var tmpDirPath = Paths.get(System.getProperty("java.io.tmpdir"), "mongoose").toString();
				assertTrue(tmpFilePath.startsWith(tmpDirPath));
				assertTrue(tmpDirPath.length() < tmpFilePath.length() + 1);
			}
		);
	}

	@Test
	public void testDeleteNonExistingFile() {
		final var tmpFilePathRef = new AtomicReference<String>(null);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				tmpFilePathRef.set(tmpFilePath);
			}
		);
		final var tmpFilePath = tmpFilePathRef.get();
		assertNotNull(tmpFilePath);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, DELETE, "/files" + tmpFilePath),
			resp -> assertEquals(NOT_FOUND, resp.status())
		);
	}

	@Test
	public void testReadNonExistingFile() {
		final var tmpFilePathRef = new AtomicReference<String>(null);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				tmpFilePathRef.set(tmpFilePath);
			}
		);
		final var tmpFilePath = tmpFilePathRef.get();
		assertNotNull(tmpFilePath);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, GET, "/files" + tmpFilePath),
			resp -> assertEquals(NOT_FOUND, resp.status())
		);
	}

	@Test
	public void testWriteFile()
	throws Exception {
		final var tmpFilePathRef = new AtomicReference<String>(null);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				tmpFilePathRef.set(tmpFilePath);
			}
		);
		final var tmpFilePath = tmpFilePathRef.get();
		assertNotNull(tmpFilePath);
		final var srcContent = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		client.sendRequestAndConsumeResponse(
			() -> {
				final var req = new DefaultFullHttpRequest(
					HTTP_1_1, PUT, "/files" + tmpFilePath, Unpooled.wrappedBuffer(srcContent)
				);
				final var reqHeaders = req.headers();
				reqHeaders.set(CONTENT_LENGTH, 10);
				reqHeaders.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
				return req;
			},
			resp -> assertEquals(OK, resp.status())
		);
		final var dstContent = Files.readAllBytes(Paths.get(tmpFilePath));
		assertArrayEquals(srcContent, dstContent);
	}

	@Test
	public void testDeleteFile() {
		final var tmpFilePathRef = new AtomicReference<String>(null);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				tmpFilePathRef.set(tmpFilePath);
			}
		);
		final var tmpFilePath = tmpFilePathRef.get();
		assertNotNull(tmpFilePath);
		final var srcContent = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		client.sendRequestAndConsumeResponse(
			() -> {
				final var req = new DefaultFullHttpRequest(
					HTTP_1_1, PUT, "/files" + tmpFilePath, Unpooled.wrappedBuffer(srcContent)
				);
				final var reqHeaders = req.headers();
				reqHeaders.set(CONTENT_LENGTH, 10);
				reqHeaders.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
				return req;
			},
			resp -> assertEquals(OK, resp.status())
		);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, DELETE, "/files" + tmpFilePath),
			resp -> assertEquals(OK, resp.status())
		);
	}

	@Test
	public void testFileSize() {
		final var tmpFilePathRef = new AtomicReference<String>(null);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				tmpFilePathRef.set(tmpFilePath);
			}
		);
		final var tmpFilePath = tmpFilePathRef.get();
		assertNotNull(tmpFilePath);
		final var srcContent = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		client.sendRequestAndConsumeResponse(
			() -> {
				final var req = new DefaultFullHttpRequest(
					HTTP_1_1, PUT, "/files" + tmpFilePath, Unpooled.wrappedBuffer(srcContent)
				);
				final var reqHeaders = req.headers();
				reqHeaders.set(CONTENT_LENGTH, 10);
				reqHeaders.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
				return req;
			},
			resp -> assertEquals(OK, resp.status())
		);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, HEAD, "/files" + tmpFilePath),
			resp -> {
				assertEquals(OK, resp.status());
				final var contentLen = resp.headers().getInt(CONTENT_LENGTH);
				assertEquals(10, contentLen.intValue());
			}
		);
	}

	@Test
	public final void testReadFile() {
		final var tmpFilePathRef = new AtomicReference<String>(null);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, POST, "/files/tmp"),
			resp -> {
				assertEquals(OK, resp.status());
				final var tmpFilePath = resp.headers().get(LOCATION);
				tmpFilePathRef.set(tmpFilePath);
			}
		);
		final var tmpFilePath = tmpFilePathRef.get();
		assertNotNull(tmpFilePath);
		final var srcContent = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		client.sendRequestAndConsumeResponse(
			() -> {
				final var req = new DefaultFullHttpRequest(
					HTTP_1_1, PUT, "/files" + tmpFilePath, Unpooled.wrappedBuffer(srcContent)
				);
				final var reqHeaders = req.headers();
				reqHeaders.set(CONTENT_LENGTH, 10);
				reqHeaders.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
				return req;
			},
			resp -> assertEquals(OK, resp.status())
		);
		client.sendRequestAndConsumeResponse(
			() -> new DefaultFullHttpRequest(HTTP_1_1, GET, "/files" + tmpFilePath),
			resp -> {
				assertEquals(OK, resp.status());
				final var respHeaders = resp.headers();
				final var contentLen = respHeaders.getInt(CONTENT_LENGTH);
				assertEquals(10, contentLen.intValue());
				assertEquals(APPLICATION_OCTET_STREAM.toString(), respHeaders.get(CONTENT_TYPE));
				final var dstContent = new byte[contentLen];
				resp.content().readBytes(dstContent);
				assertArrayEquals(srcContent, dstContent);
			}
		);
	}
}
