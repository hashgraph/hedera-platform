/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.virtualmap.internal.pipeline;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.virtualmap.VirtualMapSettingsFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.VIRTUAL_MERKLE_STATS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * <p>
 * Manages the lifecycle of an object that implements {@link VirtualRoot}.
 * </p>
 *
 * <p>
 * This pipeline is responsible for enforcing the following invariants and constraints:
 * </p>
 *
 * <hr>
 * <p><strong>General</strong></p>
 *
 * <ul>
 * 	<li>all copies must be <strong>flushed</strong> or <strong>merged</strong> prior to eviction from memory</li>
 * 	<li>a copy can only be <strong>flushed</strong> or <strong>merged</strong>, not both</li>
 * 	<li>no <strong>flushes</strong> or <strong>merges</strong> are processed during copy detachment</li>
 * 	<li>pipelines can be terminated even when not all copies are released or detached (e.g. during reconnect
 * 		or node shutdown). A terminated pipeline is not required to <strong>flush</strong> or <strong>merge</strong>
 * 		copies before those copies are collected by the java garbage collector.</li>
 * </ul>
 *
 * <hr>
 * <p><strong>Flushing</strong></p>
 * <ul>
 * <li>
 * only immutable copies can be <strong>flushed</strong>
 * </li>
 * <li>
 * only the oldest unreleased copy can be <strong>flushed</strong>
 * </li>
 * </ul>
 *
 * <hr>
 * <p><strong>Merging</strong></p>
 * <ul>
 * 	<li>only released or detached copies can be <strong>merged</strong>
 * 	<li>copies can ony be <strong>merged</strong> into immutable copies</li>
 * </ul>
 *
 * <hr>
 * <p><strong>Hashing</strong></p>
 * <ul>
 * <li>
 * hashes must happen in order, that is the copy from round N must be hashed before the copy from round N+1 is hashed
 * </li>
 * <li>
 * copies must be hashed before they are <strong>flushed</strong>
 * </li>
 * <li>
 * copies must be hashed before they are <strong>merged</strong>
 * </li>
 * <li>
 * the copy that is being <strong>merged</strong> into must be hashed before the merge
 * </li>
 * </ul>
 *
 * <hr>
 * <p><strong>Thread Safety</strong></p>
 * <ul>
 * 	<li><strong>merging</strong> and <strong>flushing</strong> are not thread safe with respect to other
 * 		<strong>merge</strong>/<strong>flush</strong> operations in the general case.</li>
 * 	<li><strong>merged</strong> and <strong>flushing</strong> are not thread safe with respect to hashing on the copies
 * 		being <strong>merged</strong> or <strong>flushed</strong></li>
 * 	<li>terminated pipelines will wait for any <strong>merges</strong> or <strong>flushes</strong> to complete
 * 		before shutting down the pipeline. This method can be called concurrently to all other methods. Any concurrent
 * 		calls that race with this one and come after will not execute.</li>
 * </ul>
 */
public class VirtualPipeline {

	private static final String PIPELINE_COMPONENT = "virtual-pipeline";
	private static final String PIPELINE_THREAD_NAME = "lifecycle";

	private static final Logger LOG = LogManager.getLogger(VirtualPipeline.class);

	/**
	 * <p>
	 * Keeps copies of all {@link VirtualRoot}s that are still part of this pipeline.
	 * </p>
	 *
	 * <p>
	 * Copies are removed from this list when released and (flushed or merged).
	 * </p>
	 */
	private final PipelineList<VirtualRoot> copies;

	private final AtomicInteger unreleasedCopies = new AtomicInteger();

	/**
	 * A list of copies that have not yet been hashed. We guarantee that each copy
	 * is hashed in order from oldest to newest (relying on the order of
	 * {@link #registerCopy(VirtualRoot)} to establish that order). Once hashed, the
	 * copy is removed from this deque.
	 */
	private final ConcurrentLinkedDeque<VirtualRoot> unhashedCopies;

