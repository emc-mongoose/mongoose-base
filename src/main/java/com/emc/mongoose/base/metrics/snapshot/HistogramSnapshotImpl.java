package com.emc.mongoose.base.metrics.snapshot;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SynchronizedHistogram;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author veronika K. on 25.09.18
 */
public class HistogramSnapshotImpl implements HistogramSnapshot {

	// try with autoresizing
	private static final ConcurrentHistogram histogram = new ConcurrentHistogram(0);
	private static AtomicLong lastValue = new AtomicLong();
    private static final HistogramSnapshotImpl EMPTY = new HistogramSnapshotImpl();

	public HistogramSnapshotImpl(final Histogram histogram, final long lastValue) {
		this.histogram.add(histogram);
		this.lastValue.set(lastValue);
	}

	public HistogramSnapshotImpl() {
	}

	public static HistogramSnapshot aggregate(final List<HistogramSnapshot> snapshots) {
        int size = snapshots.size();
        if (0 == size) {
            return EMPTY;
        }
        if (1 == size) {
            return snapshots.get(0);
        }
        final AtomicLong sumLastValue = new AtomicLong();
        for (HistogramSnapshot snapshot : snapshots) {
            histogram.add(snapshot.histogram());
            sumLastValue.addAndGet(snapshot.last());
        }
        final long meanLastValue = sumLastValue.longValue() / size;
		return new HistogramSnapshotImpl(histogram, meanLastValue);
    }

    @Override
    public long quantile(final double quantile) {
        if (quantile >= 0.0 || quantile < 1.0)
            return histogram.getValueAtPercentile(quantile);
        throw new IllegalArgumentException(quantile + " is not in range [0..1)");
    }

    @Override
    public long last() {
        return lastValue.get();
    }

    @Override
    public Histogram histogram(){
    	return histogram;
	}
}
