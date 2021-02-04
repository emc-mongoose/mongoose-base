package com.emc.mongoose.base.load.step.client.metrics;

import com.emc.mongoose.base.concurrent.ServiceTaskExecutor;
import com.emc.mongoose.base.load.step.LoadStep;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.metrics.snapshot.AllMetricsSnapshot;
import com.github.akurilov.fiber4j.ExclusiveFiberBase;
import com.github.akurilov.fiber4j.FibersExecutor;

import java.util.List;

import org.apache.logging.log4j.Level;

import static com.emc.mongoose.base.Exceptions.throwUncheckedIfInterrupted;

public final class MetricsSnapshotsSupplierTaskImpl extends ExclusiveFiberBase
				implements MetricsSnapshotsSupplierTask {

	private final LoadStep loadStep;
	private volatile List<? extends AllMetricsSnapshot> snapshotsByOrigin = null;
	private volatile boolean failedBeforeFlag = false;
	private long lastCalledMillis = 0;
	private int waitPeriodMillis;
	public MetricsSnapshotsSupplierTaskImpl(final LoadStep loadStep) {
		this(ServiceTaskExecutor.INSTANCE, loadStep);
		this.waitPeriodMillis = 100;
		// TODO: add a config flag on how often snapshots are aggregated
	}

	public MetricsSnapshotsSupplierTaskImpl(final FibersExecutor executor, final LoadStep loadStep) {
		super(executor);
		this.loadStep = loadStep;
	}

	@Override
	protected final void invokeTimedExclusively(final long startTimeNanos) {
		try {
			final long nextSnapshotUpdateTs = System.currentTimeMillis();
			if (nextSnapshotUpdateTs - lastCalledMillis >= waitPeriodMillis){
				snapshotsByOrigin = loadStep.metricsSnapshots();
				lastCalledMillis = nextSnapshotUpdateTs;
			}

		} catch (final Exception e) {
			throwUncheckedIfInterrupted(e);
			LogUtil.exception(Level.INFO, e, "Failed to fetch the metrics snapshots from \"{}\"", loadStep);
			if (failedBeforeFlag) {
				LogUtil.exception(
								Level.WARN, e, "Failed to fetch the metrics snapshots from \"{}\" twice, stopping", loadStep);
				stop();
			} else {
				failedBeforeFlag = true;
			}
		}
	}

	@Override
	public final List<? extends AllMetricsSnapshot> get() {
		return snapshotsByOrigin;
	}

	@Override
	protected final void doClose() {
		if (null != snapshotsByOrigin) {
			snapshotsByOrigin.clear();
		}
	}
}

//TODO: check how much this affects metrics and network traffic
//  if (startTimeNanos-lastCalledTime > 1_000_000_000) {
//				loadStep.metricsSnapshots();
//						//}
//						lastCalledTime = startTimeNanos;