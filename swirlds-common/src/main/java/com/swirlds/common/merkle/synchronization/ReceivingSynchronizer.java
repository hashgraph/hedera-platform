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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.hash.MerkleHashValidator;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.threading.StandardWorkGroup;
import com.swirlds.logging.payloads.SynchronizationCompletePayload;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

import static com.swirlds.common.merkle.synchronization.MerkleSynchronizationUtils.getChild;
import static com.swirlds.common.merkle.synchronization.MerkleSynchronizationUtils.getChildHash;
import static com.swirlds.common.merkle.synchronization.MerkleSynchronizationUtils.getHash;
import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;

/**
 * Merkle tree synchronization is a process by which two potentially overlapping merkle trees are made into
 * the same tree without transmitting the overlapping data.
 *
 * There are two types of participants in a synchronization: the "receiver" and the "sender". The receiver
 * wants to have the same merkle tree that the sender has. (The sender does not care about updating its own tree.)
 * The receiver may have a merkle tree that contains anywhere from 0-100% of the sender's tree.
 *
 * This class implements the receiver.
 */
public class ReceivingSynchronizer {

	private Logger log;
	private Marker marker;

	/**
	 * Used to get data from the sender.
	 */
	private MerkleDataInputStream in;

	/**
	 * Used to transmit data to the sender.
	 */
	private MerkleDataOutputStream out;

	/**
	 * The root of the merkle tree known by the receiver.
	 */
	private final MerkleNode originalRoot;

	/**
	 * The root of the merkle tree that resulted from the synchronization operation.
	 */
	private MerkleNode newRoot;

	/**
	 * The nodes that require initialization after synchronization is complete.
	 */
	private LinkedList<MerkleInternal> uninitializedNodes;

	/**
	 * Contains information about nodes which the receiver is awaiting data from the sender.
	 */
	private final Queue<ExpectedNodeData> expectedNodeData;

	/**
	 * ACK messages are all the same, no need to create a new object for each one.
	 */
	private static final AckMessage positiveAck = new AckMessage(true);
	private static final AckMessage negativeAck = new AckMessage(false);

	private int leafNodesReceived;
	private int internalNodesReceived;
	private int redundantLeafNodes;
	private int redundantInternalNodes;

	private long synchronizationTimeMilliseconds;
	private long initializationTimeMilliseconds;

	/**
	 * Create a new merkle synchronization receiver.
	 *
	 * @param in
	 * 		A stream for getting data from the sender.
	 * @param out
	 * 		A stream for transmitting data to the sender.
	 * @param originalRoot
	 * 		The root of the merkle tree known by the receiver. The tree referenced by this root will
	 * 		not be modified in any way by this method.
	 */
	public ReceivingSynchronizer(MerkleDataInputStream in, MerkleDataOutputStream out,
			MerkleNode originalRoot, Logger log, Marker marker) throws MerkleSynchronizationException {
		this.in = in;
		this.out = out;
		this.originalRoot = originalRoot;
		this.log = log;
		this.marker = marker;
		this.expectedNodeData = new LinkedList<>();
		this.uninitializedNodes = new LinkedList<>();
	}

	private void logStatistics() {
		log.info(marker, () -> new SynchronizationCompletePayload(
				"Finished synchronization",
				synchronizationTimeMilliseconds / 1000.0,
				initializationTimeMilliseconds / 1000.0,
				leafNodesReceived + internalNodesReceived,
				leafNodesReceived,
				redundantLeafNodes,
				internalNodesReceived,
				redundantInternalNodes).toString());
	}

	/**
	 * Utility method to aid in benchmarking. Allows for the output stream to be replaced
	 * with a stream that simulates lag.
	 */
	protected AsyncOutputStream getAsyncOutputStream(MerkleDataOutputStream out, StandardWorkGroup workGroup) {
		return new AsyncOutputStream(out, workGroup);
	}

	/**
	 * Synchronize with the receiver on another thread.
	 *
	 * @return The root of the newly synchronized tree.
	 */
	public MerkleNode synchronize() throws InterruptedException {
		long synchronizationStartTime = System.currentTimeMillis();

		log.info(marker, "Starting synchronization in the role of the receiver.");

		StandardWorkGroup workGroup = new StandardWorkGroup("receiving-synchronizer");

		AsyncInputStream asyncIn = new AsyncInputStream(in, workGroup);
		AsyncOutputStream asyncOut = getAsyncOutputStream(out, workGroup);
		try (MerkleHashValidator validator = new MerkleHashValidator(
				ReconnectSettingsFactory.get().getHashValidationThreadPoolSize())) {

			workGroup.execute("receiving-thread", () -> execute(asyncIn, asyncOut, validator));

			workGroup.waitForTermination();

			if (workGroup.hasExceptions()) {
				cleanupFailedSynchronization(asyncIn);
				workGroup.logAllExceptions(log, marker, Level.ERROR);
				throw new MerkleSynchronizationException("Synchronization failed with exceptions");
			}

			try {
				if (!validator.isValid()) {
					throw new MerkleSynchronizationException("Invalid hash detected, aborting synchronization");
				}
			} catch (ExecutionException e) {
				throw new MerkleSynchronizationException(e);
			}
		}

		synchronizationTimeMilliseconds = System.currentTimeMillis() - synchronizationStartTime;

		logStatistics();

		return newRoot;
	}

	/**
	 * Should be called if reconnect is started but fails. Cleans up resources.
	 */
	private void cleanupFailedSynchronization(AsyncInputStream asyncIn) {
		if (newRoot != null) {
			newRoot.release();
		}
		asyncIn.abort();
	}

