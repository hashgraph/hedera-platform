/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.threading;

import com.swirlds.common.NodeId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * Utility factory providing standardized methods for creating new {@link Thread} instances.
 */
public final class StandardThreadFactory {

	private static final String DEFAULT_POOL_NAME = "default_thread_pool";

	private static final Map<String, ThreadFactory> factoryCache;


	static {
		factoryCache = new HashMap<>();
	}

	/**
	 * Internal private constructor to prevent instantiation of this factory class.
	 */
	private StandardThreadFactory() {

	}

	/**
	 * Create a new thread for the given runnable using the specified pool name. A {@link ThreadFactory} is allocated
	 * for each unique pool name. The thread is named like "{@literal < }xx worker N{@literal > }" where "xx" is the
	 * pool name and "N" is the per pool unique index of the thread. Indexes will not be reused if the thread terminates
	 * and will increment for each new thread created.
	 *
	 * NOTE: This method uses the default pool name and should only be used for creating extremely short-lived threads.
	 *
	 * @param runnable
	 * 		the thing the thread should run
	 * @return the new thread
	 */
	public static Thread newThreadFromPool(final Runnable runnable) {
		return newThreadFromPool(DEFAULT_POOL_NAME, runnable);
	}

	/**
	 * Create a new thread for the given runnable using the specified pool name. A {@link ThreadFactory} is allocated
	 * for each unique pool name. The thread is named like "{@literal < }xx worker N{@literal > }" where "xx" is the
	 * pool name and "N" is the per pool unique index of the thread. Indexes will not be reused if the thread terminates
	 * and will increment for each new thread created.
	 *
	 * NOTE: This method creates daemon threads with {@link Thread#NORM_PRIORITY} priority.
	 *
	 * @param poolName
	 * 		a unique name for this thread pool
	 * @param runnable
	 * 		the thing the thread should run
	 * @return the new thread
	 */
	public static Thread newThreadFromPool(final String poolName, final Runnable runnable) {
		return newThreadFromPool(poolName, runnable, true, Thread.NORM_PRIORITY);
	}

	/**
	 * Create a new thread for the given runnable using the specified pool name. A {@link ThreadFactory} is allocated
	 * for each unique pool name. The thread is named like "{@literal < }xx worker N{@literal > }" where "xx" is the
	 * pool name and "N" is the per pool unique index of the thread. Indexes will not be reused if the thread terminates
	 * and will increment for each new thread created.
	 *
	 * NOTE: This method creates threads with {@link Thread#NORM_PRIORITY} priority.
	 *
	 * @param poolName
	 * 		a unique name for this thread pool
	 * @param runnable
	 * 		the thing the thread should run
	 * @param daemon
	 * 		if true then this thread should be spawned as a daemon thread
	 * @return the new thread
	 */
	public static Thread newThreadFromPool(final String poolName, final Runnable runnable, final boolean daemon) {
		return newThreadFromPool(poolName, runnable, daemon, Thread.NORM_PRIORITY);
	}

