package com.emc.mongoose.base.metrics;

import static com.emc.mongoose.base.Constants.KEY_CLASS_NAME;
import static com.emc.mongoose.base.Constants.KEY_STEP_ID;
import static com.emc.mongoose.base.Exceptions.throwUncheckedIfInterrupted;
import static com.emc.mongoose.base.metrics.MetricsConstants.METRIC_LABELS;
import static com.emc.mongoose.base.metrics.TimingMetricType.LATENCY;
import static com.emc.mongoose.base.metrics.TimingMetricType.DURATION;
import static com.github.akurilov.commons.lang.Exceptions.throwUnchecked;
import static org.apache.logging.log4j.CloseableThreadContext.Instance;
import static org.apache.logging.log4j.CloseableThreadContext.put;

import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.logging.MetricsAsciiTableLogMessage;
import com.emc.mongoose.base.logging.MetricsCsvLogMessage;
import com.emc.mongoose.base.logging.MetricsTotalCsvLogMessage;
import com.emc.mongoose.base.logging.StepResultsMetricsLogMessage;
import com.emc.mongoose.base.metrics.context.DistributedMetricsContext;
import com.emc.mongoose.base.metrics.context.MetricsContext;
import com.emc.mongoose.base.metrics.snapshot.TimingMetricQuantileResultsImpl;
import com.emc.mongoose.base.metrics.snapshot.AllMetricsSnapshot;
import com.emc.mongoose.base.metrics.snapshot.ConcurrencyMetricSnapshot;
import com.emc.mongoose.base.metrics.snapshot.DistributedAllMetricsSnapshot;
import com.emc.mongoose.base.metrics.util.PrometheusMetricsExporter;
import com.emc.mongoose.base.metrics.util.PrometheusMetricsExporterImpl;
import com.github.akurilov.fiber4j.ExclusiveFiberBase;
import com.github.akurilov.fiber4j.Fiber;
import com.github.akurilov.fiber4j.FibersExecutor;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

/** Created by kurila on 18.05.17. */
public class MetricsManagerImpl extends ExclusiveFiberBase implements MetricsManager {

	private static final String CLS_NAME = MetricsManagerImpl.class.getSimpleName();
	private final Set<MetricsContext> allMetrics = new ConcurrentSkipListSet<>();
	private final Map<DistributedMetricsContext, PrometheusMetricsExporter> distributedMetrics = new ConcurrentHashMap<>();
	private final Set<MetricsContext> selectedMetrics = new TreeSet<>();
	private final Lock outputLock = new ReentrantLock();

	public MetricsManagerImpl(final FibersExecutor instance) {
		super(instance);
	}

	@Override
	protected final void invokeTimedExclusively(final long startTimeNanos) {
		ThreadContext.put(KEY_CLASS_NAME, CLS_NAME);
		int actualConcurrency = 0;
		int nextConcurrencyThreshold;
		if (outputLock.tryLock()) {
			try {
				for (final MetricsContext metricsCtx : allMetrics) {
					ThreadContext.put(KEY_STEP_ID, metricsCtx.loadStepId());
					// TODO: as a future improvement consider whether throttling is needed here.
					metricsCtx.refreshLastSnapshot();

					final AllMetricsSnapshot snapshot = metricsCtx.lastSnapshot();
					if (null != snapshot) {
						final ConcurrencyMetricSnapshot concurrencySnapshot = snapshot.concurrencySnapshot();
						if (null != concurrencySnapshot) {
							actualConcurrency = (int) concurrencySnapshot.last();
						}
						// threshold load state checks
						nextConcurrencyThreshold = metricsCtx.concurrencyThreshold();
						if (nextConcurrencyThreshold > 0 && actualConcurrency >= nextConcurrencyThreshold) {
							if (!metricsCtx.thresholdStateEntered() && !metricsCtx.thresholdStateExited()) {
								Loggers.MSG.info(
												"{}: the threshold of {} active load operations count is reached, "
																+ "starting the additional metrics accounting",
												metricsCtx.toString(),
												metricsCtx.concurrencyThreshold());
								metricsCtx.enterThresholdState();
							}
						} else if (metricsCtx.thresholdStateEntered() && !metricsCtx.thresholdStateExited()) {
							exitMetricsThresholdState(metricsCtx);
						}
						// periodic output
						final long outputPeriodMillis = metricsCtx.outputPeriodMillis();
						final long lastOutputTs = metricsCtx.lastOutputTs();
						final long nextOutputTs = System.currentTimeMillis();
						if (outputPeriodMillis > 0 && nextOutputTs - lastOutputTs >= outputPeriodMillis) {
							metricsCtx.lastOutputTs(nextOutputTs);
							selectedMetrics.add(metricsCtx);
							if (metricsCtx.avgPersistEnabled()) {
								Loggers.METRICS_FILE.info(
												new MetricsCsvLogMessage(
																snapshot, metricsCtx.opType(), metricsCtx.concurrencyLimit()));
							}
						}
					}
				}
				// console output
				if (!selectedMetrics.isEmpty()) {
					Loggers.METRICS_STD_OUT.info(new MetricsAsciiTableLogMessage(selectedMetrics));
					selectedMetrics.clear();
				}
			} catch (final ConcurrentModificationException ignored) {} catch (final Throwable cause) {
				throwUncheckedIfInterrupted(cause);
				LogUtil.exception(Level.DEBUG, cause, "Metrics manager failure");
			} finally {
				outputLock.unlock();
			}
		}
	}

