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

package com.swirlds.platform.state;

import com.swirlds.common.system.SwirldMain;
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
							signedState.getSwirldState(),
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
