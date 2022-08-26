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

package com.swirlds.common.test.notification;

import com.swirlds.common.notification.DispatchException;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.notification.internal.AsyncNotificationEngine;
import com.swirlds.common.threading.futures.ConcurrentFuturePool;
import com.swirlds.common.threading.futures.FuturePool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Integer Notification Tests")
public class IntegerNotificationTests {

	private static final boolean ENABLE_DIAG_PRINTOUT = false;

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Sync: Unordered Summation")
	public void syncUnorderedSummation(int iterations) {

		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicLong lastKnownId = new AtomicLong(0);

		assertNotNull(engine);

		engine.register(SyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		for (int i = 0; i < iterations; i++) {
			engine.dispatch(SyncUnorderedIntegerListener.class, new IntegerNotification(1));
		}

		assertEquals(iterations, lastKnownId.get());
		assertEquals(iterations, sum.get());

		engine.shutdown();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Sync: Unordered Dual Ops")
	public void syncUnorderedDualOps(int iterations) {

		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicInteger subtract = new AtomicInteger(iterations * 4);
		final AtomicLong lastKnownId = new AtomicLong(0);


		assertNotNull(engine);

		engine.register(SyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		engine.register(SyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertEquals(lastKnownId.get(), n.getSequence());

			subtract.addAndGet(-1 * n.getValue());
			lastKnownId.set(n.getSequence());
		});

		for (int i = 0; i < iterations; i++) {
			engine.dispatch(SyncUnorderedIntegerListener.class, new IntegerNotification(4));
		}

		assertEquals(iterations * 4, sum.get());
		assertEquals(0, subtract.get());

		engine.shutdown();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Async: Unordered Summation")
	public void asyncUnorderedSummation(int iterations) {

		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicLong lastKnownId = new AtomicLong(0);

		assertNotNull(engine);

		engine.register(AsyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		final FuturePool<NotificationResult<IntegerNotification>> futures = new FuturePool<>();

		for (int i = 0; i < iterations; i++) {
			futures.add(engine.dispatch(AsyncUnorderedIntegerListener.class, new IntegerNotification(1)));
		}

		futures.waitForCompletion();
		engine.shutdown();

		assertEquals(iterations, lastKnownId.get());
		assertEquals(iterations, sum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Async: Unordered Dual Ops")
	public void asyncUnorderedDualOps(int iterations) {

		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicInteger subtract = new AtomicInteger(iterations * 4);
		final AtomicLong lastKnownId = new AtomicLong(0);


		assertNotNull(engine);

		engine.register(AsyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		engine.register(AsyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertEquals(lastKnownId.get(), n.getSequence());

			subtract.addAndGet(-1 * n.getValue());
			lastKnownId.set(n.getSequence());
		});

		final FuturePool<NotificationResult<IntegerNotification>> futures = new FuturePool<>();

		for (int i = 0; i < iterations; i++) {
			futures.add(engine.dispatch(AsyncUnorderedIntegerListener.class, new IntegerNotification(4)));
		}

		futures.waitForCompletion();
		engine.shutdown();

		assertEquals(iterations * 4, sum.get());
		assertEquals(0, subtract.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Sync: Unordered MT Summation")
	public void syncUnorderedThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicLong lastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(SyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertNotEquals(lastKnownId.get(), n.getSequence());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			final Future future = executorService.submit(() -> {
				try {
					engine.dispatch(SyncUnorderedIntegerListener.class, new IntegerNotification(1));
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 5 second limit");

		engine.shutdown();

		assertEquals(iterations, sum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Sync: Ordered MT Summation")
	public void syncOrderedThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicLong lastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(SyncOrderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			final Future future = executorService.submit(() -> {
				try {
					engine.dispatch(SyncOrderedIntegerListener.class, new IntegerNotification(1));
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 5 second limit");

		engine.shutdown();

		assertEquals(iterations, lastKnownId.get());
		assertEquals(iterations, sum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Async: Unordered MT Summation")
	public void asyncUnorderedThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicLong lastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(AsyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertNotEquals(lastKnownId.get(), n.getSequence());
//			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<NotificationResult<IntegerNotification>> futures = new ConcurrentFuturePool<>();
		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			Future future = executorService.submit(() -> {
				try {
					futures.add(engine.dispatch(AsyncUnorderedIntegerListener.class, new IntegerNotification(1)));
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();
		futures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 5 second limit");

		engine.shutdown();

		assertEquals(iterations, sum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Async: Ordered MT Summation")
	public void asyncOrderedThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger sum = new AtomicInteger(0);
		final AtomicLong lastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(AsyncOrderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Notification #%06d", n.getSequence()));
			}

			assertTrue(n.getSequence() > lastKnownId.get());

			sum.addAndGet(n.getValue());
			lastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<NotificationResult<IntegerNotification>> futures = new ConcurrentFuturePool<>();
		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			final Future future = executorService.submit(() -> {
				try {
					futures.add(engine.dispatch(AsyncOrderedIntegerListener.class, new IntegerNotification(1)));
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();
		futures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 5 second limit");

		engine.shutdown();

		assertEquals(iterations, lastKnownId.get());
		assertEquals(iterations, sum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Mixed: Unordered MT Summation")
	public void mixedUnorderedThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger syncSum = new AtomicInteger(0);
		final AtomicInteger asyncSum = new AtomicInteger(0);
		final AtomicLong syncLastKnownId = new AtomicLong(0);
		final AtomicLong asyncLastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(SyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Sync Notification #%06d", n.getSequence()));
			}

			assertNotEquals(syncLastKnownId.get(), n.getSequence());
//			assertTrue(n.getSequence() > syncLastKnownId.get());

			syncSum.addAndGet(n.getValue());
			syncLastKnownId.set(n.getSequence());
		});

		engine.register(AsyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing ASYNC Notification #%06d", n.getSequence()));
			}

			assertNotEquals(asyncLastKnownId.get(), n.getSequence());
//			assertTrue(n.getSequence() > asyncLastKnownId.get());


			asyncSum.addAndGet(n.getValue());
			asyncLastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<NotificationResult<IntegerNotification>> futures = new ConcurrentFuturePool<>();
		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			final int iter = i;
			final Future future = executorService.submit(() -> {
				try {
					if (isEven(iter)) {
						futures.add(engine.dispatch(SyncUnorderedIntegerListener.class, new IntegerNotification(1)));
					} else {
						futures.add(engine.dispatch(AsyncUnorderedIntegerListener.class, new IntegerNotification(1)));
					}
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();
		futures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 5 second limit");

		engine.shutdown();

		final int expectedSyncIterations = (iterations / 2) + ((isOdd(iterations)) ? 1 : 0);
		final int expectedAsyncIterations = (iterations / 2);

		assertEquals(expectedSyncIterations, syncSum.get());
		assertEquals(expectedAsyncIterations, asyncSum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("Mixed: Ordered MT Summation")
	public void mixedOrderedThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger syncSum = new AtomicInteger(0);
		final AtomicInteger asyncSum = new AtomicInteger(0);
		final AtomicLong syncLastKnownId = new AtomicLong(0);
		final AtomicLong asyncLastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(SyncOrderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Sync Notification #%06d", n.getSequence()));
			}

//			assertNotEquals(syncLastKnownId.get(), n.getSequence());
			assertTrue(n.getSequence() > syncLastKnownId.get());

			syncSum.addAndGet(n.getValue());
			syncLastKnownId.set(n.getSequence());
		});

		engine.register(AsyncOrderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing ASYNC Notification #%06d", n.getSequence()));
			}

//			assertNotEquals(asyncLastKnownId.get(), n.getSequence());
			assertTrue(n.getSequence() > asyncLastKnownId.get());


			asyncSum.addAndGet(n.getValue());
			asyncLastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<NotificationResult<IntegerNotification>> futures = new ConcurrentFuturePool<>();
		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			final int iter = i;
			final Future future = executorService.submit(() -> {
				try {
					if (isEven(iter)) {
						futures.add(engine.dispatch(SyncOrderedIntegerListener.class, new IntegerNotification(1)));
					} else {
						futures.add(engine.dispatch(AsyncOrderedIntegerListener.class, new IntegerNotification(1)));
					}
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();
		futures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 5 second limit");

		engine.shutdown();

		final int expectedSyncIterations = (iterations / 2) + ((isOdd(iterations)) ? 1 : 0);
		final int expectedAsyncIterations = (iterations / 2);

		assertTrue(
				((syncLastKnownId.get() == iterations && asyncLastKnownId.get() <= (iterations - 1))
						|| (syncLastKnownId.get() <= (iterations - 1) && asyncLastKnownId.get() == iterations)),
				String.format("Last Known Sequences - Out of Range [ sync = %d, async = %d ]", syncLastKnownId.get(),
						asyncLastKnownId.get())
		);

		assertEquals(expectedSyncIterations, syncSum.get());
		assertEquals(expectedAsyncIterations, asyncSum.get());
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 5, 21, 57, 1_000, 10_000, 100_000 })
	@DisplayName("SUAO: MT Summation")
	public void suaoThreadedSummation(int iterations) throws InterruptedException {
		final NotificationEngine engine = new AsyncNotificationEngine();

		final AtomicInteger syncSum = new AtomicInteger(0);
		final AtomicInteger asyncSum = new AtomicInteger(0);
		final AtomicLong syncLastKnownId = new AtomicLong(0);
		final AtomicLong asyncLastKnownId = new AtomicLong(0);
		final ExecutorService executorService =
				Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		assertNotNull(engine);

		engine.register(SyncUnorderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing Sync Notification #%06d", n.getSequence()));
			}

			assertNotEquals(syncLastKnownId.get(), n.getSequence());
//			assertTrue(n.getSequence() > syncLastKnownId.get());

			syncSum.addAndGet(n.getValue());

			if (n.getSequence() > syncLastKnownId.get()) {
				syncLastKnownId.set(n.getSequence());
			}
		});

		engine.register(AsyncOrderedIntegerListener.class, (n) -> {
			if (ENABLE_DIAG_PRINTOUT) {
				System.out.println(String.format("Processing ASYNC Notification #%06d", n.getSequence()));
			}

//			assertNotEquals(asyncLastKnownId.get(), n.getSequence());
			assertTrue(n.getSequence() > asyncLastKnownId.get());


			asyncSum.addAndGet(n.getValue());
			asyncLastKnownId.set(n.getSequence());
		});

		final ConcurrentFuturePool<NotificationResult<IntegerNotification>> futures = new ConcurrentFuturePool<>();
		final ConcurrentFuturePool<?> callableFutures = new ConcurrentFuturePool<>();

		for (int i = 0; i < iterations; i++) {
			final int iter = i;
			final Future future = executorService.submit(() -> {
				try {
					if (isEven(iter)) {
						futures.add(engine.dispatch(SyncUnorderedIntegerListener.class, new IntegerNotification(1)));
					} else {
						futures.add(engine.dispatch(AsyncOrderedIntegerListener.class, new IntegerNotification(1)));
					}
				} catch (DispatchException ex) {
					ex.printStackTrace();
				}
			});

			callableFutures.add(future);
		}

		callableFutures.waitForCompletion();
		futures.waitForCompletion();

		executorService.shutdown();
		assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS),
				"ExecutorService failed to stop within the 10 second limit");

		engine.shutdown();

		final int expectedSyncIterations = (iterations / 2) + ((isOdd(iterations)) ? 1 : 0);
		final int expectedAsyncIterations = (iterations / 2);

		assertTrue(
				((syncLastKnownId.get() == iterations && asyncLastKnownId.get() <= (iterations - 1))
						|| (syncLastKnownId.get() <= (iterations - 1) && asyncLastKnownId.get() == iterations)),
				String.format("Last Known Sequences - Out of Range [ sync = %d, async = %d ]", syncLastKnownId.get(),
						asyncLastKnownId.get())
		);

		assertEquals(expectedSyncIterations, syncSum.get(), "Unexpected Sync Summation");
		assertEquals(expectedAsyncIterations, asyncSum.get(), "Unexpected ASYNC Summation");
	}

	private static boolean isEven(final int num) {
		return num % 2 == 0;
	}

	private static boolean isOdd(final int num) {
		return num % 2 != 0;
	}
}
