package com.emc.mongoose.base.load.step.local.context;

import static com.emc.mongoose.base.Constants.KEY_CLASS_NAME;
import static com.emc.mongoose.base.Constants.KEY_STEP_ID;
import static com.emc.mongoose.base.Exceptions.throwUncheckedIfInterrupted;
import static com.github.akurilov.commons.concurrent.AsyncRunnable.State.SHUTDOWN;
import static com.github.akurilov.commons.concurrent.AsyncRunnable.State.STARTED;
import static com.github.akurilov.commons.lang.Exceptions.throwUnchecked;
import static org.apache.logging.log4j.CloseableThreadContext.Instance;

import com.emc.mongoose.base.concurrent.DaemonBase;
import com.emc.mongoose.base.item.DataItem;
import com.emc.mongoose.base.item.Item;
import com.emc.mongoose.base.item.op.Operation;
import com.emc.mongoose.base.item.op.Operation.Status;
import com.emc.mongoose.base.item.op.composite.CompositeOperation;
import com.emc.mongoose.base.item.op.data.DataOperation;
import com.emc.mongoose.base.item.op.partial.PartialOperation;
import com.emc.mongoose.base.item.op.path.PathOperation;
import com.emc.mongoose.base.load.generator.LoadGenerator;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.logging.OperationTraceCsvBatchLogMessage;
import com.emc.mongoose.base.logging.OperationTraceCsvLogMessage;
import com.emc.mongoose.base.metrics.context.MetricsContext;
import com.emc.mongoose.base.metrics.snapshot.AllMetricsSnapshot;
import com.emc.mongoose.base.storage.driver.StorageDriver;
import com.github.akurilov.commons.io.Output;
import com.github.akurilov.commons.reflection.TypeUtil;
import com.github.akurilov.commons.system.SizeInBytes;
import com.github.akurilov.confuse.Config;
import java.io.EOFException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.SplittableRandom;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