	/**
	 * A reference to the most recent copy. This is the copy that {@link VirtualRoot#onShutdown(boolean)}
	 * will be called on.
	 */
	private final AtomicReference<VirtualRoot> mostRecentCopy = new AtomicReference<>();

	/**
	 * True if the pipeline is alive and running. When set to false, any already scheduled work
	 * will still complete. A pipeline is either terminated because the last copy has been released
	 * or because of an explicit call to {@link #terminate()}.
	 */
	private volatile boolean alive;

	/**
	 * A single-threaded executor on which we perform all flush and merge tasks.
	 */
	private final ExecutorService executorService;

	/**
	 * The number of copies waiting to be flushed.
	 */
	private final AtomicInteger flushBacklog = new AtomicInteger(0);

	private final Lock hashLock;

	/**
	 * Create a new pipeline for a family of fast copies on a virtual root.
	 */
	public VirtualPipeline() {
		copies = new PipelineList<>();
		unhashedCopies = new ConcurrentLinkedDeque<>();

		alive = true;
		hashLock = new ReentrantLock();
		executorService = Executors.newSingleThreadExecutor(new ThreadConfiguration()
				.setComponent(PIPELINE_COMPONENT)
				.setThreadName(PIPELINE_THREAD_NAME)
				.buildFactory());
	}

	/**
	 * Make sure that the given copy is properly registered with this pipeline.
	 *
	 * @param copy
	 * 		the copy in question
	 */
	private void validatePipelineRegistration(final VirtualRoot copy) {
		if (!copy.isRegisteredToPipeline(this)) {
			throw new IllegalStateException("copy is not registered with this pipeline");
		}
	}

	/**
	 * Get the number of copies that need to be flushed but have not yet been flushed. If a copy is currently in the
	 * process of being flushed then it is included in this count.
	 *
	 * @return the number of copies awaiting flushing
	 */
	public int getFlushBacklogSize() {
		return flushBacklog.get();
	}

