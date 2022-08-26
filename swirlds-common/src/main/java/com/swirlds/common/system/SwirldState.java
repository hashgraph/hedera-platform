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

package com.swirlds.common.system;

import com.swirlds.common.Releasable;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.interfaces.Archivable;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.SwirldTransaction;

import java.time.Instant;

/**
 * A Swirld app is defined by creating two classes, one implementing {@link SwirldMain}, and the other
 * {@link SwirldState}, such that: <br>
 * <br>
 * <ul>
 * <li>{@code SwirldState} has a no-argument constructor (called by {@link Platform})</li>
 * <li>All {@code SwirldState} variables are thread-safe and private</li>
 * <li>All {@code SwirldState} methods are synchronized</li>
 * <li>{@code SwirldMain} never modifies an object in {@code SwirldState}</li>
 * </ul>
 * <br>
 * So, if {@code SwirldState} contains an array, and {@code SwirldMain} gets it through a getter method,
 * then the developer is responsible for making sure {@code SwirldMain} never changes the contents of that
 * array. Or the getter can simply return a deep copy of the array instead of the original.
 */
public interface SwirldState extends Archivable, MerkleNode {

	/**
	 * Initialize a state. Called exactly once each time a node creates/recreates a state
	 * (e.g. restart, reconnect, genesis).
	 *
	 * The application should check to see if the address book has changed, and if so those
	 * changes should be handled (in the future the address book will not be changed in this method).
	 * It may also be convenient for the application to initialize internal data structures at this time.
	 *
	 * @param platform
	 * 		the Platform that instantiated this state
	 * @param addressBook
	 * 		the members and info about them
	 * @param swirldDualState
	 * 		the dual state instance used by the initialization function
	 * @param trigger
	 * 		describes the reason why the state was created/recreated
	 * @param previousSoftwareVersion
	 * 		the previous version of the software, {@link SoftwareVersion#NO_VERSION} if this is genesis or if
	 * 		migrating from code from before the concept of an application software version
	 */
	default void init(
			final Platform platform,
			final AddressBook addressBook,
			final SwirldDualState swirldDualState,
			final InitTrigger trigger,
			final SoftwareVersion previousSoftwareVersion) {
		// Override if needed
	}

	/**
	 * Return a deep copy of the the current address book.
	 *
	 * @return a deep copy of the current address book
	 */
	AddressBook getAddressBookCopy();

	/**
	 * Given a transaction, update the state to reflect its effect. A given SwirldState object will see a
	 * sequence of transactions consisting of some number for which <code>consensus</code> is true, followed
	 * by the rest being false. The transactions for which it is true are sent in the consensus order. The
	 * rest are sent in an order that is the current best guess as to what the consensus will be. But that
	 * order is subject to change. A given SwirldState object will never see that order change. But a
	 * different SwirldState object may be instantiated by the Platform, and it may receive those
	 * transactions in a different order.
	 * <p>
	 * The address parameter will usually be null. When it isn't, this transaction is a request that a new
	 * member be added to the address book. The new member's information is passed in as
	 * <code>address</code>, and the member who is inviting them has ID number <code>id</code> (or -1 if the
	 * config.txt file is "inviting" them).
	 * <p>
	 * For such invitations, the transaction <code>trans</code> might be null. Or it might describe
	 * something relevant to the invitation, such as what privileges the invitor gives the inviteee. Or
	 * perhaps some fraction of the invitor's resources to be given to the invitee. This method is
	 * responsible for deciding whether to add the invitee to the address book, to make the addition (or
	 * not), and to update the state accordingly (such as by transferring resources from inviter to
	 * invitee).
	 * <p>
	 * The state of this object must NEVER change except inside the methods init(), SwirldState.copyFrom(),
	 * FastCopyable.copyFrom(), and handleTransaction(). So it is good if handleTransaction changes some of
	 * the class variables and then returns. It is also OK if handleTransactions spawns a number of threads
	 * that change those variables, then waits until all those threads have ended, and then returns. It is
	 * even OK for it to create a pool of threads that continue to exist after handleTransaction returns, as
	 * long as it ensures that those threads have finished all their changes before it returns. But it is an
	 * error for handleTransaction to spawn a thread that will make changes after handleTransaction returns
	 * and before the next time handleTransaction is called. If handleTransaction does create threads that
	 * continue to exist after it returns (in a legal way), then it should clean up those resources when the object has
	 * been fully released (see documentation in {@link Releasable}). If the SwirldState extends one of the partial
	 * merkle implementations (e.g. PartialMerkleLeaf or PartialNaryMerkleInternal) then it can put that cleanup in the
	 * onDestroy() method, which is called when the object becomes fully released.
	 *
	 * @param id
	 * 		the ID number of the member who created this transaction
	 * @param isConsensus
	 * 		is this transaction's timeCreated and position in history part of the consensus?
	 * @param timeCreated
	 * 		the time when this transaction was first created and sent to the network, as claimed by
	 * 		the member that created it (which might be dishonest or mistaken)
	 * @param timestamp
	 * 		the consensus timestamp for when this transaction happened (or an estimate of it, if it
	 * 		hasn't reached consensus yet)
	 * @param trans
	 * 		the transaction to handle, encoded any way the swirld app author chooses
	 * @param swirldDualState
	 * 		current dualState object which can be read/written by the application
	 */
	void handleTransaction(long id, boolean isConsensus,
			Instant timeCreated, Instant timestamp, SwirldTransaction trans, SwirldDualState swirldDualState);

