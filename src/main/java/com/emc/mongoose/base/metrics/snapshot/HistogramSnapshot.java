package com.emc.mongoose.base.metrics.snapshot;

import org.HdrHistogram.Histogram;

/** @author veronika K. on 03.10.18 */
public interface HistogramSnapshot extends LongLastMetricSnapshot {

	long quantile(final double quantile);

	long last();

	Histogram histogram();
}
