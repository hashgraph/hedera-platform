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

import com.swirlds.common.Transaction;
import com.swirlds.platform.event.NoEvent;
import com.swirlds.platform.event.TransactionConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.FREEZE;

/**
 * Store various lists of transactions created by self, both system and non-system, for wrapping in the next
 * Event to be created, and for immediate processing in the various SwirdState or SwirdState2 objects even
 * before being wrapped in an Event.
 *
 * These include a list of transactions to be added to the next Event that is created. They also include a
 * list of transactions by self to be handled by the stateCurr object immediately, so you can see the
 * results of your own actions right away, rather than having to wait until the next sync for them to become
 * visible to yourself. There is also a list for the stateWork object to handle. There is also a list for
 * the stateCons object, but it doesn't read from that list. It merely copies that list to the list for
 * stateWork whenever stateCons is copied to stateWork. And doCons also removes transactions from its list
 * when it sees them inside an event that has achieved consensus and is now being handled by stateCons. That
 * is because future stateWork objects will now reflect them as a result of copying the state, so they don't
 * need to be in this extra list of transactions any more.
 */
class TransLists {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * A single transaction, consisting of a sequence of bytes, and a boolean. All code (including the
	 * caller) must treat this object as immutable, and not modify the contents of the array.
	 */
	// public class Transaction {
	// public final byte[] trans;
	// public final boolean sys;
	//
	// /**
	// * Store trans and sys and don't let them change.
	// *
	// * @param trans
	// * sequence of bytes defining the transaction
	// * @param sys
	// * is this a system transaction?
	// */
	// public Transaction(byte[] trans, boolean sys) {
	// this.trans = trans;
	// this.sys = sys;
	// }
	// }

	/**
	 * Two arrays derived from a Transaction list. This is returned by pollTransForEvent(). All code
	 * (including the caller) should treat this as immutable, and not change the elements of the arrays.
	 */
	// public class TransArrayPair {
	// public final byte[][] trans;
	// public final boolean[] sys;
	//
	// /** convert the transList to two arrays, and clear transList so it is empty */
	// public TransArrayPair(LinkedList<Transaction> transList) {
	// int size = transList.size();
	// trans = new byte[size][];
	// sys = new boolean[size];
	// int i = 0;
	// for (Transaction t : transList) {
	// trans[i] = t.getContentsDirect();
	// sys[i] = t.isSystem();
	// i++;
	// }
	// transList.clear();
	// }
	// }

	/** a non-event sometimes added to the forCurr queue to unblock threadCurr */
	public final EventImpl noEvent = new NoEvent();

	/** The owner of this TransLists. It must be the only object to ever access it. */
	private final EventFlow eventFlow;

	/** list of transactions by self waiting to be put into an event */
	private final LinkedList<Transaction> transEvent = new LinkedList<>();
	/** the number of user transactions in the transEvent list */
	private volatile int numUserTransEvent = 0;
	private volatile int numFreezeTransEvent = 0;
	/** list of transactions by self waiting to be handled by doCurr */
	private volatile LinkedList<Transaction> transCurr = new LinkedList<>();
	/** list of transactions by self waiting to be handled by doWork */
	private volatile LinkedList<Transaction> transWork = new LinkedList<>();
	/** list of transactions by self waiting to be handled by doCons (which just passes them on) */
	private final LinkedList<Transaction> transCons = new LinkedList<>();

	/**
	 * The constructor should be passed an EventFlow which is the only object to access this TransLists.
	 *
	 * @param eventFlow
	 * 		this must be the only object that will ever access this TransLists object
	 */
	TransLists(EventFlow eventFlow) {
		this.eventFlow = eventFlow;
	}

	/**
	 * remove all the transactions from the list waiting to be in an event, and return them as two arrays
	 */
	public synchronized Transaction[] pollTransForEvent() {
		// Early return due to no transactions waiting
		if (transEvent.size() == 0) {
// log.debug(Settings.LOGM_REGRESSION_TESTS,
// "Regression: Early pollTransForEvent() exit");
			return new Transaction[0];
		}

		final LinkedList<Transaction> selectedTrans = new LinkedList<>();
		int currEventSize = 0;

		while (currEventSize < Settings.maxTransactionBytesPerEvent && transEvent.size() > 0) {
			final Transaction trans = transEvent.peek();

			if (trans != null) {
				// This event already contains transactions
				// The next transaction is larger than the remaining space in the event
				if (trans.size() > (Settings.maxTransactionBytesPerEvent - currEventSize)) {
// log.debug(Settings.LOGM_REGRESSION_TESTS,
// "Regression: Transaction size ({}) larger than remaining event space ({})",
// trans.size(),
// (Settings.maxTransactionBytesPerEvent - currEventSize));
					break;
				}

				currEventSize += trans.size();
				selectedTrans.offer(transEvent.poll());
				if (!trans.isSystem()) {
					numUserTransEvent--;
				} else {
					if (trans.getContents(0) == TransactionConstants.SYS_TRANS_STATE_SIG_FREEZE) {
						numFreezeTransEvent--;
						log.info(FREEZE.getMarker(),
								"A Freeze system transaction has been put into selectedTrans. numFreezeTransEvent: {}",
								numFreezeTransEvent);
					}
				}
			}
		}

		return selectedTrans.toArray(new Transaction[selectedTrans.size()]);
	}