	/**
	 * Called against a given {@link SwirldTransaction} only once and immediately before the event is added to the
	 * hashgraph. This method may modify the given {@link SwirldTransaction} by doing nothing, adding additional
	 * signatures, removing existing signatures, or replacing signatures with versions that expand the
	 * public key from an application specific identifier to an actual public key. Additional signatures
	 * extracted from the transaction payload can also be added to the list of signatures to be verified.
	 *
	 * @param trans
	 * 		the transaction for which signature expansion should occur
	 * @see SwirldTransaction
	 * @see TransactionSignature
	 */
	void expandSignatures(SwirldTransaction trans);

	/**
	 * {@inheritDoc}
	 *
	 * WARNING: failure to properly call archive on some types of internal data structures such as MerkleMaps may
	 * result in a much larger memory footprint than necessary.
	 */
	@Override
	default void archive() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SwirldState copy();

	/**
	 * Normally, an app must define a state class that implements SwirldState. The Platform then sends
	 * transactions to it, where any given transaction is only sent once. The app could instead implement
	 * SwirldState2, which is identical, except that the Platform will send each transaction twice. If the
	 * app should respond to transactions immediately, even before their consensus order is known, then it
	 * should implement SwirldState. If it is acceptable to wait until consensus has been found before each
	 * transaction is processed, then the app can implement either SwirldState or SwirldState2, and the
	 * latter will be more efficient, reducing the amount of computation. If the former is implemented, then
	 * the app can allow non-consensus actions to change the shared state. If the latter is implemented,
	 * then it must not. The following describes what is happening in more detail.
	 * <p>
	 * If an app's state class implements SwirldState, then the platform will instantiate it to create a
	 * state object X, and will send transactions to it by calling X.handleTransaction(). Each transaction
	 * is sent to X only once. First the Platform will give X multiple transactions where consensus is true.
	 * Then it will make a fast copy of X, creating a new object Y. Then it will send X multiple
	 * transactions where consensus is false. Eventually, it will stop sending transactions to X, and switch
	 * to sending transactions to Y. It will start by sending multiple transactions that had consensus=false
	 * when they were sent to X, but now have consensus=true when they are sent to Y. In this way, any
	 * particular state object will see each transaction only once, and the platform frequently makes fast
	 * copies of the objects. The computer may handle each transaction multiple times, but any particular
	 * object only handles it once. This is useful, because it lets the app respond to transactions even
	 * before they have a consensus order, but then can re-respond appropriately when that order changes.
	 * The app doesn't have to be written specially to deal with this changing history. The Platform takes
	 * care of managing all the version control as the order of history keeps changing.
	 * <p>
	 * If an app's state class implements SwirldState2, then the Platform treats it differently. It will
	 * instantiate an object X, and send it every transaction twice. The first time it sends a transaction
	 * T, it will have consensus=false, and the second time it sends T, it will have consensus = true. Some
	 * transactions might only be sent once (with consensus=true), but most will be sent twice. The sequence
	 * of transactions will go back and forth between consensus being true and false, rather than having all
	 * true followed by all false. When using SwirldState2, the Platform does not have to make frequent fast
	 * copies of the state (though it will still make some fast copies, for other reasons). An app
	 * implementing SwirldState2 must either completely ignore the transactions with consensus=false, or
	 * handle them specially so that they don't affect the shared state. The SwirldState2 instance is always immutable
	 * when consensus=false. If there is a need to actually show their effect on the state, then it will be easier to
	 * implement SwirldState. But if it is acceptable to delay the effect until consensus, then SwirldState2 may be
	 * more efficient.
	 */
	interface SwirldState2 extends SwirldState {
	}
}
