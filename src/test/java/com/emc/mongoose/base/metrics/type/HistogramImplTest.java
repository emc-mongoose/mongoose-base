package com.emc.mongoose.base.metrics.type;

import static org.junit.Assert.assertEquals;

import com.emc.mongoose.base.metrics.snapshot.HistogramSnapshot;
import com.emc.mongoose.base.metrics.snapshot.HistogramSnapshotImpl;
import com.emc.mongoose.base.metrics.util.ConcurrentSlidingWindowLongReservoir;
import java.util.stream.LongStream;

import org.junit.Ignore;
import org.junit.Test;

/** @author veronika K. on 16.10.18 */
public class HistogramImplTest {

	@Test
	public void quantileTest() {
		final long[] srcData = new long[]{
				450, 9, 400, 36, 225, 72, 360, 56, 180, 600, 21, 162, 150, 320, 160, 270, 162, 210, 60,
				504, 175, 150, 80, 200, 48, 180, 18, 80, 84, 126, 30, 32, 216, 63, 640, 36, 200, 45, 300,
				90, 108, 135, 30, 216, 96, 180, 12, 90, 180, 240, 108, 560, 50, 105, 144, 240, 120, 560,
				18, 18, 180, 432, 30, 60, 630, 5, 210, 150, 48, 216, 560, 9, 90, 210, 360, 42, 81, 75, 72,
				56, 112, 280, 192, 160, 48, 108, 98, 192, 144, 49, 40, 60, 160, 45, 300, 48, 14, 144, 168,
				96,
		};
		final LongMeter<HistogramSnapshot> histogram = new HistogramImpl(new ConcurrentSlidingWindowLongReservoir(100));
		LongStream.of(srcData).forEach(histogram::update);
		final HistogramSnapshot snapshot = histogram.snapshot();
		assertEquals(5, snapshot.quantile(0.0)); // -> minimum
		assertEquals(5, snapshot.quantile(0.001));
		assertEquals(30, snapshot.quantile(0.1));
		assertEquals(48, snapshot.quantile(0.2));
		assertEquals(56, snapshot.quantile(0.25));
		assertEquals(126, snapshot.quantile(0.5)); // -> median
		assertEquals(210, snapshot.quantile(0.75));
		assertEquals(225, snapshot.quantile(0.8));
		assertEquals(400, snapshot.quantile(0.9));
		assertEquals(560, snapshot.quantile(0.95));
		assertEquals(640, snapshot.quantile(0.99));
		assertEquals(640, snapshot.quantile(0.999));
	}

	@Test @Ignore(value="invalid")
	public void mergeQuantilesTest()
	throws Exception {
		final long[] data1 = new long[] {
			450, 9, 400, 36, 225, 72, 360, 56, 180, 600, 21, 162, 150, 320, 160, 270, 162, 210, 60,
		};
		final long[] data2 = new long[] {
			504, 175, 150, 80, 200, 48, 180, 18, 80, 84, 126, 30, 32, 216, 63, 640, 36, 200, 45, 300,
		};
		final long[] dataCombined = new long[] {
			450, 9, 400, 36, 225, 72, 360, 56, 180, 600, 21, 162, 150, 320, 160, 270, 162, 210, 60,
			504, 175, 150, 80, 200, 48, 180, 18, 80, 84, 126, 30, 32, 216, 63, 640, 36, 200, 45, 300,
		};
		final var snapshot1 = new HistogramSnapshotImpl(data1);
		final var snapshot2 = new HistogramSnapshotImpl(data2);
		final var snapshotCombined = new HistogramSnapshotImpl(dataCombined);
		final var median1 = snapshot1.quantile(0.5); // 162
		final var median2 = snapshot2.quantile(0.5); // 126
		final var medianCombined = snapshotCombined.quantile(0.5); // 160
		final long[] dataSynthetic = new long[data1.length + data2.length];
		for(int i = 0; i < (int) (data1.length * 0.5); i ++) {
			dataSynthetic[i] = median1;
		}
		for(int i = (int) (data1.length * 0.5); i < data1.length; i ++) {
			dataSynthetic[i] = median1 + 1;
		}
		for(int i = 0; i < (int) (data2.length * 0.5); i ++) {
			dataSynthetic[data1.length + i] = median2;
		}
		for(int i = (int) (data2.length * 0.5); i < data2.length; i ++) {
			dataSynthetic[data1.length + i] = median2 + 1;
		}
		final var snapshotSynthetic = new HistogramSnapshotImpl(dataSynthetic);
		System.out.println(snapshotSynthetic.quantile(0.5));
	}
}
