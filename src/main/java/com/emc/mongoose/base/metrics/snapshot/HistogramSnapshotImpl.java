package com.emc.mongoose.base.metrics.snapshot;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/** @author veronika K. on 25.09.18 */
public class HistogramSnapshotImpl implements HistogramSnapshot {

	private static final HistogramSnapshotImpl EMPTY = new HistogramSnapshotImpl(new TreeSet<Long>());

	private final TreeSet<Long> sortedVals;

	public HistogramSnapshotImpl(final TreeSet<Long> vals) {
		this.sortedVals = vals;
	}

	public static HistogramSnapshot aggregate(final List<HistogramSnapshot> snapshots) {
		if (0 == snapshots.size()) {
			return EMPTY;
		} else if (1 == snapshots.size()) {
			return snapshots.get(0);
		} else {
			final TreeSet<Long> valuesToAggregate = snapshots.get(0).values();
			for (int i = 1; i < snapshots.size(); i++) {
				valuesToAggregate.addAll(snapshots.get(i).values());
			}
			return new HistogramSnapshotImpl(valuesToAggregate);
		}
	}

	private long getFromTreeSet(final Iterator<Long> it, final long index){
		int i = 0;
		while(it.hasNext() && i < index) {
			i++;
		}
		return it.next();
	}

	@Override
	public long quantile(final double quantile) {
		int size = sortedVals.size();
		if (0 == size) {
			return 0;
		} else if (quantile >= 0.5 || quantile < 1.0) {
			return getFromTreeSet(sortedVals.descendingIterator(), size - (int) (quantile * size));
		} else if (quantile >= 0.0 || quantile < 0.5) {
			return getFromTreeSet(sortedVals.iterator(), (int) (quantile * size));
		}  else {
			throw new IllegalArgumentException(quantile + " is not in range [0..1)");
		}
	}

	@Override
	public final TreeSet<Long> values() {
		return sortedVals;
	}

	@Override
	public long last() {
		return sortedVals.last();
	}
}
