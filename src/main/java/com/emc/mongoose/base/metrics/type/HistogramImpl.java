package com.emc.mongoose.base.metrics.type;

import com.emc.mongoose.base.metrics.snapshot.HistogramSnapshotImpl;
import com.emc.mongoose.base.metrics.util.LongReservoir;
import com.emc.mongoose.base.metrics.snapshot.HistogramSnapshot;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.SynchronizedHistogram;

import java.util.concurrent.atomic.LongAdder;

/** @author veronika K. on 01.10.18 */
public class HistogramImpl implements LongMeter<HistogramSnapshot> {

	private static final ConcurrentHistogram histogram = new ConcurrentHistogram(0);
	private static long last;

	@Override
	public void update(final long value) {
		histogram.recordValue(value);
		last = value;
	}

	@Override
	public HistogramSnapshotImpl snapshot() {
		return new HistogramSnapshotImpl(histogram, last);
	}
}