	/**
	 * Prepare to receive data about a node in the future.
	 */
	private void prepareForNodeData(AsyncInputStream asyncIn, Hash expectedHash, MerkleInternal parent,
			int childIndex, MerkleNode originalNode) {
		asyncIn.addAnticipatedMessage(new NodeDataMessage());
		expectedNodeData.add(new ExpectedNodeData(expectedHash, parent, childIndex, originalNode));
	}

	/**
	 * Handle data containing a leaf node.
	 */
	private void handleLeafData(ExpectedNodeData expectedData, NodeDataMessage data, MerkleHashValidator validator) {
		validator.validateAsync(expectedData.getHash(), (MerkleLeaf) data.getNode());
	}

	/**
	 * Handle data containing an internal node.
	 */
	private void handleInternalData(ExpectedNodeData expectedData, NodeDataMessage data, MerkleHashValidator validator,
			AsyncInputStream asyncIn, AsyncOutputStream asyncOut) throws InterruptedException {

		MerkleInternal node = (MerkleInternal) data.getNode();
		MerkleNode originalNode = expectedData.getOriginalNode();
		markForInitialization(node);

		validator.validateAsync(expectedData.getHash(), node, data.getChildHashes());
		for (int childIndex = 0; childIndex < data.getNumberOfChildren(); childIndex++) {
			if (data.getChildHashes().get(childIndex).equals(getChildHash(originalNode, childIndex))) {
				sendAck(asyncOut, true);
			} else {
				sendAck(asyncOut, false);
			}

			Hash childHash = data.getChildHashes().get(childIndex);
			prepareForNodeData(asyncIn, childHash, node, childIndex, getChild(originalNode, childIndex));
		}
	}

	private void validateLocalData(MerkleHashValidator validator, ExpectedNodeData expectedNodeData) {
		Hash hash;
		if (expectedNodeData.getOriginalNode() == null) {
			hash = CryptoFactory.getInstance().getNullHash(MERKLE_DIGEST_TYPE);
		} else {
			hash = expectedNodeData.getOriginalNode().getHash();
		}

		validator.validate(expectedNodeData.getHash(), hash);
	}

	private void addToNodeCount(ExpectedNodeData expectedData, NodeDataMessage data) {
		boolean isLeaf = data.getNode() == null || data.getNode().isLeaf();
		if (isLeaf) {
			leafNodesReceived++;
		} else {
			internalNodesReceived++;
		}
		Hash localHash = getHash(expectedData.getOriginalNode());
		Hash newHash = expectedData.getHash();
		boolean hashMatches = localHash.equals(newHash);
		if (hashMatches) {
			if (isLeaf) {
				redundantLeafNodes++;
			} else {
				redundantInternalNodes++;
			}
		}
	}

	private void handleData(ExpectedNodeData expectedData, NodeDataMessage data, MerkleHashValidator validator,
			AsyncInputStream asyncIn, AsyncOutputStream asyncOut) throws InterruptedException {


		MerkleNode node;
		if (data.currentNodeIsUpToDate()) {
			node = expectedData.getOriginalNode();
			validateLocalData(validator, expectedData);
		} else {

			addToNodeCount(expectedData, data);
			node = data.getNode();
			if (data.isLeaf()) {
				handleLeafData(expectedData, data, validator);
			} else {
				handleInternalData(expectedData, data, validator, asyncIn, asyncOut);
			}
		}

		addToParent(node, expectedData.getParent(), expectedData.getPositionInParent());
	}

	/**
	 * Perform the synchronization algorithm in the role of the receiver.
	 */
	private void execute(AsyncInputStream asyncIn, AsyncOutputStream asyncOut, MerkleHashValidator validator) {
		try {
			asyncIn.addAnticipatedMessage(new Hash());
			Hash rootHash = asyncIn.readAnticipatedMessage();
			sendAck(asyncOut, rootHash.equals(getHash(originalRoot)));
			prepareForNodeData(asyncIn, rootHash, null, 0, originalRoot);

			while ((expectedNodeData.size() > 0 && validator.isValidSoFar())
					&& !Thread.currentThread().isInterrupted()) {
				ExpectedNodeData expectedData = expectedNodeData.remove();
				NodeDataMessage data = asyncIn.readAnticipatedMessage();
				handleData(expectedData, data, validator, asyncIn, asyncOut);
			}

			if (validator.isValidSoFar() && !Thread.currentThread().isInterrupted()) {
				initialize();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			asyncIn.close();
			asyncOut.close();
		}
	}

	/**
	 * Initialize all nodes that were transmitted from the Sender.
	 */
	private void initialize() {
		long startTime = System.currentTimeMillis();
		log.info(marker, "Starting initialization of merkle tree.");
		for (MerkleInternal node : uninitializedNodes) {
			node.initialize(null);
		}
		uninitializedNodes = null;
		initializationTimeMilliseconds = System.currentTimeMillis() - startTime;
	}

	/**
	 * Prepare a node for later initialization.
	 */
	private void markForInitialization(MerkleInternal node) {
		// Nodes are added in reverse order
		// This allows for a forward-iterator to initialize nodes before their ancestors
		uninitializedNodes.addFirst(node);
	}

	private void sendAck(AsyncOutputStream asyncOut, boolean affirmative) throws InterruptedException {
		if (affirmative) {
			asyncOut.sendAsync(positiveAck);
		} else {
			asyncOut.sendAsync(negativeAck);
		}
	}

	/**
	 * Add a new node to its parent.
	 */
	private void addToParent(MerkleNode node, MerkleInternal parent, int childIndex) {
		if (parent == null) {
			// This node is the root
			newRoot = node;
		} else {
			parent.setChild(childIndex, node);
		}
	}

	/**
	 * Returns the root of the tree after synchronization. Only valid when synchronization has finished.
	 */
	public MerkleNode getRoot() {
		return newRoot;
	}
}
