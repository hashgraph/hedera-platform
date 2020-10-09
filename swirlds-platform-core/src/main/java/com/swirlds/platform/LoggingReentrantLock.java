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
package com.swirlds.platform;

import com.swirlds.common.NodeId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.LOCKS;

/**
 * This class is similar to ReentrantLock, except that it can also do logging whenever a lock is held for
 * too long before being released, or whenever a thread has to wait too long to acquire a lock. The four
 * standard methods to obtain/release a lock are supported in the original form, plus in a form where they
 * are overloaded to take an optional comment that documents where in the code or under what circumstance it
 * is obtaining/releasing the lock.
 */
public class LoggingReentrantLock extends ReentrantLock {
	/** needed to serialize */
	private static final long serialVersionUID = 1L;
	/**
	 * Should any logging be done? It's false if Settings.lockLogBlockTimeout &lt;= 0 at the time this is
	 * instantiated. It's false if Settings.lockLogAliceOnly is true but the current platform doesn't have a
	 * selfId of 0. It's false if the user uses a ReentrantLock constructor that doesn't take the platform
	 * or name parameters. Otherwise, it's true.
	 */
	private final boolean enableLogging;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** the name of this lock, written to the logs, and given to the lock itself */
	private final String name;
	/** the member ID of the member who created this lock (only used for debugging) */
	private NodeId selfId;
	/**
	 * write to the log if a lock is released timeout or more milliseconds after being obtained. So 0 means
	 * log everything. Use -1 to log nothing.
	 */
	private final int timeout;
	/** The time at which the lock currently being held was obtained (null if no lock) */
	private volatile long startHold = System.nanoTime();
	/** name of the thread holding this lock, used when logging another thread stuck waiting too long */
	private volatile String holdingThread = "-ERROR-";
	/** the comment for this lock to be written during logging */
	private String startComment = "";
	/** all LoggingReentrantLock objects instantiated so far */
	private static final ConcurrentHashMap<Integer, LoggingReentrantLock> idToLock = new ConcurrentHashMap<>();
	/** the last note set for this lock */
	private volatile String note = "lockCreated";
	/** the time of the last note set for this lock */
	private volatile long noteTime = System.nanoTime();

	public static LoggingReentrantLock newLock(NodeId selfId, boolean fair,
			String name, int timeout) {
		LoggingReentrantLock lock = new LoggingReentrantLock(selfId, fair, name,
				timeout);
		LockInfo info = getLockInfo(lock);
		if (info != null) {
			idToLock.put(info.getIdentityHashCode(), lock);
		}
		return lock;
	}

	public static LoggingReentrantLock newLock(NodeId selfId, boolean fair,
			String name) {
		return newLock(selfId, fair, name, Settings.lockLogTimeout);
	}

	/** This constructor is equivalent to the constructor of ReentrantLock(). It disables logging. */
	public LoggingReentrantLock() {
		super();
		name = "noName";
		timeout = -1;
		enableLogging = false;
	}

	/**
	 * This constructor is equivalent to the constructor of ReentrantLock(boolean fair). It disables
	 * logging.
	 */
	public LoggingReentrantLock(boolean fair) {
		super(fair);
		name = "noName";
		timeout = -1;
		enableLogging = false;
	}

	/**
	 * This constructor should be used instead of LoggingReentrantLock(boolean fair), to prepare for
	 * logging. But users should call the factory method newLock, instead.
	 *
	 * @param selfId
	 * 		the ID of the member whose Platform is using this lock
	 * @param fair
	 * 		if true, schedules waiting threads in the order they called lock
	 * @param name
	 * 		the name of the lock, to be written during logging
	 * @param timeout
	 * 		write to the log if a lock is released timeout or more milliseconds after being obtained
	 * 		(-1 to never write). So 0 will log everything.
	 */
	private LoggingReentrantLock(NodeId selfId, boolean fair, String name,
			int timeout) {
		super(fair);
		this.name = name;
		this.timeout = timeout;
		this.selfId = selfId;
		enableLogging = (Settings.lockLogBlockTimeout >= 0)
				&& (!Settings.lockLogMemberZeroOnly || selfId.equalsMain(0));
		startComment = "(not logged)"; // this only becomes part of the name if enableLogging==false
	}

	/**
	 * record a note for this lock. when a log is written, it will include the last note, and when it was
	 * set
	 *
	 * @param note
	 * 		the note to remember (which replaces the previous note, if any)
	 */
	public void setNote(String note) {
		if (!enableLogging) { // if not logging, then ignore notes
			return;
		}
		this.note = note;
		noteTime = noteTime = System.nanoTime();
	}