/** Created by kurila on 12.07.16. */
public class LoadStepContextImpl<I extends Item, O extends Operation<I>> extends DaemonBase
				implements LoadStepContext<I, O> {

	private final String id;
	private final LoadGenerator<I, O> generator;
	private final StorageDriver<I, O> driver;
	private final long countLimit;
	private final long sizeLimit;
	private final long failCountLimit;
	private final boolean failRateLimitFlag;
	private final ConcurrentMap<I, O> latestSuccOpResultByItem;
	private final boolean recycleFlag;
	private final boolean retryFlag;
	private final MetricsContext metricsCtx;
	private final LongAdder counterResults = new LongAdder();
	private final boolean tracePersistFlag;
	private final int batchSize;
	private volatile Output<O> opsResultsOutput;
	private volatile Output<O> opsMetricsOutput;
	private final boolean waitOpFinishBeforeStop;
	private final int waitOpFinishLimit;
	private final boolean outputDuplicates;
	private final boolean updateContents;
	private final ThreadLocal<SplittableRandom> rand = ThreadLocal.withInitial(SplittableRandom::new);

	/** @param id test step id */
	public LoadStepContextImpl(
					final String id,
					final LoadGenerator<I, O> generator,
					final StorageDriver<I, O> driver,
					final MetricsContext metricsCtx,
					final Config loadConfig,
					final boolean tracePersistFlag) {
		this.id = id;
		this.generator = generator;
		this.driver = driver;
		this.driver.operationResultOutput(this);
		this.metricsCtx = metricsCtx;
		this.tracePersistFlag = tracePersistFlag;
		this.batchSize = loadConfig.intVal("batch-size");
		final Config opConfig = loadConfig.configVal("op");
		final var recycleConfig = opConfig.configVal("recycle");
		this.recycleFlag = recycleConfig.boolVal("mode");
		this.updateContents = recycleConfig.boolVal("content-update");
		this.retryFlag = opConfig.boolVal("retry");
		final Config opLimitConfig = opConfig.configVal("limit");
		final int recycleLimit = opLimitConfig.intVal("recycle");
		if (recycleFlag || retryFlag) {
			latestSuccOpResultByItem = new ConcurrentHashMap<>(recycleLimit);
		} else {
			latestSuccOpResultByItem = null;
		}
		final long configCountLimit = opLimitConfig.longVal("count");
		this.countLimit = configCountLimit > 0 ? configCountLimit : Long.MAX_VALUE;
		final SizeInBytes configSizeLimit;
		final Config stepLimitConfig = loadConfig.configVal("step-limit");
		final Object configSizeLimitRaw = stepLimitConfig.val("size");
		if (configSizeLimitRaw instanceof String) {
			configSizeLimit = new SizeInBytes((String) configSizeLimitRaw);
		} else {
			configSizeLimit = new SizeInBytes(TypeUtil.typeConvert(configSizeLimitRaw, long.class));
		}
		this.sizeLimit = configSizeLimit.get() > 0 ? configSizeLimit.get() : Long.MAX_VALUE;
		final Config failConfig = opLimitConfig.configVal("fail");
		final long configFailCount = failConfig.longVal("count");
		this.failCountLimit = configFailCount > 0 ? configFailCount : Long.MAX_VALUE;
		this.failRateLimitFlag = failConfig.boolVal("rate");
		this.waitOpFinishBeforeStop = opConfig.boolVal("wait-finish");
		this.waitOpFinishLimit = opConfig.intVal("wait-limit");
		this.outputDuplicates = opConfig.boolVal("output-duplicates");
	}

	@Override
	public boolean isDone() {
		if (!STARTED.equals(state()) && !SHUTDOWN.equals(state())) {
			Loggers.MSG.debug("{}: done due to {} state", id, state());
			return true;
		}
		if (isDoneCountLimit()) {
			Loggers.MSG.debug("{}: done due to max count ({}) done state", id, countLimit);
			return true;
		}
		if (isDoneSizeLimit()) {
			Loggers.MSG.debug("{}: done due to max size done state", id);
			return true;
		}
		if (isFailThresholdReached()) {
			Loggers.ERR.warn("{}: done due to \"BAD\" state", id);
			return true;
		}
		if (!recycleFlag && allOperationsCompleted()) {
			Loggers.MSG.debug(
							"{}: done due to all {} load operations have been completed",
							id,
							generator.generatedOpCount());
			return true;
		}
		// issue SLTM-938 fix
		if (isNothingToRecycle()) {
			Loggers.ERR.warn("{}: no load operations to recycle (all failed?)", id);
			return true;
		}
		return false;
	}

	private boolean isDoneCountLimit() {
		if (countLimit > 0) {
			if (counterResults.sum() >= countLimit) {
				Loggers.MSG.debug(
								"{}: count limit reached, {} results >= {} limit",
								id,
								counterResults.sum(),
								countLimit);
				return true;
			}
			final AllMetricsSnapshot lastStats = metricsCtx.lastSnapshot();
			final long succCountSum = lastStats.successSnapshot().count();
			final long failCountSum = lastStats.failsSnapshot().count();
			if (succCountSum + failCountSum >= countLimit) {
				Loggers.MSG.debug(
								"{}: count limit reached, {} successful + {} failed >= {} limit",
								id,
								succCountSum,
								failCountSum,
								countLimit);
				return true;
			}
		}
		return false;
	}

	private boolean isDoneSizeLimit() {
		if (sizeLimit > 0) {
			final long sizeSum = metricsCtx.lastSnapshot().byteSnapshot().count();
			if (sizeSum >= sizeLimit) {
				Loggers.MSG.debug(
								"{}: size limit reached, done {} >= {} limit",
								id,
								SizeInBytes.formatFixedSize(sizeSum),
								sizeLimit);
				return true;
			}
		}
		return false;
	}

	private boolean allOperationsCompleted() {
		try {
			if (generator.isStopped()) {
				return counterResults.longValue() >= generator.generatedOpCount();
			}
		} catch (final RemoteException ignored) {}
		return false;
	}

	// issue SLTM-938 fix
	private boolean isNothingToRecycle() {
		final long resultCount = counterResults.sum();
		return recycleFlag
						&& generator.isNothingToRecycle()
						&&
						// all generated ops executed at least once
						resultCount > 0
						&& resultCount >= generator.generatedOpCount()
						&&
						// no successful op results
						latestSuccOpResultByItem.size() == 0;
	}

	/**
	 * @return true if the configured failures threshold is reached and the step should be stopped,
	 *     false otherwise
	 */
	private boolean isFailThresholdReached() {
		final AllMetricsSnapshot allMetricsSnapshot = metricsCtx.lastSnapshot();
		final long failCountSum = allMetricsSnapshot.failsSnapshot().count();
		final double failRateLast = allMetricsSnapshot.failsSnapshot().last();
		final double succRateLast = allMetricsSnapshot.successSnapshot().last();
		if (failCountSum > failCountLimit) {
			Loggers.ERR.warn(
							"{}: failure count ({}) is more than the configured limit ({}), stopping the step",
							id,
							failCountSum,
							failCountLimit);
			return true;
		}
		if (failRateLimitFlag && failRateLast > succRateLast) {
			Loggers.ERR.warn(
							"{}: failures rate ({} failures/sec) is more than success rate ({} op/sec), stopping the step",
							id,
							failRateLast,
							succRateLast);
			return true;
		}
		return false;
	}

	private boolean isIdle() throws ConcurrentModificationException {
		try {
			if (!generator.isStopped() && !generator.isClosed()) {
				return false;
			}
			if (!driver.isStopped() && !driver.isClosed() && !driver.isIdle()) {
				return false;
			}
		} catch (final RemoteException ignored) {}
		return true;
	}

	@Override
	public final void operationsResultsOutput(final Output<O> opsResultsOutput) {
		this.opsResultsOutput = opsResultsOutput;
	}

	@Override
	public final void operationsMetricsOutput(final Output<O> opsMetricsOutput) {
		this.opsMetricsOutput = opsMetricsOutput;
	}

	@Override
	public final int activeOpCount() {
		return driver.activeOpCount();
	}

	@Override
	public final boolean put(final O opResult) {
		ThreadContext.put(KEY_STEP_ID, id);
		// I/O trace logging
		if (tracePersistFlag) {
			Loggers.OP_TRACES.info(new OperationTraceCsvLogMessage<>(opResult));
		}
		// account the completed composite ops only
		if (opResult instanceof CompositeOperation
						&& !((CompositeOperation) opResult).allSubOperationsDone()) {
			return true;
		}
		final Status status = opResult.status();
		if (Status.SUCC.equals(status)) {
			final long reqDuration = opResult.duration();
			final long respLatency = opResult.latency();
			final long countBytesDone;
			if (opResult instanceof DataOperation) {
				countBytesDone = ((DataOperation) opResult).countBytesDone();
			} else if (opResult instanceof PathOperation) {
				countBytesDone = ((PathOperation) opResult).countBytesDone();
			} else {
				countBytesDone = 0;
			}
			if (opResult instanceof PartialOperation) {
				metricsCtx.markPartSucc(countBytesDone, reqDuration, respLatency);
			} else {
				if (!recycleFlag) {
					// recycled ops should only appear in output.csv only once unless
					// outputDuplicates flag is specified
					outputResults(opResult);
				} else {
					// for recycled ops we might want to print them once or every time
					if (outputDuplicates) {
						outputResults(opResult);
					} else {
						// this way we only add duplicate items once to the output list
						latestSuccOpResultByItem.put(opResult.item(), opResult);
					}

					// for recycled ops we might also want to update contents before recycling
					if (updateContents) {
						//if (recycleFlag && updateContents) {
						final var dataItem = (DataItem) opResult.item();
						// TODO: possible change: remove dataItem.offset() to improve perf and increase variability
						dataItem.offset(dataItem.offset() + rand.get().nextLong());
					}
					generator.recycle(opResult);
				}

				// each recycled op's lat and dur should be written to file each time
				// just like regular op
				outputTimingMetrics(opResult);
				metricsCtx.markSucc(countBytesDone, reqDuration, respLatency);
				counterResults.increment();
			}
		} else if (Status.PENDING.equals(status)) {
			// in case driver cannot finish operation due to storage API issues or of some other sort, we need
			// to set the operation status to Pending, so that we don't count it in the metrics and recycle the operation
			generator.recycle(opResult);
			counterResults.increment();
		} else if (Status.OMIT.equals(status)) {
			// operation status is set to Omit in case we want an operation to complete, but not to register
			// in the metrics in any way
			outputResults(opResult);
		} else {
			if (recycleFlag) {
				latestSuccOpResultByItem.remove(opResult.item());
			}
			if (!Status.INTERRUPTED.equals(status)) {
				if (retryFlag) {
					generator.recycle(opResult);
				} else {
					Loggers.ERR.debug("{}: {}", opResult.toString(), status.toString());
					metricsCtx.markFail();
					counterResults.increment();
				}
			}
		}
		return true;
	}

	@Override
	public final int put(final List<O> opResults, final int from, final int to) {
		ThreadContext.put(KEY_STEP_ID, id);
		// I/O trace logging
		if (tracePersistFlag) {
			Loggers.OP_TRACES.info(new OperationTraceCsvBatchLogMessage<>(opResults, from, to));
		}
		O opResult;
		Status status;
		long reqDuration;
		long respLatency;
		long countBytesDone = 0;
		int i;
		for (i = from; i < to; i++) {
			opResult = opResults.get(i);
			// account the completed composite ops only
			if (opResult instanceof CompositeOperation
							&& !((CompositeOperation) opResult).allSubOperationsDone()) {
				continue;
			}
			status = opResult.status();
			reqDuration = opResult.duration();
			respLatency = opResult.latency();
			if (opResult instanceof DataOperation) {
				countBytesDone = ((DataOperation) opResult).countBytesDone();
			} else if (opResult instanceof PathOperation) {
				countBytesDone = ((PathOperation) opResult).countBytesDone();
			}
			if (Status.SUCC.equals(status)) {
				if (opResult instanceof PartialOperation) {
					metricsCtx.markPartSucc(countBytesDone, reqDuration, respLatency);
				} else {
					if (!recycleFlag) {
						// recycled ops should only appear in output.csv only once unless
						// outputDuplicates flag is specified
						outputResults(opResult);
					} else {
						// for recycled ops we might want to print them once or every time
						if (outputDuplicates) {
							outputResults(opResult);
						} else {
							// this way we only add duplicate items once to the output list
							latestSuccOpResultByItem.put(opResult.item(), opResult);
						}

						// for recycled ops we might also want to update contents before recycling
						if (updateContents) {
							//if (recycleFlag && updateContents) {
							final var dataItem = (DataItem) opResult.item();
							// TODO: possible change: remove dataItem.offset() to improve perf and increase variability
							dataItem.offset(dataItem.offset() + rand.get().nextLong());
						}
						generator.recycle(opResult);
					}

					// each recycled op's lat and dur should be written to file each time
					// just like regular op
					outputTimingMetrics(opResult);
					metricsCtx.markSucc(countBytesDone, reqDuration, respLatency);
					counterResults.increment();
				}
			} else if (Status.PENDING.equals(status)) {
				// in case driver cannot finish operation due to storage API issues or of some other sort, we need
				// to set the operation status to Pending, so that we don't count it in the metrics and recycle the operation
				generator.recycle(opResult);
				counterResults.increment();
			} else if (Status.OMIT.equals(status)) {
				// operation status is set to Omit in case we want an operation to complete, but not to register
				// in the metrics in any way
				outputResults(opResult);
			} else {
				if (recycleFlag) {
					latestSuccOpResultByItem.remove(opResult.item());
				}
				if (!Status.INTERRUPTED.equals(status)) {
					if (retryFlag) {
						generator.recycle(opResult);
					} else {
						Loggers.ERR.debug("{}: {}", opResult.toString(), status.toString());
						metricsCtx.markFail();
						counterResults.increment();
					}
				}
			}
		}
		return i - from;
	}

	@Override
	public final int put(final List<O> opsResults) {
		return put(opsResults, 0, opsResults.size());
	}

	@Override
	protected void doStart() throws IllegalStateException {
		try {
			driver.start();
		} catch (final RemoteException ignored) {} catch (final IllegalStateException e) {
			LogUtil.exception(Level.WARN, e, "{}: failed to start the storage driver \"{}\"", id, driver);
		}
		try {
			generator.start();
		} catch (final RemoteException ignored) {} catch (final IllegalStateException e) {
			LogUtil.exception(
							Level.WARN, e, "{}: failed to start the load generator \"{}\"", id, generator);
		}
	}
	private void outputResults(final O opResult) {
		final var opsResultsOutput = this.opsResultsOutput;
		if (opsResultsOutput != null) {
			try {
				if (!opsResultsOutput.put(opResult)) {
					Loggers.ERR.warn("Failed to output the I/O result");
				}
			} catch (final Exception e) {
				throwUncheckedIfInterrupted(e);
				if (e instanceof EOFException) {
					LogUtil.exception(Level.DEBUG, e, "Load operations results destination end of input");
				} else if (e instanceof IOException) {
					LogUtil.exception(
							Level.WARN, e, "Failed to put the load operation to the destination");
				} else {
					throw e;
				}
			}
		}
	}

	private void outputTimingMetrics(final O opResult) {
		final var opsMetricsOutput = this.opsMetricsOutput;
		if (opsMetricsOutput != null) {
			try {
				if (!opsMetricsOutput.put(opResult)) {
					Loggers.ERR.warn("Failed to output the operation metrics");
				}
			} catch (final Exception e) {
				throwUncheckedIfInterrupted(e);
				if (e instanceof EOFException) {
					LogUtil.exception(Level.DEBUG, e, "Load operations metrics result destination end of input");
				} else if (e instanceof IOException) {
					LogUtil.exception(
							Level.WARN, e, "Failed to put the load operation metrics to the destination");
				} else {
					throw e;
				}
			}
		}
	}

	@Override
	protected final void doShutdown() {
		try (final Instance ctx = CloseableThreadContext.put(KEY_STEP_ID, id)
						.put(KEY_CLASS_NAME, getClass().getSimpleName())) {
			generator.stop();
			Loggers.MSG.debug("{}: load generator \"{}\" stopped", id, generator.toString());
		} catch (final RemoteException ignored) {}
		try (final Instance ctx = CloseableThreadContext.put(KEY_STEP_ID, id)
						.put(KEY_CLASS_NAME, getClass().getSimpleName())) {
			driver.shutdown();
			Loggers.MSG.debug("{}: storage driver {} shutdown", id, driver.toString());
		} catch (final RemoteException ignored) {}
	}

	@Override
	protected final void doStop() throws IllegalStateException {
		if (waitOpFinishBeforeStop) {
			var i = 0;
			var sleep = 1000;
			for (; ((activeOpCount() != 0) && !Thread.currentThread().isInterrupted()) && ((i * sleep)/1000D < waitOpFinishLimit); i++) {
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
					Loggers.MSG.debug("couldn't put context thread {} to sleep or was interrupted", this);
				}
			}
			Loggers.MSG.info("{}: waited {}s for ops to finish (active count = {})", id, i, activeOpCount());
		}

		driver.stop();

		if (latestSuccOpResultByItem != null && opsResultsOutput != null) {
			try {
				final var ioResultCount = latestSuccOpResultByItem.size();
				Loggers.MSG.info(
								"{}: please wait while performing {} I/O results output...", id, ioResultCount);
				for (final var latestOpResult : latestSuccOpResultByItem.values()) {
					try {
						if (!opsResultsOutput.put(latestOpResult)) {
							Loggers.ERR.debug(
											"{}: item info output fails to ingest, blocking the closing method", id);
							while (!opsResultsOutput.put(latestOpResult)) {
								Thread.sleep(1);
							}
							Loggers.MSG.debug("{}: closing method unblocked", id);
						}
					} catch (final Exception e) {
						if (e instanceof IOException) {
							LogUtil.exception(Level.WARN, e, "{}: failed to output the latest results", id);
						} else {
							throw e;
						}
					}
				}
			} catch (final InterruptedException e) {
				throwUnchecked(e);
			} finally {
				Loggers.MSG.info("{}: I/O results output done", id);
			}
			latestSuccOpResultByItem.clear();
		}

		if (opsResultsOutput != null) {
			try {
				opsResultsOutput.put((O) null);
				Loggers.MSG.debug("{}: poisoned the items output", id);
			} catch (final NullPointerException e) {
				LogUtil.exception(
								Level.ERROR,
								e,
								"{}: results output \"{}\" failed to eat the poison",
								id,
								opsResultsOutput);
			} catch (final Exception e) {
				if (e instanceof IOException) {
					LogUtil.exception(Level.WARN, e, "{}: failed to poison the results output", id);
				} else {
					throw e;
				}
			}
		}

		if (opsMetricsOutput != null) {
			try {
				opsMetricsOutput.put((O) null);
				Loggers.MSG.debug("{}: poisoned the items timing metrics output", id);
			} catch (final NullPointerException e) {
				LogUtil.exception(
						Level.ERROR,
						e,
						"{}: timing metrics results output \"{}\" failed to eat the poison",
						id,
						opsMetricsOutput);
			} catch (final Exception e) {
				if (e instanceof IOException) {
					LogUtil.exception(Level.WARN, e, "{}: failed to poison the timing metrics results output", id);
				} else {
					throw e;
				}
			}
		}

		Loggers.MSG.debug("{}: interrupted the load step context", id);
	}

	@Override
	protected final void doClose() {
		try (final Instance logCtx = CloseableThreadContext.put(KEY_STEP_ID, id)
						.put(KEY_CLASS_NAME, getClass().getSimpleName())) {
			try {
				generator.close();
			} catch (final IOException e) {
				LogUtil.exception(
								Level.ERROR, e, "Failed to close the load generator \"{}\"", generator.toString());
			}
			try {
				driver.close();
			} catch (final IOException e) {
				LogUtil.exception(
								Level.ERROR, e, "Failed to close the storage driver \"{}\"", driver.toString());
			}
			Loggers.MSG.debug("{}: closed the load step context", id);
		}
	}
}
