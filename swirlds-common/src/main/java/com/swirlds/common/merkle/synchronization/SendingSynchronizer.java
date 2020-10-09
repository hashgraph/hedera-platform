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

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.threading.StandardWorkGroup;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.swirlds.common.merkle.synchronization.MerkleSynchronizationUtils.getHash;

/**
 * Merkle tree synchronization is a process by which two potentially overlapping merkle trees are made into
 * the same tree without transmitting the overlapping data.
 *
 * There are two types of participants in a synchronization: the "receiver" and the "sender". The receiver
 * wants to have the same merkle tree that the sender has. (The sender does not care about updating its own tree.)
 * The receiver may have a merkle tree that contains anywhere from 0-100% of the sender's tree.
 *
 * This class implements the sender.
 */
public class SendingSynchronizer {

	private Logger log;
	private Marker marker;

	/**
	 * Used to get data from the listener.
	 */
	private MerkleDataInputStream in;

	/**
	 * Used to transmit data to the listener.
	 */
	private MerkleDataOutputStream out;

	private MerkleNode root;

	/**
	 * Nodes that will be sent to the receiver. If we learn that the receiver already has the node before we send then
	 * a placeholder will be sent instead.
	 */
	private final Queue<NodeToSend> nodesToSend;

	/**
	 * Nodes that have had their hash sent but the corresponding AckMessage has not yet been received.
	 */
	private final BlockingQueue<NodeToSend> nodesAwaitingResponse;

	volatile boolean finished;

	/**
	 * Create a new merkle synchronization receiver.
	 *
	 * @param in
	 * 		A stream for getting data from the sender.
	 * @param out
	 * 		A stream for transmitting data to the sender.
	 * @param root
	 * 		The root of the merkle tree known by the receiver. The tree referenced by this root will
	 * 		not be modified in any way by this method.
	 * @param log
	 * 		used to write messages to the log
	 * @param marker
	 * 		the marker to use while writing to the log
	 */
	public SendingSynchronizer(MerkleDataInputStream in, MerkleDataOutputStream out, MerkleNode root,
			Logger log, Marker marker) {
		this.in = in;
		this.out = out;
		this.root = root;
		this.log = log;
		this.marker = marker;

		this.nodesToSend = new LinkedList<>();
		this.nodesAwaitingResponse = new LinkedBlockingQueue<>();

		this.finished = false;
	}

	private NodeToSend prepareToSend(AsyncInputStream asyncIn, MerkleNode node) {
		asyncIn.addAnticipatedMessage(new AckMessage());
		NodeToSend nodeToSend = new NodeToSend(node);
		this.nodesToSend.add(nodeToSend);
		this.nodesAwaitingResponse.add(nodeToSend);
		return nodeToSend;
	}

	/**
	 * Utility method to aid in benchmarking. Allows for the output stream to be replaced
	 * with a stream that simulates lag.
	 */
	protected AsyncOutputStream getAsyncOutputStream(MerkleDataOutputStream out, StandardWorkGroup workGroup) {
		return new AsyncOutputStream(out, workGroup);
	}

	/**
	 * Synchronize with the receiver. Blocks until finished.
	 */
	public void synchronize() throws InterruptedException {
		StandardWorkGroup workGroup = new StandardWorkGroup("sending-synchronizer");

		AsyncInputStream asyncIn = new AsyncInputStream(in, workGroup);
		AsyncOutputStream asyncOut = getAsyncOutputStream(out, workGroup);

		prepareToSend(asyncIn, root);

		workGroup.execute("receiving-thread", () -> receivingThread(asyncIn));
		workGroup.execute("sending-thread", () -> sendingThread(asyncIn, asyncOut));

		workGroup.waitForTermination();

		if (workGroup.hasExceptions()) {
			workGroup.logAllExceptions(log, marker, Level.ERROR);
			throw new MerkleSynchronizationException("Synchronization failed with exceptions");
		}
	}

	/**
	 * Send a node's full data, including the hashes of the children (if any).
	 */
	private void sendFullNode(AsyncInputStream asyncIn, AsyncOutputStream asyncOut, NodeToSend nodeToSend)
			throws InterruptedException {

		asyncOut.sendAsync(new NodeDataMessage(nodeToSend.getNode()));
		if (nodeToSend.getNode() != null && !nodeToSend.getNode().isLeaf()) {
			MerkleInternal internal = (MerkleInternal) nodeToSend.getNode();
			List<NodeToSend> children = new LinkedList<>();
			for (int childIndex = 0; childIndex < internal.getNumberOfChildren(); childIndex++) {
				MerkleNode child = internal.getChild(childIndex);
				NodeToSend childToSend = prepareToSend(asyncIn, child);
				children.add(childToSend);
			}
			nodeToSend.addChildren(children);
		}
	}

	/**
	 * This thread sends nodes to the receiver.
	 */
	private void sendingThread(AsyncInputStream asyncIn, AsyncOutputStream asyncOut) {
		try {

			// The receiver expects the root hash to be the first thing sent
			asyncOut.sendAsync(getHash(root));

			while (!nodesToSend.isEmpty()) {
				NodeToSend nodeToSend = nodesToSend.remove();

				nodeToSend.waitForAck();

				if (nodeToSend.getAckStatus()) {
					// Don't send the full node, just inform the receiver that we will not be sending it.
					asyncOut.sendAsync(new NodeDataMessage());
				} else {
					// Send all of the node's data (including child information and hashes)
					sendFullNode(asyncIn, asyncOut, nodeToSend);
				}

			}
			finished = true;

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			asyncOut.close();
		}
	}

	/**
	 * Read the ACK/NACK response from the stream.
	 */
	protected boolean getAck(AsyncInputStream asyncIn) throws InterruptedException {
		AckMessage ack = asyncIn.readAnticipatedMessage();
		return ack.isAffirmative();
	}

	/**
	 * This thread receives responses form the receiver, restricting the nodes sent as it obtains a "theory" of the
	 * status of the receiver's tree.
	 */
	private void receivingThread(AsyncInputStream asyncIn) {
		try {
			long lastSuccessfulPollMilliseconds = System.currentTimeMillis();
			while ((!finished || nodesAwaitingResponse.size() > 0) && !Thread.currentThread().isInterrupted()) {
				NodeToSend nodeAwaitingResponse = nodesAwaitingResponse.poll(10, TimeUnit.MILLISECONDS);
				long now = System.currentTimeMillis();
				if (nodeAwaitingResponse == null) {
					if (now - lastSuccessfulPollMilliseconds > 10_000) {
						// Sanity check -- if it somehow takes more than 10 seconds to receive an ACK message then
						// something has gone terribly wrong.
						throw new MerkleSynchronizationException("Timed out while waiting for ACK message");
					}
					continue;
				} else {
					lastSuccessfulPollMilliseconds = now;
				}
				boolean ackStatus = getAck(asyncIn);
				nodeAwaitingResponse.registerAck(ackStatus);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			asyncIn.close();
		}
	}
}