	private void startIfNotStarted() {
		if (!isStarted()) {
			super.start();
			Loggers.MSG.debug("Started the metrics manager fiber");
		}
	}

	@Override
	public void register(final MetricsContext metricsCtx) {
		try {
			startIfNotStarted();
			allMetrics.add(metricsCtx);
			if (metricsCtx instanceof DistributedMetricsContext) {
				final var distributedMetricsCtx = (DistributedMetricsContext) metricsCtx;
				final String[] labelValues = {
						metricsCtx.loadStepId(),
						metricsCtx.opType().name(),
						String.valueOf(metricsCtx.concurrencyLimit()),
						metricsCtx.itemDataSize().toString(),
						String.valueOf(metricsCtx.startTimeStamp()),
						((DistributedMetricsContext) metricsCtx).nodeAddrs().toString(),
						metricsCtx.comment(),
						String.valueOf(metricsCtx.runId())
				};
				distributedMetrics.put(
								distributedMetricsCtx,
								new PrometheusMetricsExporterImpl(distributedMetricsCtx)
												.labels(METRIC_LABELS, labelValues)
												//.quantiles(distributedMetricsCtx.quantileValues())
												.register());
			}
			Loggers.MSG.debug("Metrics context \"{}\" registered", metricsCtx);
		} catch (final RuntimeException e) {
			throwUncheckedIfInterrupted(e);
			LogUtil.exception(
							Level.WARN,
							e,
							"Failed to register the Prometheus Exporter for the metrics context \"{}\"",
							metricsCtx.toString());
		}
	}