	/**
	 * @return the number of user transactions waiting to be put in an event
	 */
	public int numUserTransForEvent() {
		return numUserTransEvent + numFreezeTransEvent;
	}

	public int numFreezeTransEvent() {
		return numFreezeTransEvent;
	}

	/**
	 * Add the given transaction to all the stored lists. If any are full, it does nothing and returns false
	 * immediately.
	 *
	 * @param trans
	 * 		The transaction. It must have been created by self.
	 * @return true if successful
	 */
	public synchronized boolean offer(Transaction trans) {
		// Check if we should ignore this transaction and return false because the queue is full.
		// Always accept system transactions, but stop accepting others when the queue for the next event is
		// full.
		final int t = Settings.throttleTransactionQueueSize;
		if (!trans.isSystem() && //
				(transEvent.size() > t //
						|| transCurr.size() > t //
						|| transCons.size() > t //
						|| (!eventFlow.isSwirldState2()
						&& transWork.size() > t))) {
			return false;
		}

		boolean ans = true; // this should stay true. The linked lists will never be full or return false.
		// both SwirldState and SwirldState2 use these 3 queues
		ans = ans && transEvent.offer(trans);
		if (ans) {
			if (!trans.isSystem()) {
				numUserTransEvent++;
			} else {
				if (trans.getContents(0) == TransactionConstants.SYS_TRANS_STATE_SIG_FREEZE) {
					numFreezeTransEvent++;
					log.info(FREEZE.getMarker(),
							"Freeze system transaction has been put into transEvent. numFreezeTransEvent: {}",
							numFreezeTransEvent);
				}
			}
		}
		ans = ans && transCurr.offer(trans);
		ans = ans && transCons.offer(trans);
		// this 4th queue is only for SwirldState
		if (!eventFlow.isSwirldState2()) {
			ans = ans && transWork.offer(trans);
		}

		// if forCurr is empty, then put in noEvent so anyone waiting for an event will unblock,
		// though they will detect that this is a noEvent, and so not process it. It's just there
		// to make them wake up briefly, so they can process this new transaction. It also does
		// the same for forWork if it is empty
		eventFlow.unblockCurrWork();
		if (!ans) {
			log.error(EXCEPTION.getMarker(),
					"TransLists queues were all shorter than Settings.throttleQueues,"
							+ " yet offer returned false");
		}
		return ans; // this will be true, unless a bad error has occurred
	}

	/**
	 * Add the given transaction to all the stored lists. If any are full, it does nothing and returns
	 * false.
	 *
	 * @param trans
	 * 		The sequence of bytes for the transaction. It must have been created by self.
	 * @param system
	 * 		true if this is a system transaction
	 * @return did the insertion succeed?
	 */
	public synchronized boolean offer(Transaction trans, boolean system) {
		return offer(trans);
	}

	/** remove and return the earliest-added event in transCurr, or null if none */
	public synchronized Transaction pollCurr() {
		return transCurr.poll();
	}

	/** remove and return the earliest-added event in transWork, or null if none */
	public synchronized Transaction pollWork() {
		return transWork.poll();
	}

	/** remove and return the earliest-added event in transCons, or null if none */
	public synchronized Transaction pollCons() {
		return transCons.poll();
	}

	/**
	 * get the number of transactions in the transCurr queue (to be handled by stateCurr)
	 *
	 * @return the number of transactions
	 */
	synchronized int getCurrSize() {
		return transCurr.size();
	}

	/**
	 * get the number of transactions in the transWork queue (to be handled by stateWork)
	 *
	 * @return the number of transactions
	 */
	synchronized int getWorkSize() {
		return transWork.size();
	}

	/**
	 * get the number of transactions in the transEvent queue
	 *
	 * @return the number of transactions
	 */
	synchronized int getEventSize() {
		return transEvent.size();
	}

	/**
	 * get the number of transactions in the transCons queue
	 *
	 * @return the number of transactions
	 */
	synchronized int getConsSize() {
		return transCons.size();
	}

	/** Do a shuffle: discard transCurr, move transWork to transCurr, clone transCons to transWork */
	@SuppressWarnings("unchecked") // needed because stupid Java type erasure gives no alternative
	public synchronized void shuffle() {
		transCurr = transWork;
		transWork = (LinkedList<Transaction>) transCons.clone();
	}

	/** return a single string giving the number of transactions in each list, and swirldState2 */
	public synchronized String status() {
		return "TransList sizes:"//
				+ " transEvent=" + transEvent.size()//
				+ " transCurr=" + transCurr.size()//
				+ " transWork=" + transWork.size()//
				+ " transCons=" + transCons.size()//
				+ " swirldState2=" + eventFlow.isSwirldState2();
	}

	/**
	 * Clear all the transactions from TransLists
	 */
	synchronized void clear() {
		transEvent.clear();
		transCurr.clear();
		transWork.clear();
		transCons.clear();
	}
}