	/**
	 * Create a new thread for the given runnable using the specified pool name. The thread is named like "{@literal <
	 * }xx worker N{@literal > }" where "xx" is the pool name and "N" is the per pool unique index of the thread.
	 * Indexes will not be reused if the thread terminates and will increment for each new thread created.
	 *
	 * @param poolName
	 * 		a unique name for this thread pool
	 * @param runnable
	 * 		the thing the thread should run
	 * @param daemon
	 * 		if true then this thread should be spawned as a daemon thread
	 * @param priority
	 * 		the priority for this thread in the JVM thread scheduler
	 * @return the new thread
	 */
	public static Thread newThreadFromPool(final String poolName, final Runnable runnable, final boolean daemon,
			final int priority) {
		return getOrCreateFactory(poolName, daemon, priority).newThread(runnable);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx{@literal > }" where
	 * "xx" is the name.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @return the new thread
	 */
	public static Thread newThread(final String name, final Runnable runnable) {
		return newThread(name, runnable, true);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx{@literal > }" where
	 * "xx" is the name.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param daemon
	 * 		if true then this thread should be spawned as a daemon thread
	 * @return the new thread
	 */
	public static Thread newThread(final String name, final Runnable runnable, final boolean daemon) {
		return newThread(name, runnable, daemon, Thread.NORM_PRIORITY);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx{@literal > }" where
	 * "xx" is the name.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param daemon
	 * 		if true then this thread should be spawned as a daemon thread
	 * @param priority
	 * 		the priority for this thread in the JVM thread scheduler
	 * @return the new thread
	 */
	public static Thread newThread(final String name, final Runnable runnable, final boolean daemon,
			final int priority) {
		final Thread thread = new Thread(runnable, String.format("<%16s >", name));
		thread.setDaemon(daemon);
		thread.setPriority(priority);

		return thread;
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx 2 4{@literal > }" where
	 * "xx" is the name, 2 is the platform ID (0 is the first one the Browser creates, 1 is next, etc) of the platform
	 * running this thread, and 4 is the platform ID of the member that this thread is supposed to talk with. This form
	 * of newThreadFromPool should only be used to instantiate a thread that is dedicated to talking with a specific
	 * member. Otherwise, the last parameter should not be used.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param selfId
	 * 		the member id number of the platform creating this thread
	 * @return the new thread
	 * @see #newThread(String, Runnable, NodeId, NodeId, int, boolean)
	 */
	public static Thread newThread(final String name, final Runnable runnable, final NodeId selfId) {
		return newThread(name, runnable, selfId, null);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx 2 4{@literal > }" where
	 * "xx" is the name, 2 is the platform ID (0 is the first one the Browser creates, 1 is next, etc) of the platform
	 * running this thread, and 4 is the platform ID of the member that this thread is supposed to talk with. This form
	 * of newThreadFromPool should only be used to instantiate a thread that is dedicated to talking with a specific
	 * member. Otherwise, the last parameter should not be used.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param selfId
	 * 		the member id number of the platform creating this thread
	 * @param otherId
	 * 		the member id number of the member this thread talks to (or null if none)
	 * @return the new thread
	 * @see #newThread(String, Runnable, NodeId, NodeId, int, boolean)
	 */
	public static Thread newThread(final String name, final Runnable runnable, final NodeId selfId,
			final NodeId otherId) {
		return newThread(name, runnable, selfId, otherId, Thread.NORM_PRIORITY, false);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx 2 4{@literal > }" where
	 * "xx" is the name, 2 is the platform ID (0 is the first one the Browser creates, 1 is next, etc) of the platform
	 * running this thread, and 4 is the platform ID of the member that this thread is supposed to talk with. This form
	 * of newThreadFromPool should only be used to instantiate a thread that is dedicated to talking with a specific
	 * member. Otherwise, the last parameter should not be used.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param selfId
	 * 		the member id number of the platform creating this thread
	 * @param priority
	 * 		the priority for this thread in the JVM thread scheduler
	 * @return the new thread
	 * @see #newThread(String, Runnable, NodeId, NodeId, int, boolean)
	 */
	public static Thread newThread(final String name, final Runnable runnable, final NodeId selfId,
			final int priority) {
		return newThread(name, runnable, selfId, null, priority, false);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx 2 4{@literal > }" where
	 * "xx" is the name, 2 is the platform ID (0 is the first one the Browser creates, 1 is next, etc) of the platform
	 * running this thread, and 4 is the platform ID of the member that this thread is supposed to talk with. This form
	 * of newThreadFromPool should only be used to instantiate a thread that is dedicated to talking with a specific
	 * member. Otherwise, the last parameter should not be used.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param selfId
	 * 		the member id number of the platform creating this thread
	 * @param otherId
	 * 		the member id number of the member this thread talks to (or null if none)
	 * @param priority
	 * 		the priority for this thread in the JVM thread scheduler
	 * @return the new thread
	 * @see #newThread(String, Runnable, NodeId, NodeId, int, boolean)
	 */
	public static Thread newThread(final String name, final Runnable runnable, final NodeId selfId,
			final NodeId otherId, final int priority) {
		return newThread(name, runnable, selfId, otherId, priority, false);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx 2 4{@literal > }" where
	 * "xx" is the name, 2 is the platform ID (0 is the first one the Browser creates, 1 is next, etc) of the platform
	 * running this thread, and 4 is the platform ID of the member that this thread is supposed to talk with.
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param selfId
	 * 		the member id number of the platform creating this thread
	 * @param priority
	 * 		the priority for this thread in the JVM thread scheduler
	 * @param daemon
	 * 		if true then this thread should be spawned as a daemon thread
	 * @return the new thread
	 * @see #newThread(String, Runnable, NodeId, NodeId, int, boolean)
	 */
	public static Thread newThread(final String name, final Runnable runnable, final NodeId selfId,
			final int priority, final boolean daemon) {
		return newThread(name, runnable, selfId, null, priority, daemon);
	}

	/**
	 * Create a new thread for the given runnable. The thread is named like "{@literal < }xx 2 4{@literal > }" where
	 * "xx" is the name, 2 is the platform ID (0 is the first one the Browser creates, 1 is next, etc) of the platform
	 * running this thread, and 4 is the platform ID of the member that this thread is supposed to talk with.
	 *
	 * <pre>
	 * platformRun  = Platform
	 * appMain      = appMain
	 * heartbeat    = SyncHeartbeat
	 * syncServer   = SyncServer
	 * syncListener = SyncListener
	 * syncCaller   = syncCaller
	 * threadCons   = EventFlow.doCons
	 * threadCurr   = EventFlow.doCurr
	 * threadWork   = EventFlow.doWork
	 * </pre>
	 *
	 * @param name
	 * 		a name describing the kind of thread, maximum of 16 characters
	 * @param runnable
	 * 		the thing the thread should run
	 * @param selfId
	 * 		the member id number of the platform creating this thread
	 * @param otherId
	 * 		the member id number of the member this thread talks to (or null if none)
	 * @param priority
	 * 		the priority for this thread in the JVM thread scheduler
	 * @param daemon
	 * 		if true then this thread should be spawned as a daemon thread
	 * @return the new thread
	 */
	public static Thread newThread(final String name, final Runnable runnable, final NodeId selfId,
			final NodeId otherId, final int priority, final boolean daemon) {
		Thread thread;
		if (otherId == null) {
			thread = new Thread(runnable,
					String.format("<%16s%3s   >", name, selfId));
		} else {
			thread = new Thread(runnable,
					String.format("<%16s%3s%3s>", name, selfId, otherId));
		}
		thread.setDaemon(daemon);
		thread.setPriority(priority);
		return thread;
	}

	private synchronized static ThreadFactory getOrCreateFactory(final String name, final boolean daemon,
			final int priority) {
		final String prefixPattern = "< %s worker ";

		return factoryCache.computeIfAbsent(name, (k) ->
				new StandardThreadFactoryBuilder()
						.poolNamePrefixPattern(prefixPattern)
						.poolName(k)
						.daemon(daemon)
						.priority(priority)
						.build()
		);
	}
}
