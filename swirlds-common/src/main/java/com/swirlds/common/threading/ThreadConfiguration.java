/*
 * (c) 2016-2022 Swirlds, Inc.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This object is used to configure and build {@link Thread} instances.
 */
public class ThreadConfiguration {

	private static final Logger defaultLogger = LogManager.getLogger();

	/**
	 * The index of the thread created by this configuration object.
	 */
	private final AtomicInteger threadNumber;

	/**
	 * The ID of the node that is running the thread.
	 */
	private long nodeId;

	/**
	 * The name of the component with which this thread is associated.
	 */
	private String component;

	/**
	 * A name for this thread.
	 */
	private String threadName;

	/**
	 * The ID of the other node if this thread is responsible for a task associated with a
	 * particular node.
	 */
	private long otherNodeId;

	/**
	 * The thread group that will contain new threads.
	 */
	private ThreadGroup threadGroup;

	/**
	 * If new threads are daemons or not.
	 */
	private boolean daemon;

	/**
	 * The priority for new threads.
	 */
	private int priority;

	/**
	 * The classloader for new threads.
	 */
	private ClassLoader contextClassLoader;

	/**
	 * The exception handler for new threads.
	 */
	private Thread.UncaughtExceptionHandler exceptionHandler;

	/**
	 * The runnable that will be executed on the thread.
	 */
	private Runnable runnable;

	/**
	 * Build a new thread configuration with default values.
	 */
	public ThreadConfiguration() {
		threadNumber = new AtomicInteger(0);
		nodeId = -1;
		otherNodeId = -1;
		component = "default";
		threadName = "unnamed";
		threadGroup = defaultThreadGroup();
		daemon = true;
		priority = Thread.NORM_PRIORITY;
		contextClassLoader = null;
		exceptionHandler = null;
	}

	/**
	 * Build a new thread.
	 */
	public Thread build() {
		if (runnable == null) {
			throw new NullPointerException("runnable must not be null");
		}

		final Thread thread = new Thread(getThreadGroup(), runnable, buildNextThreadName());

		thread.setDaemon(isDaemon());
		thread.setPriority(getPriority());
		thread.setUncaughtExceptionHandler(getExceptionHandler());
		if (getContextClassLoader() != null) {
			thread.setContextClassLoader(getContextClassLoader());
		}

		return thread;
	}

	/**
	 * Get a {@link ThreadFactory} that contains the configuration specified by this object.
	 */
	public ThreadFactory buildFactory() {
		return new ThreadConfigurationFactory(
				() -> buildNextThreadName(
						getComponent(), getThreadName(), getNodeId(), getOtherNodeId(), threadNumber::getAndIncrement),
				getPriority(),
				getThreadGroup(),
				isDaemon(),
				getContextClassLoader(),
				getExceptionHandler());
	}

	/**
	 * Get the default thread group that will be used if there is no user provided thread group
	 */
	private static ThreadGroup defaultThreadGroup() {
		final SecurityManager securityManager = System.getSecurityManager();
		if (System.getSecurityManager() == null) {
			return Thread.currentThread().getThreadGroup();
		} else {
			return securityManager.getThreadGroup();
		}
	}

	/**
	 * Construct the full thread name.
	 */
	public String buildNextThreadName() {
		return buildNextThreadName(component, threadName, nodeId, otherNodeId, threadNumber::getAndIncrement);
	}

