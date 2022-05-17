/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.merkle.synchronization.views;

/**
 * This interface contains methods that are used by the teacher during reconnect to track the responses provided by
 * the learner, and to build a working theory of the learner's knowledge.
 *
 * @param <T>
 * 		the type of the object used by the view to represent a merkle node
 */
public interface TeacherResponseTracker<T> {

	/**
	 * Register the result of the learner's response to a query
	 *
	 * @param node
	 * 		the node in question
	 * @param learnerHasNode
	 * 		true if the learner has the node, otherwise false
	 */
	void registerResponseForNode(T node, boolean learnerHasNode);

	/**
	 * Check if the learner has confirmed that it has a given node. Should return false if the learner has responded
	 * with "no" or if the learner has not yet responded.
	 *
	 * @param node
	 * 		the node in question
	 * @return true if a message has been received showing that the learner already has the node
	 */
	boolean hasLearnerConfirmedFor(T node);

}
