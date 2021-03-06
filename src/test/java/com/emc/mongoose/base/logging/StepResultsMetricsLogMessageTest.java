package com.emc.mongoose.base.logging;

import static org.junit.Assert.assertEquals;

import com.emc.mongoose.base.Constants;
import com.emc.mongoose.base.item.op.OpType;
import com.emc.mongoose.base.metrics.MetricsConstants;
import com.emc.mongoose.base.metrics.snapshot.ConcurrencyMetricSnapshot;
import com.emc.mongoose.base.metrics.snapshot.DistributedAllMetricsSnapshot;
import com.emc.mongoose.base.metrics.snapshot.DistributedAllMetricsSnapshotImpl;
import com.emc.mongoose.base.metrics.snapshot.RateMetricSnapshot;
import com.emc.mongoose.base.metrics.snapshot.RateMetricSnapshotImpl;
import com.emc.mongoose.base.metrics.snapshot.TimingMetricSnapshot;
import com.emc.mongoose.base.metrics.type.ConcurrencyMeterImpl;
import com.emc.mongoose.base.metrics.type.TimingMeterImpl;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class StepResultsMetricsLogMessageTest extends StepResultsMetricsLogMessage {

	private static final OpType OP_TYPE = OpType.READ;
	private static final String STEP_ID = StepResultsMetricsLogMessageTest.class.getSimpleName();
	private static final int COUNT = 123456;
	private static final int DUR_MAX = 31416;
	private static final int LAT_MAX = 27183;
	private static final long[] DURATIONS = new long[COUNT];
	private static long durSum = 0;
	private static Map<Double, Long> latencies;
	private static Map<Double, Long> durations;
	static {
		for (int i = 0; i < COUNT; i++) {
			DURATIONS[i] = System.nanoTime() % DUR_MAX;
			durSum += DURATIONS[i];
		}
	}

	private static final long[] LATENCIES = new long[COUNT];
	private static long latSum = 0;

	static {
		for (int i = 0; i < COUNT; i++) {
			LATENCIES[i] = System.nanoTime() % LAT_MAX;
			latSum += LATENCIES[i];
		}
	}

	private static final long[] CONCURRENCIES = new long[COUNT];

	static {
		for (int i = 0; i < COUNT; i++) {
			CONCURRENCIES[i] = 10;
		}
	}

	private static final DistributedAllMetricsSnapshot SNAPSHOT;

	static {
		final TimingMetricSnapshot dS = new TimingMeterImpl(MetricsConstants.METRIC_NAME_DUR).snapshot();
		final TimingMetricSnapshot lS = new TimingMeterImpl(MetricsConstants.METRIC_NAME_LAT).snapshot();
		final ConcurrencyMetricSnapshot cS = new ConcurrencyMeterImpl(MetricsConstants.METRIC_NAME_CONC).snapshot();
		final RateMetricSnapshot fS = new RateMetricSnapshotImpl(0, 0, MetricsConstants.METRIC_NAME_FAIL, 0, 0);
		final double countDividedByDur = (double)COUNT / durSum;
		final RateMetricSnapshot sS = new RateMetricSnapshotImpl(countDividedByDur
						, countDividedByDur, MetricsConstants.METRIC_NAME_SUCC, COUNT, 0);
		final RateMetricSnapshot bS = new RateMetricSnapshotImpl(
						countDividedByDur,
						countDividedByDur,
						MetricsConstants.METRIC_NAME_BYTE,
						Double.valueOf(COUNT * Constants.K).longValue(),
						0);
		SNAPSHOT = new DistributedAllMetricsSnapshotImpl(dS, lS, cS, fS, sS, bS, 2, 123456);
		// there is no way to unit-test TimingMetricQuantileResultsImpl as it requires creating several files with actual
		// metrics, so it's only covered by functional tests
		latencies = new LinkedHashMap<>();
		latencies.put(0.25, 25L);
		latencies.put(0.5, 50L);
		latencies.put(0.75, 75L);
		durations = new LinkedHashMap<>();
		durations.put(0.25, 26L);
		durations.put(0.5, 51L);
		durations.put(0.75, 76L);
	}

	public StepResultsMetricsLogMessageTest() {
		super(OP_TYPE, STEP_ID, 0, SNAPSHOT, latencies, durations);
	}

	@Test
	public final void testIsValidYaml() throws Exception {
		final StringBuilder buff = new StringBuilder();
		formatTo(buff);
		System.out.println(buff.toString());
		final YAMLFactory yamlFactory = new YAMLFactory();
		final ObjectMapper mapper = new ObjectMapper(yamlFactory);
		final JavaType parsedType = mapper.getTypeFactory().constructArrayType(Map.class);
		final Map<String, Object> parsed = ((Map<String, Object>[]) mapper.readValue(buff.toString(), parsedType))[0];
		assertEquals(STEP_ID, parsed.get("Load Step Id"));
		assertEquals(OP_TYPE.name(), parsed.get("Operation Type"));
		assertEquals(COUNT, ((Map<String, Object>) parsed.get("Operations Count")).get("Successful"));
	}
}