	@Override
	public void unregister(final MetricsContext metricsCtx) {
		try (final Instance logCtx = put(KEY_STEP_ID, metricsCtx.loadStepId()).put(KEY_CLASS_NAME, getClass().getSimpleName())) {
			if (allMetrics.remove(metricsCtx)) {
				TimingMetricQuantileResultsImpl latencyQuantiles = null;
				TimingMetricQuantileResultsImpl durationQuantiles = null;
				try {
					if (!outputLock.tryLock(Fiber.WARN_DURATION_LIMIT_NANOS, TimeUnit.NANOSECONDS)) {
						Loggers.ERR.warn(
										"Acquire lock timeout while unregistering the metrics context \"{}\"", metricsCtx);
					}
					metricsCtx.refreshLastSnapshot(); // one last time
					final AllMetricsSnapshot snapshot = metricsCtx.lastSnapshot();
					// check for the metrics threshold state if entered
					if (metricsCtx.thresholdStateEntered() && !metricsCtx.thresholdStateExited()) {
						exitMetricsThresholdState(metricsCtx);
					}

					if (metricsCtx instanceof DistributedMetricsContext) {
						final DistributedMetricsContext<?> distributedMetricsCtx = (DistributedMetricsContext<?>) metricsCtx;
						final String timingMetricsDirPath = System.getProperty("java.io.tmpdir") + "/mongoose/";
						final String timingMetricsFilePattern = "timingMetrics_" + metricsCtx.loadStepId();
						latencyQuantiles = new TimingMetricQuantileResultsImpl(distributedMetricsCtx.quantileValues(),
								LATENCY, distributedMetricsCtx.nodeCount(), timingMetricsDirPath,
								timingMetricsFilePattern, metricsCtx.timingPersistEnabled());
						durationQuantiles = new TimingMetricQuantileResultsImpl(distributedMetricsCtx.quantileValues(),
								DURATION, distributedMetricsCtx.nodeCount(), timingMetricsDirPath,
								timingMetricsFilePattern, metricsCtx.timingPersistEnabled());

					if (null != snapshot) {
						// file output
						// due to unknown reasons writing to a csv.total is based on a flag and not on a metrics
						// class instance. though this flag is only enabled for distributed context.
						if (metricsCtx.sumPersistEnabled()) {
							Loggers.METRICS_FILE_TOTAL.info(
											new MetricsTotalCsvLogMessage(snapshot, metricsCtx.opType(),
													metricsCtx.concurrencyLimit(), latencyQuantiles.getMetricsValues(),
													durationQuantiles.getMetricsValues()));
						}
					}
					// console output
						Loggers.METRICS_STD_OUT.info(
										new MetricsAsciiTableLogMessage(Collections.singleton(metricsCtx)));
						final DistributedAllMetricsSnapshot aggregSnapshot = (DistributedAllMetricsSnapshot) snapshot;
						if (null != aggregSnapshot) {
							Loggers.METRICS_STD_OUT.info(
											new StepResultsMetricsLogMessage(
															metricsCtx.opType(),
															metricsCtx.loadStepId(),
															metricsCtx.concurrencyLimit(),
															aggregSnapshot,
															latencyQuantiles.getMetricsValues(),
															durationQuantiles.getMetricsValues()));
						} else {
							Loggers.ERR.warn("Metrics snapshot is empty. No metrics were recorded apparently.");
						}

						final PrometheusMetricsExporter exporter = distributedMetrics.remove(distributedMetricsCtx);
						if (null != exporter) {
							CollectorRegistry.defaultRegistry.unregister((Collector) exporter);
						}
					}
				} catch (final InterruptedException e) {
					throwUnchecked(e);
				} finally {
					try {
						outputLock.unlock();
						if (null != latencyQuantiles && null != durationQuantiles) {
							latencyQuantiles.close();
							durationQuantiles.close();
						}
					} catch (final IllegalMonitorStateException ignored) {
					} catch (final IOException e) {
						LogUtil.exception(Level.WARN, e,
								"probably failed to delete one of the tmp local files");
					}
				}
			} else {
				Loggers.ERR.debug("Metrics context \"{}\" has not been registered", metricsCtx);
			}
			Loggers.MSG.debug("Metrics context \"{}\" unregistered", metricsCtx);
		} finally {
			if (allMetrics.isEmpty()) {
				stop();
				Loggers.MSG.debug("Stopped the metrics manager fiber");
			}
		}
	}

	private static void exitMetricsThresholdState(final MetricsContext<?> metricsCtx) {
		Loggers.MSG.info(
						"{}: the active load operations count is below the threshold of {}, stopping the additional metrics "
										+ "accounting",
						metricsCtx.toString(),
						metricsCtx.concurrencyThreshold());
		final MetricsContext lastThresholdMetrics = metricsCtx.thresholdMetrics();
		final AllMetricsSnapshot snapshot = lastThresholdMetrics.lastSnapshot();
		if (lastThresholdMetrics.sumPersistEnabled()) {
			Loggers.METRICS_THRESHOLD_FILE_TOTAL.info(
							new MetricsCsvLogMessage(snapshot, metricsCtx.opType(), metricsCtx.concurrencyLimit()));
		}
		metricsCtx.exitThresholdState();
	}

	@Override
	protected final void doClose() {
		allMetrics.forEach(MetricsContext::close);
		allMetrics.clear();
		distributedMetrics.clear();
	}
}
