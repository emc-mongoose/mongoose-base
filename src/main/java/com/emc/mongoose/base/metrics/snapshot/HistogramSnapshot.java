package com.emc.mongoose.base.metrics.snapshot;

import java.util.TreeSet;

/** @author veronika K. on 03.10.18 */
public interface HistogramSnapshot extends LongLastMetricSnapshot {

	long quantile(final double quantile);

	TreeSet<Long> values();

	@Override
	long last();
}