	/**
	 * Construct a thread name.
	 *
	 * <p>
	 * This functionality is in a static function so that we can reuse name creation code
	 * for threads created by the configuration object and for threads created by a factory object.
	 * </p>
	 *
	 * <p>
	 * Format is as follows:
	 * </p>
	 *
	 * <p>
	 * If other node ID is set:
	 * &lt;COMPONENT_NAME: THREAD_NAME NODE_ID #THREAD_NUMBER&gt;
	 * </p>
	 *
	 * <p>
	 * If other node ID is unset:
	 * &lt;COMPONENT_NAME: THREAD_NAME NODE_ID to OTHER_NODE_ID #THREAD_NUMBER&gt;
	 * </p>
	 */
	protected static String buildNextThreadName(
			final String component,
			final String threadName,
			final long nodeId,
			final long otherNodeId,
			final IntSupplier threadNumber) {

		final StringBuilder sb = new StringBuilder();

		sb.append("<");
		sb.append(component).append(": ").append(threadName).append(" ");
		if (nodeId == -1) {
			sb.append("?");
		} else {
			sb.append(nodeId);
		}
		sb.append(" ");
		if (otherNodeId != -1) {
			sb.append("to ").append(otherNodeId).append(" ");
		}
		sb.append("#").append(threadNumber.getAsInt());
		sb.append(">");

		return sb.toString();
	}

	/**
	 * Builds a default uncaught exception handler.
	 */
	private static Thread.UncaughtExceptionHandler buildDefaultExceptionHandler() {
		return (Thread t, Throwable e) ->
				defaultLogger.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e);
	}

	/**
	 * Get the the thread group that new threads will be created in.
	 */
	public ThreadGroup getThreadGroup() {
		return threadGroup;
	}

	/**
	 * Set the the thread group that new threads will be created in.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setThreadGroup(final ThreadGroup threadGroup) {
		this.threadGroup = threadGroup;
		return this;
	}

	/**
	 * Get the daemon behavior of new threads.
	 */
	public boolean isDaemon() {
		return daemon;
	}

	/**
	 * Set the daemon behavior of new threads.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setDaemon(final boolean daemon) {
		this.daemon = daemon;
		return this;
	}

	/**
	 * Get the priority of new threads.
	 */
	public int getPriority() {
		return priority;
	}

	/**
	 * Set the priority of new threads.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setPriority(final int priority) {
		this.priority = priority;
		return this;
	}

	/**
	 * Get the class loader for new threads.
	 */
	public ClassLoader getContextClassLoader() {
		return contextClassLoader;
	}

	/**
	 * Set the class loader for new threads.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setContextClassLoader(final ClassLoader contextClassLoader) {
		this.contextClassLoader = contextClassLoader;
		return this;
	}

	/**
	 * Get the exception handler for new threads.
	 */
	public Thread.UncaughtExceptionHandler getExceptionHandler() {
		return exceptionHandler == null ? buildDefaultExceptionHandler() : exceptionHandler;
	}

	/**
	 * Set the exception handler for new threads.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setExceptionHandler(final Thread.UncaughtExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
		return this;
	}

	/**
	 * Get the node ID that will run threads created by this object.
	 */
	public long getNodeId() {
		return nodeId;
	}

	/**
	 * Set the node ID. Node IDs less than 0 are interpreted as "no node ID".
	 *
	 * @return this object
	 */
	public ThreadConfiguration setNodeId(final long nodeId) {
		this.nodeId = nodeId;
		return this;
	}

	/**
	 * Get the name of the component that new threads will be associated with.
	 */
	public String getComponent() {
		return component;
	}

	/**
	 * Set the name of the component that new threads will be associated with.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setComponent(final String component) {
		this.component = component;
		return this;
	}

	/**
	 * Get the name for created threads.
	 */
	public String getThreadName() {
		return threadName;
	}

	/**
	 * Set the name for created threads.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setThreadName(final String threadName) {
		this.threadName = threadName;
		return this;
	}

	/**
	 * Set the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 */
	public long getOtherNodeId() {
		return otherNodeId;
	}

	/**
	 * Get the node ID of the other node (if created threads will be dealing with a task related to a specific node).
	 *
	 * @return this object
	 */
	public ThreadConfiguration setOtherNodeId(final long otherNodeId) {
		this.otherNodeId = otherNodeId;
		return this;
	}

	/**
	 * Get the runnable that will be executed on the thread.
	 */
	public Runnable getRunnable() {
		return runnable;
	}

	/**
	 * Set the runnable that will be executed on the thread.
	 *
	 * @return this object
	 */
	public ThreadConfiguration setRunnable(final Runnable runnable) {
		this.runnable = runnable;
		return this;
	}
}
