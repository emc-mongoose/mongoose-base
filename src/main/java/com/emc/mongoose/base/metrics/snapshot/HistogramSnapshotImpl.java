package com.emc.mongoose.base.metrics.snapshot;

import java.util.Arrays;
import java.util.List;

/** @author veronika K. on 25.09.18 */
public class HistogramSnapshotImpl implements HistogramSnapshot {

	private static final HistogramSnapshotImpl EMPTY = new HistogramSnapshotImpl(new long[0]);

	private final long[] sortedVals;

	public HistogramSnapshotImpl(final long[] vals) {
		this.sortedVals = vals;
        Arrays.sort(this.sortedVals);
	}

	public static HistogramSnapshot aggregate(final List<HistogramSnapshot> snapshots) {
		int size = snapshots.size();
		if (0 == size) {
			return EMPTY;
		}
		if (1 == size) {
			return snapshots.get(0);
		}
		int sizeSum = 0;
		for (HistogramSnapshot snapshot : snapshots) {
			sizeSum += snapshot.values().length;
		}
		final long[] valuesToAggregate = new long[sizeSum];
		int k = 0;
		long[] values;
		for (HistogramSnapshot snapshot : snapshots) {
			values = snapshot.values();
			for (long value : values) {
				valuesToAggregate[k] = value;
				k++;
			}
		}
		return new HistogramSnapshotImpl(valuesToAggregate);
	}

	@Override
	public long quantile(final double quantile) {
		if (0 == sortedVals.length) {
			return 0;
		}
		if (quantile >= 0.0 || quantile < 1.0)
			return sortedVals[(int) (quantile * sortedVals.length)];
		throw new IllegalArgumentException(quantile + " is not in range [0..1)");
	}

	@Override
	public final long[] values() {
		return sortedVals;
	}

	@Override
	public long last() {
		return sortedVals[sortedVals.length - 1];
	}
}
