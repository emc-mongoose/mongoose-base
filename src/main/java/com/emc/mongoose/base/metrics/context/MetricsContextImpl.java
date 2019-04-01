package com.emc.mongoose.base.metrics.context;

import com.emc.mongoose.base.item.op.OpType;
import com.emc.mongoose.base.metrics.MetricsConstants;
import com.emc.mongoose.base.metrics.snapshot.AllMetricsSnapshotImpl;
import com.emc.mongoose.base.metrics.snapshot.ConcurrencyMetricSnapshot;
import com.emc.mongoose.base.metrics.snapshot.TimingMetricSnapshot;
import com.emc.mongoose.base.metrics.type.ConcurrencyMeterImpl;
import com.emc.mongoose.base.metrics.type.HistogramImpl;
import com.emc.mongoose.base.metrics.type.LongMeter;
import com.emc.mongoose.base.metrics.type.RateMeter;
import com.emc.mongoose.base.metrics.type.RateMeterImpl;
import com.emc.mongoose.base.metrics.type.TimingMeterImpl;
import com.emc.mongoose.base.metrics.snapshot.RateMetricSnapshot;
import com.emc.mongoose.base.metrics.util.ConcurrentSlidingWindowLongReservoir;
import com.github.akurilov.commons.system.SizeInBytes;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntSupplier;

import static com.emc.mongoose.base.metrics.MetricsConstants.META_DATA_COMMENT;
import static com.emc.mongoose.base.metrics.MetricsConstants.META_DATA_ITEM_DATA_SIZE;
import static com.emc.mongoose.base.metrics.MetricsConstants.META_DATA_LIMIT_CONC;
import static com.emc.mongoose.base.metrics.MetricsConstants.META_DATA_OP_TYPE;
import static com.emc.mongoose.base.metrics.MetricsConstants.META_DATA_RUN_ID;
import static com.emc.mongoose.base.metrics.MetricsConstants.META_DATA_STEP_ID;

