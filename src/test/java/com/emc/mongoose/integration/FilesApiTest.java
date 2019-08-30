package com.emc.mongoose.integration;

import com.emc.mongoose.base.load.step.file.FileManagerImpl;
import com.emc.mongoose.base.svc.http.HttpServerImpl;
import com.emc.mongoose.base.svc.http.ServerChannelInitializer;
import com.emc.mongoose.base.svc.http.ServerChannelInitializerImpl;
import com.emc.mongoose.base.svc.http.handler.impl.FilesRequestHandler;
import com.emc.mongoose.integration.util.TestingHttpClient;
import com.github.akurilov.commons.concurrent.AsyncRunnable;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.assertEquals;
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
	public void testNewTmpFileName()
	throws Exception {
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
}
