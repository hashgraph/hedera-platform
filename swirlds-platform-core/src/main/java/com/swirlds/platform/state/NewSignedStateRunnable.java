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

package com.swirlds.platform.state;

import com.swirlds.common.SwirldMain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.BlockingQueue;

import static com.swirlds.logging.LogMarker.EXCEPTION;

public class NewSignedStateRunnable implements Runnable {

	private static final Logger log = LogManager.getLogger();
	private BlockingQueue<SignedState> newSignedStateQueue;
	private SwirldMain swirldMain;

	public NewSignedStateRunnable(BlockingQueue<SignedState> newSignedStateQueue, SwirldMain swirldMain) {
		this.newSignedStateQueue = newSignedStateQueue;
		this.swirldMain = swirldMain;
	}

	@Override
	public void run() {
		while (true) {
			try {
				// Signed states in this queue have already been reserved
				SignedState signedState = newSignedStateQueue.take();
				try {
					swirldMain.newSignedState(
							signedState.getState(),
							signedState.getConsensusTimestamp(),
							signedState.getLastRoundReceived());
				} finally {
					signedState.releaseState();
				}
			} catch (Throwable t) {
				log.error(EXCEPTION.getMarker(), "Exception in NewSignedStateRunnable", t);
			}
		}
	}
}