public class MetricsContextImpl<S extends AllMetricsSnapshotImpl> extends MetricsContextBase<S>
				implements MetricsContext<S> {

	private final LongMeter<TimingMetricSnapshot> reqDuration, respLatency;
	private final LongMeter<ConcurrencyMetricSnapshot> actualConcurrency;
	private final RateMeter<RateMetricSnapshot> throughputSuccess, throughputFail, reqBytes;
	private volatile TimingMetricSnapshot reqDurSnapshot, respLatSnapshot;
	private volatile ConcurrencyMetricSnapshot actualConcurrencySnapshot;
	private volatile long lastSnapshotsUpdateTs = 0;
	private final IntSupplier actualConcurrencyGauge;
	private final ReadWriteLock timingLock = new ReentrantReadWriteLock();
	private final Lock timingLockUpdate = timingLock.readLock();
	private final Lock timingsUpdateLock = timingLock.writeLock();

	public MetricsContextImpl(
					final Map metaData,
					final IntSupplier actualConcurrencyGauge,
					final int concurrencyThreshold,
					final int updateIntervalSec,
					final boolean stdOutColorFlag) {
		super(
						metaData,
						concurrencyThreshold,
						stdOutColorFlag,
						TimeUnit.SECONDS.toMillis(updateIntervalSec));
		//
		respLatency = new TimingMeterImpl(
						new HistogramImpl(new ConcurrentSlidingWindowLongReservoir(DEFAULT_RESERVOIR_SIZE)),
						MetricsConstants.METRIC_NAME_LAT);
		respLatSnapshot = respLatency.snapshot();
		//
		reqDuration = new TimingMeterImpl(
						new HistogramImpl(new ConcurrentSlidingWindowLongReservoir(DEFAULT_RESERVOIR_SIZE)),
						MetricsConstants.METRIC_NAME_DUR);
		reqDurSnapshot = reqDuration.snapshot();
		//
		this.actualConcurrencyGauge = actualConcurrencyGauge;
		actualConcurrency = new ConcurrencyMeterImpl(MetricsConstants.METRIC_NAME_CONC);
		actualConcurrencySnapshot = actualConcurrency.snapshot();
		//
		final var clock = Clock.systemUTC();
		//
		throughputSuccess = new RateMeterImpl(clock, MetricsConstants.METRIC_NAME_SUCC);
		//
		throughputFail = new RateMeterImpl(clock, MetricsConstants.METRIC_NAME_FAIL);
		//
		reqBytes = new RateMeterImpl(clock, MetricsConstants.METRIC_NAME_BYTE);
	}

	@Override
	public final void start() {
		super.start();
		throughputSuccess.resetStartTime();
		throughputFail.resetStartTime();
		reqBytes.resetStartTime();
	}

	@Override
	public final void markSucc(final long bytes, final long duration, final long latency) {
		throughputSuccess.update(1);
		reqBytes.update(bytes);
		updateTimings(latency, duration);
		if (thresholdMetricsCtx != null) {
			thresholdMetricsCtx.markSucc(bytes, duration, latency);
		}
	}

	@Override
	public final void markPartSucc(final long bytes, final long duration, final long latency) {
		reqBytes.update(bytes);
		updateTimings(latency, duration);
		if (thresholdMetricsCtx != null) {
			thresholdMetricsCtx.markPartSucc(bytes, duration, latency);
		}
	}

	@Override
	public final void markSucc(
					final long count, final long bytes, final long durationValues[], final long latencyValues[]) {
		throughputSuccess.update(count);
		reqBytes.update(bytes);
		final var timingsLen = Math.min(durationValues.length, latencyValues.length);
		long duration, latency;
		for (var i = 0; i < timingsLen; ++i) {
			duration = durationValues[i];
			latency = latencyValues[i];
			updateTimings(latency, duration);
		}
		if (thresholdMetricsCtx != null) {
			thresholdMetricsCtx.markSucc(count, bytes, durationValues, latencyValues);
		}
	}

	@Override
	public final void markPartSucc(
					final long bytes, final long durationValues[], final long latencyValues[]) {
		reqBytes.update(bytes);
		final var timingsLen = Math.min(durationValues.length, latencyValues.length);
		long duration, latency;
		for (var i = 0; i < timingsLen; ++i) {
			duration = durationValues[i];
			latency = latencyValues[i];
			updateTimings(latency, duration);
		}
		if (thresholdMetricsCtx != null) {
			thresholdMetricsCtx.markPartSucc(bytes, durationValues, latencyValues);
		}
	}

	private void updateTimings(final long latencyMicros, final long durationMicros) {
		if (latencyMicros > 0 && durationMicros > latencyMicros) {
			timingLockUpdate.lock();
			try {
				reqDuration.update(durationMicros);
				respLatency.update(latencyMicros);
			} finally {
				timingLockUpdate.unlock();
			}
		}
	}

	@Override
	public final void markFail() {
		throughputFail.update(1);
		if (thresholdMetricsCtx != null) {
			thresholdMetricsCtx.markFail();
		}
	}

	@Override
	public final void markFail(final long count) {
		throughputFail.update(count);
		if (thresholdMetricsCtx != null) {
			thresholdMetricsCtx.markFail(count);
		}
	}

	@Override
	public final boolean avgPersistEnabled() {
		return false;
	}

	@Override
	public final boolean sumPersistEnabled() {
		return false;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void refreshLastSnapshot() {
		final var currentTimeMillis = System.currentTimeMillis();
		if (currentTimeMillis - lastSnapshotsUpdateTs > DEFAULT_SNAPSHOT_UPDATE_PERIOD_MILLIS) {
			lastSnapshotsUpdateTs = currentTimeMillis;
			updateTimings();
			actualConcurrency.update(actualConcurrencyGauge.getAsInt());
			actualConcurrencySnapshot = actualConcurrency.snapshot();
		}
		lastSnapshot = (S) new AllMetricsSnapshotImpl(
						reqDurSnapshot,
						respLatSnapshot,
						actualConcurrencySnapshot,
						throughputFail.snapshot(),
						throughputSuccess.snapshot(),
						reqBytes.snapshot(),
						elapsedTimeMillis());
		super.refreshLastSnapshot();
	}

	private void updateTimings() {
		if (timingsUpdateLock.tryLock()) {
			try {
				reqDurSnapshot = reqDuration.snapshot();
				respLatSnapshot = respLatency.snapshot();
			} finally {
				timingsUpdateLock.unlock();
			}
		}
	}

	@Override
	protected MetricsContextImpl<S> newThresholdMetricsContext() {
		return new ContextBuilderImpl()
						.loadStepId(loadStepId())
						.opType(opType())
						.actualConcurrencyGauge(actualConcurrencyGauge)
						.concurrencyLimit(concurrencyLimit())
						.concurrencyThreshold(0)
						.itemDataSize(itemDataSize())
						.outputPeriodSec((int) TimeUnit.MILLISECONDS.toSeconds(outputPeriodMillis))
						.stdOutColorFlag(stdOutColorFlag)
						.runId("")
						.build();
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean equals(final Object other) {
		if (null == other) {
			return false;
		}
		if (other instanceof MetricsContextImpl) {
			return 0 == compareTo((MetricsContextImpl<S>) other);
		} else {
			return false;
		}
	}

	@Override
	public final String toString() {
		return getClass().getSimpleName()
						+ "("
						+ opType().name()
						+ '-'
						+ concurrencyLimit()
						+ "x1@"
						+ loadStepId()
						+ ")";
	}

	@Override
	public final void close() {
		super.close();
	}

	public static ContextBuilder builder() {
		return new ContextBuilderImpl();
	}

	private static class ContextBuilderImpl
					implements ContextBuilder<ContextBuilder, MetricsContextImpl> {

		private IntSupplier actualConcurrencyGauge;
		private int concurrencyThreshold;
		private boolean stdOutColorFlag;
		private int outputPeriodSec;
		private Map metaData = new HashMap();

		public MetricsContextImpl build() {
			return new MetricsContextImpl(
							metaData,
							actualConcurrencyGauge,
							concurrencyThreshold,
							outputPeriodSec,
							stdOutColorFlag);
		}

		@Override
		public ContextBuilderImpl loadStepId(final String id) {
			this.metaData.put(META_DATA_STEP_ID, id);
			return this;
		}

		@Override
		public ContextBuilderImpl runId(final String id){
			this.metaData.put(META_DATA_RUN_ID, id);
			return this;
		}

		@Override
		public ContextBuilder comment(final String comment) {
			this.metaData.put(META_DATA_COMMENT, comment);
			return this;
		}

		@Override
		public ContextBuilderImpl opType(final OpType opType) {
			this.metaData.put(META_DATA_OP_TYPE, opType);
			return this;
		}

		@Override
		public ContextBuilderImpl concurrencyLimit(final int concurrencyLimit) {
			this.metaData.put(META_DATA_LIMIT_CONC, concurrencyLimit);
			return this;
		}

		@Override
		public ContextBuilderImpl concurrencyThreshold(final int concurrencyThreshold) {
			this.concurrencyThreshold = concurrencyThreshold;
			return this;
		}

		@Override
		public ContextBuilderImpl itemDataSize(final SizeInBytes itemDataSize) {
			this.metaData.put(META_DATA_ITEM_DATA_SIZE, itemDataSize);
			return this;
		}

		@Override
		public ContextBuilderImpl stdOutColorFlag(final boolean stdOutColorFlag) {
			this.stdOutColorFlag = stdOutColorFlag;
			return this;
		}

		@Override
		public ContextBuilderImpl outputPeriodSec(final int outputPeriodSec) {
			this.outputPeriodSec = outputPeriodSec;
			return this;
		}

		@Override
		public ContextBuilderImpl actualConcurrencyGauge(final IntSupplier actualConcurrencyGauge) {
			this.actualConcurrencyGauge = actualConcurrencyGauge;
			return this;
		}
	}
}