	/**
	 * Return the LockInfo for a LoggingReentrantLock. There must not be any other threads blocked on this
	 * lock when this method is called. This is slow, so it is only called once, when the lock is first
	 * created.
	 *
	 * @param lock
	 * 		the lock to get info for
	 * @return the LockInfo for that lock, or null if it was unable to find it
	 */
	private static LockInfo getLockInfo(LoggingReentrantLock lock) {
		// It is a serious design flaw in the Java libraries that there is absolutely no way to tell whether
		// a given Lock and a given LockInfo refer to the same thing or not. Even
		// System.identityHashCode(lock) and lockInfo.getIdentityHashCode() return different numbers. So the
		// only solution is to actually spawn a thread and make it block, and look at its identity hash
		// code, and store the result in a hashMap. Behold the world's biggest kludge:

		final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		LockInfo lockInfo = null;
		for (int i = 1; i <= 10; i++) {// try at most 10 times, using longer delays each time
			lock.lock("getLockName 1");
			try {
				Thread blocked = new Thread(() -> {
					lock.lock("getLockName 3");
					lock.unlock("getLockName 4");
				});
				blocked.start();
				Thread.sleep(i * 10); // need to wait for the thread to start and then block
				ThreadInfo threadInfo = threadMXBean
						.getThreadInfo(blocked.getId());
				lockInfo = threadInfo.getLockInfo();
				if (lockInfo != null) {
					break;
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // Preserve the interrupt state
			} finally {
				lock.unlock("getLockName 2");
			}
		}
		return lockInfo;
	}

	/**
	 * Get the name given to this LoggingReentrantLock when it was first created
	 *
	 * @return the name
	 */
	String getName() {
		return name;
	}

	/**
	 * Return the name of a lock. This is usually the same as Lock.getClassName(). But if this is a
	 * LoggingReentrantLock, then it returns a name including both the name and latest startComment.
	 */
	static String getName(LockInfo lockInfo) {
		if (lockInfo == null) {
			return "null";
		}
		LoggingReentrantLock lock = idToLock
				.get(lockInfo.getIdentityHashCode());
		if (lock == null) {
			return lockInfo.getClassName();
		}
		String name;
		String timing = !lock.enableLogging ? ""
				: " (" + ((System.nanoTime() - lock.startHold) / 1_000_000)
				+ " ms ago)";
		if (lock.isLocked()) {
			name = "lock id-" + lock.selfId + " " + lock.name + " acquired at "
					+ lock.startComment + timing;
		} else {
			name = "lock id-" + lock.selfId + " " + lock.name + " is unlocked ";
		}
		if (lock.enableLogging) {
			name += String.format(" (%d ms ago note: %s)",
					(System.nanoTime() - lock.noteTime) / 1_000_000, lock.note);
		}
		return name;
	}

	////////////////////////////////////////////////////////////////////////////////////
	// methods from ReentrantLock

	/** equivalent to lock("") */
	public void lock() {
		lock("");
	}

	/** equivalent to lockInterruptibly("") */
	public void lockInterruptibly() throws InterruptedException {
		lockInterruptibly("");
	}

	/** equivalent to tryLock("") */
	public boolean tryLock() {
		return tryLock("");
	}

	/** equivalent to tryLock(timeout, unit, "") */
	public boolean tryLock(long timeout, TimeUnit unit)
			throws InterruptedException {
		return tryLock(timeout, unit, "");
	}

	/** equivalent to unlock("") */
	public void unlock() {
		unlock("");
	}

	//////////////////////////////////////////////////////////
	// methods like ReentrantLock methods, but with a comment parameter

	/**
	 * Same as ReentrantLock.lock() but with a comment to include when logging. This will block until the
	 * lock is available. If lockBlockTimeout &gt;= 0, then this will log if it has to wait more than
	 * lockBlockTimeout milliseconds to obtain the lock.
	 */
	public void lock(String comment) {
		if (!enableLogging) {
			super.lock();
			return;
		}
		// log if it blocks for at least Settings.lockBlockTimeout milliseconds
		int count = 0; // number of times it has timed out
		while (true) {
			try {
				if (super.tryLock((long) Settings.lockLogBlockTimeout,
						TimeUnit.MILLISECONDS)) {
					break;
				}
				count++;
				// String threadName = Thread.currentThread().getName();
				// String lockName = String.format("%12s", "'" + name + "'");
				// int duration = count * Settings.lockLogBlockTimeout;
				// String err = String.format(//
				// "%s still waiting on Lock %s at %s after %d ms while lock is held by %s from %s", //
				// threadName, lockName, comment, duration, holdingThread,
				// startComment);
				// String threadName = Thread.currentThread().getName();
				String lockName = String.format("%12s", "'" + name + "'");
				int duration = count * Settings.lockLogBlockTimeout;
				String err = String.format(//
						"Lock %35s: %d ms from %s blocked waiting for %s from %s", //
						lockName, duration, comment, holdingThread,
						startComment);
				log.info(LOCKS.getMarker(), err);
				if (Settings.lockLogThreadDump) {
					ThreadDumpGenerator.generateThreadDumpFile(err);
				}
			} catch (InterruptedException e) {
				log.info(LOCKS.getMarker(),
						"Lock {}: interrupted while waiting from '{}'", //
						String.format("%12s", "'" + name + "'"), comment);
			}
		}
		// don't reset timer if I get a lock I already have
		if (timeout >= 0 && super.getHoldCount() == 1) {
			startHold = System.nanoTime(); // remember when I first got this lock
			startComment = comment;
			holdingThread = Thread.currentThread().getName();
		}
	}

	/**
	 * same as ReentrantLock.lockInterruptibly() but with a comment to include when logging. This will block
	 * until the lock is available or an interrupt occurs. If lockBlockTimeout &gt;= 0, then this will log if it
	 * has to wait more than lockBlockTimeout milliseconds to obtain the lock.
	 */
	public void lockInterruptibly(String comment) throws InterruptedException {
		// log if it blocks for at least Settings.lockBlockTimeout milliseconds
		if (Settings.lockLogBlockTimeout < 0) {
			super.lockInterruptibly();
			if (enableLogging) {
				// don't reset timer if I get a lock I already have
				if (timeout >= 0 && super.getHoldCount() == 1) {
					startHold = System.nanoTime(); // remember when I first got this lock
					startComment = comment;
					holdingThread = Thread.currentThread().getName();
				}
			}
		} else {
			lock(comment); // lock will call tryLock(...), which locks interruptibly
		}
	}

	/**
	 * Same as ReentrantLock.tryLock() but with a comment to include when logging. This does not block.
	 */
	public boolean tryLock(String comment) {
		boolean success = super.tryLock();
		// don't reset timer if I get a lock I already have
		if (enableLogging && timeout >= 0 && success
				&& super.getHoldCount() == 1) {
			startHold = System.nanoTime(); // remember when I first got this lock
			startComment = comment;
			holdingThread = Thread.currentThread().getName();
		}
		return success;
	}

	/**
	 * Same as ReentrantLock.tryLock(timeout,unit) but with a comment to include when logging. This does not
	 * block.
	 */
	public boolean tryLock(long timeout, TimeUnit unit, String comment)
			throws InterruptedException {
		boolean success = super.tryLock(timeout, unit);
		// don't reset timer if I get a lock I already have
		if (enableLogging && timeout >= 0 && success
				&& super.getHoldCount() == 1) {
			startHold = System.nanoTime(); // remember when I first got this lock
			startComment = comment;
			holdingThread = Thread.currentThread().getName();
		}
		return success;
	}

	/**
	 * same as ReentrantLock.unlock() but with a comment to include when logging. It logs only if the lock
	 * was held too long (more than timeout milliseconds)
	 *
	 * @param comment
	 * 		a comment to include in the log
	 */
	public void unlock(String comment) {
		unlock(() -> comment);
	}

	/**
	 * same as ReentrantLock.unlock() but with a lambda that will be called to provide a comment for
	 * logging, but is only called when a comment is actually needed for logging (which is when the lock was
	 * held for more than timeout milliseconds)
	 *
	 * @param commentSupplier
	 * 		a lambda with no parameters that returns a String to include in the log
	 */
	public void unlock(Supplier<String> commentSupplier) {
		// if timeout==-1, then don't log anything
		if (enableLogging && timeout >= 0 && super.isHeldByCurrentThread()
				&& super.getHoldCount() == 1) {
			long duration = System.nanoTime() - startHold;
			if (duration >= timeout * 1_000_000L) {
				String err = String.format(
						"Lock %35s: %d ms from lock: '%s' to unlock: '%s'", //
						String.format("%12s", "'" + name + "'"),
						(int) (duration / 1_000_000.0), startComment,
						commentSupplier.get());
				log.info(LOCKS.getMarker(), err);
				if (Settings.lockLogThreadDump) {
					ThreadDumpGenerator.generateThreadDumpFile(err);
				}
			}
			if (duration < 0) {
				log.error(LOCKS.getMarker(),
						"LOCK ERROR: Lock {} seemed to be held for negative time {} ms from lock: '{}' to unlock: '{}'",
						//
						String.format("%12s", "'" + name + "'"),
						duration / 1_000_000.0, startComment,
						commentSupplier.get());
			}
			startComment = "ERROR"; // if this isn't changed before appearing in a log, that's a bug
		}
		super.unlock();
	}

	/**
	 * use the lock as a resource:
	 * acquire a Lock and call {@link LoggingReentrantLock#unlock(java.lang.String)} method automatically at the end of
	 * try-with-resources statement
	 *
	 * @return an {@link AutoCloseable} once the lock has been acquired.
	 */
	public ResourceLock lockAsResource(String comment) {
		lock(comment);
		return () -> unlock(comment);
	}
}
