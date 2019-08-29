package com.emc.mongoose.base.control;

import com.emc.mongoose.base.svc.http.HttpServerImpl;
import com.emc.mongoose.base.svc.http.ServerChannelInitializer;
import com.emc.mongoose.base.svc.http.ServerChannelInitializerImpl;
import com.emc.mongoose.base.svc.http.handler.impl.ConfigRequestHandler;
import com.github.akurilov.commons.concurrent.AsyncRunnable;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.impl.BasicConfig;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author veronika K. on 02.11.18 */
public class ConfigApiTest {

	private static final int PORT = 9999;
	private static final String HOST = "http://localhost:" + PORT;
	private static final Map<String, Object> SCHEMA = new HashMap<>();

	static {
		SCHEMA.put("key", Object.class);
	}

	private static final Config CONFIG = new BasicConfig("-", SCHEMA);
	private static final String EXPECTED_CONFIG_JSON = "---\nkey: \"value\"";
	private static final String EXPECTED_SCHEMA_JSON = "---\nkey: \"any\"";

	static {
		CONFIG.val("key", "value");
	}

	private static ServerChannelInitializer chanInitializer = null;
	private static AsyncRunnable server = null;

	@BeforeClass
	public static void setUp() throws Exception {
		chanInitializer = new ServerChannelInitializerImpl()
			.appendHandler(new ConfigRequestHandler(CONFIG));
		server = new HttpServerImpl(PORT, chanInitializer);
		server.start();
	}

	@Test
	public void test() throws Exception {
		final String config = resultFromServer(HOST + "/config");
		final String schema = resultFromServer(HOST + "/config/schema");
		Assert.assertEquals(EXPECTED_CONFIG_JSON, config);
		Assert.assertEquals(EXPECTED_SCHEMA_JSON, schema);
	}

	private String resultFromServer(final String urlPath) throws Exception {
		final String result;
		final URL url = new URL(urlPath);
		final URLConnection conn = url.openConnection();
		try (final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
			result = br.lines().collect(Collectors.joining("\n"));
		}
		return result;
	}

	@AfterClass
	public static void tearDown() throws Exception {
		chanInitializer.close();
		server.close();
	}
}
