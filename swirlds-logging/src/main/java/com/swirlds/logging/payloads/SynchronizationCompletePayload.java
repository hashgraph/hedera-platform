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

package com.swirlds.logging.payloads;

/**
 * This message is logged by the receiving synchronizer when synchronization has completed.
 */
public class SynchronizationCompletePayload extends AbstractLogPayload {

	private double timeInSeconds;
	private double initializationTimeInSeconds;
	private int totalNodes;
	private int leafNodes;
	private int redundantLeafNodes;
	private int internalNodes;
	private int redundantInternalNodes;

	public SynchronizationCompletePayload() {

	}

	/**
	 * @param message
	 * 		the human readable message
	 * @param timeInSeconds
	 * 		the amount of time that synchronization of the merkle tree required
	 * @param initializationTimeInSeconds
	 * 		the amount of time required to initialize the merkle tree
	 * @param totalNodes
	 * 		the total number of nodes that were sent over the network
	 * @param leafNodes
	 * 		the total number of leaf nodes that were sent over the network
	 * @param redundantLeafNodes
	 * 		the total number of leaf nodes that were redundantly sent
	 * @param internalNodes
	 * 		the total number of internal nodes that were sent over the network
	 * @param redundantInternalNodes
	 * 		the total number of internal nodes that were redundantly sent over the network
	 */
	public SynchronizationCompletePayload(
			final String message,
			final double timeInSeconds,
			final double initializationTimeInSeconds,
			final int totalNodes,
			final int leafNodes,
			final int redundantLeafNodes,
			final int internalNodes,
			final int redundantInternalNodes) {
		super(message);
		this.timeInSeconds = timeInSeconds;
		this.initializationTimeInSeconds = initializationTimeInSeconds;
		this.totalNodes = totalNodes;
		this.leafNodes = leafNodes;
		this.redundantLeafNodes = redundantLeafNodes;
		this.internalNodes = internalNodes;
		this.redundantInternalNodes = redundantInternalNodes;
	}

	public double getTimeInSeconds() {
		return timeInSeconds;
	}

	public void setTimeInSeconds(double timeInSeconds) {
		this.timeInSeconds = timeInSeconds;
	}

	public double getInitializationTimeInSeconds() {
		return initializationTimeInSeconds;
	}

	public void setInitializationTimeInSeconds(double initializationTimeInSeconds) {
		this.initializationTimeInSeconds = initializationTimeInSeconds;
	}

	public int getTotalNodes() {
		return totalNodes;
	}

	public void setTotalNodes(int totalNodes) {
		this.totalNodes = totalNodes;
	}

	public int getLeafNodes() {
		return leafNodes;
	}

	public void setLeafNodes(int leafNodes) {
		this.leafNodes = leafNodes;
	}

	public int getRedundantLeafNodes() {
		return redundantLeafNodes;
	}

	public void setRedundantLeafNodes(int redundantLeafNodes) {
		this.redundantLeafNodes = redundantLeafNodes;
	}

	public int getInternalNodes() {
		return internalNodes;
	}

	public void setInternalNodes(int internalNodes) {
		this.internalNodes = internalNodes;
	}

	public int getRedundantInternalNodes() {
		return redundantInternalNodes;
	}

	public void setRedundantInternalNodes(int redundantInternalNodes) {
		this.redundantInternalNodes = redundantInternalNodes;
	}
}