	/**
	 * Slow down the fast copy operation if there are too many copies that need to be flushed.
	 */
	private void applyFlushBackpressure() {
		final int backlogExcess = flushBacklog.get() - VirtualMapSettingsFactory.get().getPreferredFlushQueueSize();

		if (backlogExcess <= 0) {
			return;
		}

		// Sleep time grows quadratically.
		final Duration computedSleepTime =
				VirtualMapSettingsFactory.get().getFlushThrottleStepSize().multipliedBy(
						(long) backlogExcess * backlogExcess);

		final Duration maxSleepTime = VirtualMapSettingsFactory.get().getMaximumFlushThrottlePeriod();
		final Duration sleepTime = CompareTo.min(computedSleepTime, maxSleepTime);

		try {
			MILLISECONDS.sleep(sleepTime.toMillis());
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Register a fast copy of the map.
	 *
	 * @param copy
	 * 		a mutable copy of the map
	 * @throws NullPointerException
	 * 		if the copy is null
	 */
	public void registerCopy(final VirtualRoot copy) {
		Objects.requireNonNull(copy);

		if (copy.isImmutable()) {
			throw new IllegalStateException("Only mutable copies may be registered");
		}

		if (copy.shouldBeFlushed()) {
			flushBacklog.getAndIncrement();
		}

		unreleasedCopies.getAndIncrement();
		copies.add(copy);
		unhashedCopies.add(copy);
		mostRecentCopy.set(copy);
		synchronized (this) {
			if (alive) {
				executorService.submit(this::doWork);
			}
		}

		applyFlushBackpressure();
	}

	/**
	 * Waits for any pending flushes or merges to complete, and then terminates the pipeline. No
	 * further operations will occur.
	 */
	public synchronized void terminate() {
		// If we've already shutdown, we can just return. This method is synchronized, and
		// by the time we return this from this method, we will be terminated. So subsequent
		// calls (even races) will see alive as false by that point.
		if (!alive) {
			return;
		}

		pausePipelineAndExecute("terminate", () -> shutdown(false));
	}

	/**
	 * Release a copy of the map. The pipeline may still perform operations on the copy
	 * at a later time (i.e. merge and flush), and so this method only gives the guarantee
	 * that the resources held by the copy will be eventually released.
	 */
	public synchronized void releaseCopy() {
		if (!alive) {
			// Copy released after the pipeline was manually shut down.
			return;
		}

		final int remainingCopies = unreleasedCopies.decrementAndGet();

		if (remainingCopies < 0) {
			throw new IllegalStateException("copies released too many times");
		} else if (remainingCopies == 0) {
			shutdown(true);
		} else {
			executorService.submit(this::doWork);
		}
	}

	/**
	 * Ensure that a given copy is hashed. Will not re-hash if map is already hashed.
	 * Will cause older copies of the map to be hashed if they have not yet been hashed.
	 *
	 * @param copy
	 * 		a copy of the map that needs to be hashed
	 */
	public void hashCopy(final VirtualRoot copy) {
		validatePipelineRegistration(copy);

		hashLock.lock();
		try {
			if (copy.isHashed()) {
				return;
			}

			final Iterator<VirtualRoot> iterator = unhashedCopies.iterator();

			while (iterator.hasNext()) {
				final VirtualRoot unhashedCopy = iterator.next();
				iterator.remove();
				unhashedCopy.computeHash();
				if (unhashedCopy == copy) {
					break;
				}
			}

			if (!copy.isHashed()) {
				throw new IllegalStateException("failed to hash copy");
			}
		} finally {
			hashLock.unlock();
		}
	}

	/**
	 * Put a copy into a detached state. A detached copy will split off from the regular chain of caches. This allows
	 * for merges and flushes to continue even if this copy is long-lived.
	 *
	 * @param copy
	 * 		the copy to detach
	 * @param withDbCompactionEnabled
	 * 		whether to enable background compaction on the new database
	 * @return a reference to the detached state
	 */
	public <T> T detachCopy(final VirtualRoot copy, boolean withDbCompactionEnabled) {
		return detachCopy(copy, null, null, true, withDbCompactionEnabled);
	}

	/**
	 * Given some {@link VirtualRoot}, wait until any current merge or flush operations complete
	 * and then call the copy's {@link VirtualRoot#detach(String, Path, boolean, boolean)} method on the
	 * same thread this method was called on. Prevents any merging or flushing during the
	 * {@link VirtualRoot#detach(String, Path, boolean, boolean)} callback.
	 *
	 * @param copy
	 * 		The copy. Cannot be null. Should be a member of this pipeline, but technically doesn't need to be.
	 * @param label
	 * 		the label of the database that is written. If null then default location is used
	 * @param targetDirectory
	 * 		the location where detached files are written. If null then default location is used.
	 * @param reopen
	 * 		whether during detach we should also reopen the detached database
	 * @param withDbCompactionEnabled
	 * 		whether to enable background compaction on the new database, if it is reopened
	 * @return a reference to the detached state
	 */
	public <T> T detachCopy(
			final VirtualRoot copy,
			final String label,
			final Path targetDirectory,
			final boolean reopen,
			final boolean withDbCompactionEnabled) {

		validatePipelineRegistration(copy);

		final AtomicReference<T> ret = new AtomicReference<>();
		pausePipelineAndExecute("detach",
				() -> ret.set(copy.detach(label, targetDirectory, reopen, withDbCompactionEnabled)));
		if (alive) {
			executorService.submit(this::doWork);
		}
		return ret.get();
	}

	/**
	 * Wait until the pipeline thread has finished and then return.
	 *
	 * @param timeout
	 * 		the magnitude of the timeout
	 * @param unit
	 * 		the unit for timeout
	 * @return true if the executor service terminated, false if it has not yet terminated when the timeout expired
	 * @throws InterruptedException
	 * 		if calling thread is interrupted
	 */
	public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
		return executorService.awaitTermination(timeout, unit);
	}

	/**
	 * Check if this copy should be flushed.
	 */
	private static boolean shouldFlush(final VirtualRoot copy) {
		return copy.shouldBeFlushed() &&                            // not all copies need to be flushed
				copy.isImmutable() &&                               // only flush immutable copies
				!copy.isFlushed();                                  // don't flush twice
	}

	/**
	 * Flush a copy. Hash it if necessary.
	 *
	 * @param copy
	 * 		the copy to flush
	 */
	private void flush(final VirtualRoot copy) {
		if (copy.isFlushed()) {
			throw new IllegalStateException("copy is already flushed");
		}
		if (!copy.isHashed()) {
			hashCopy(copy);
		}
		copy.flush();
		flushBacklog.getAndDecrement();
	}

	/**
	 * Copies can only be merged into younger copies that are themselves immutable. Check if that is the case.
	 */
	private static boolean shouldMerge(final PipelineListNode<VirtualRoot> mergeCandidate) {
		final VirtualRoot copy = mergeCandidate.getValue();
		final PipelineListNode<VirtualRoot> mergeTarget = mergeCandidate.getNext();

		return copy.shouldBeMerged() &&                             // not all copies need to be merged
				(copy.isReleased() || copy.isDetached()) &&         // copy must be released or detached
				!copy.isMerged() &&                                 // don't merge twice
				mergeTarget != null &&                              // target must exist
				mergeTarget.getValue().isImmutable();               // target must be immutable
	}

	/**
	 * Merge a copy. Hash it if necessary. This method will not be called for any copy
	 * that does not have a valid merge target (i.e. an immutable one).
	 *
	 * @param node
	 * 		the node containing the copy to merge
	 */
	private void merge(final PipelineListNode<VirtualRoot> node) {
		final VirtualRoot copy = node.getValue();

		if (copy.isMerged()) {
			throw new IllegalStateException("copy is already merged");
		}

		if (!copy.isHashed()) {
			hashCopy(copy);
		}

		final VirtualRoot next = node.getNext().getValue();
		if (!next.isHashed()) {
			hashCopy(next);
		}

		copy.merge();
	}

	/**
	 * Check if a copy should be removed from the pipeline. Only remove copies when they are at
	 * the end of their lifecycle.
	 */
	private static boolean shouldBeRemovedFromPipeline(final VirtualRoot copy) {
		return copy.isReleased() && (copy.isFlushed() || copy.isMerged());
	}

	/**
	 * Check if a copy should prevent newer copies from being flushed.
	 */
	private static boolean shouldBlockFlushes(final VirtualRoot copy) {
		return !(copy.isReleased() || copy.isDetached()) ||
				(copy.shouldBeMerged() && !copy.isMerged()) ||
				(copy.shouldBeFlushed() && !copy.isFlushed());
	}

	/**
	 * Hash, flush, and merge all copies currently capable of these operations.
	 */
	private void hashFlushMerge() {
		PipelineListNode<VirtualRoot> next = copies.getFirst();

		// We can only flush a copy if there exists no older copy that is not
		// either released or detached. Once we encounter the first that is neither,
		// all newer copies will be prevented from flushing.
		boolean flushBlocked = false;

		// iterate from the oldest copy to the newest
		while (next != null) {
			final VirtualRoot copy = next.getValue();

			if (shouldFlush(copy)) {
				if (!flushBlocked) {
					flush(copy);
				}
			} else if (shouldMerge(next)) {
				merge(next);
			}

			if (shouldBeRemovedFromPipeline(copy)) {
				copies.remove(next);
			}

			flushBlocked |= shouldBlockFlushes(copy);

			next = next.getNext();
		}
	}

	private void doWork() {
		try {
			hashFlushMerge();
		} catch (final Exception e) {
			LOG.error(EXCEPTION.getMarker(), "exception on virtual pipeline thread", e);
			shutdown(true);
		}
	}

	/**
	 * Shutdown the executor service.
	 *
	 * @param immediately
	 * 		If {@code true}, shuts down the service immediately. This will interrupt any threads currently
	 * 		running. Useful for when there is an error, or for when the virtual map is no longer in use
	 * 		(and therefore any/all pending work will never be used).
	 */
	private synchronized void shutdown(final boolean immediately) {
		alive = false;
		if (!executorService.isShutdown()) {
			if (immediately) {
				executorService.shutdownNow();
				fireOnShutdown(immediately);
			} else {
				executorService.submit(() -> fireOnShutdown(false));
				executorService.shutdown();
			}
		}
	}

	/**
	 * Waits for any pending flushes or merges to complete and then pauses the pipeline while the
	 * given {@link Runnable} executes, and then resumes pipeline operation. Fatal errors happen
	 * if the background thread is interrupted.
	 *
	 * @param label
	 * 		A log/error friendly label to describe the runnable
	 * @param runnable
	 * 		The runnable. Cannot be null.
	 */
	private void pausePipelineAndExecute(final String label, final Runnable runnable) {
		Objects.requireNonNull(runnable);
		final CountDownLatch waitForBackgroundThreadToStart = new CountDownLatch(1);
		final CountDownLatch waitForRunnableToFinish = new CountDownLatch(1);
		executorService.execute(() -> {
			waitForBackgroundThreadToStart.countDown();

			try {
				waitForRunnableToFinish.await();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(
						"Fatal error: interrupted while waiting for runnable " + label + " to finish");
			}
		});

		try {
			waitForBackgroundThreadToStart.await();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Fatal error: failed to start " + label);
		}

		runnable.run();
		waitForRunnableToFinish.countDown();
	}

	/**
	 * Gets whether this pipeline has been terminated.
	 *
	 * @return True if this pipeline has been terminated.
	 */
	public boolean isTerminated() {
		return !alive;
	}

	/**
	 * If there is a most-recent copy, calls shutdown on it.
	 *
	 * @param immediately
	 * 		if true then the shutdown is immediate
	 */
	private void fireOnShutdown(final boolean immediately) {
		final var copy = mostRecentCopy.get();
		if (copy != null) {
			copy.onShutdown(immediately);
		}
	}

	private static String uppercaseBoolean(final boolean value) {
		return value ? "TRUE" : "FALSE";
	}

	/**
	 * This method dumps data about the current state of the pipeline to the log. Useful in emergencies
	 * when debugging pipeline failures.
	 */
	public void logDebugInfo() {

		final StringBuilder sb = new StringBuilder();

		sb.append("Virtual pipeline dump, ");

		sb.append("  size = ").append(copies.getSize()).append("\n");
		sb.append("Copies listed oldest to newest:\n");


		PipelineListNode<VirtualRoot> next = copies.getFirst();
		int index = 0;
		while (next != null) {
			final VirtualRoot copy = next.getValue();

			sb.append(index).append(" should be flushed = ").append(uppercaseBoolean(copy.shouldBeFlushed()));
			sb.append(", ready to be flushed = ").append(uppercaseBoolean(shouldFlush(copy)));
			sb.append(", ready to be merged = ").append(uppercaseBoolean(shouldMerge(next)));
			sb.append(", flushed = ").append(uppercaseBoolean(copy.isFlushed()));
			sb.append(", released = ").append(uppercaseBoolean(copy.isReleased()));
			sb.append(", hashed = ").append(uppercaseBoolean(copy.isHashed()));
			sb.append(", detached = ").append(uppercaseBoolean(copy.isDetached()));
			sb.append("\n");

			index++;
			next = next.getNext();
		}

		sb.append("There is no problem if this has happened during a freeze.\n");
		LOG.info(VIRTUAL_MERKLE_STATS.getMarker(), "{}", sb);
	}

}
