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

import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.internal.SubSetting;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;

import java.time.Duration;
import java.util.function.LongUnaryOperator;

/**
 * Settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors.
 */
public class StateSettings extends SubSetting {

	/**
	 * The directory where states are saved. This is relative to the current working directory, unless
	 * the provided path begins with "/", in which case it will be interpreted as an absolute path.
	 */
	public String savedStateDirectory = "data/saved";

	/**
	 * If true, clean out all data in the {@link #savedStateDirectory} except for the previously saved state.
	 */
	public boolean cleanSavedStateDirectory = false;

	/**
	 * The number of states permitted to sit in the signed state file manager's queue of states being written.
	 * If this queue backs up then some states may not be written to disk.
	 */
	public int stateSavingQueueSize = 20;

	/**
	 * The frequency of writes of a state to disk every this many seconds (0 to never write).
	 */
	public int saveStatePeriod = 0;

	/**
	 * Keep at least this many of the old complete signed states on disk. This should be at least 2 so that
	 * we don't delete an old state while a new one is in the process of writing to disk. set to 0 to not
	 * keep any states to disk.
	 */
	public int signedStateDisk = 3;

	/**
	 * Events this many rounds old are expired, and can be deleted from memory
	 */
	public int roundsExpired = 500;

	/**
	 * The number of consensus rounds that are defined to be non-ancient. There can be more non-ancient rounds, but
	 * these rounds will not have reached consensus. Once consensus is reached on a new round
	 * (i.e.,fame is decided for all its witnesses), another round will become ancient. Events, whose generation is
	 * older than the last non-ancient round generation, are ancient. If they don't have consensus yet, they're stale,
	 * and will never reach consensus and never have their transactions handled.
	 */
	public int roundsNonAncient = 26;

	/**
	 * If true, save the state to disk when an ISS is detected. May negatively affect the performance
	 * of the node where the ISS occurs.
	 *
	 * This feature is for debugging purposes and should not be active in production systems.
	 */
	public boolean dumpStateOnISS = false;

	/**
	 * If true, then save the state to disk when there is a fatal exception.
	 */
	public boolean dumpStateOnFatal = true;

	/**
	 * If one ISS is detected, it is likely that others will be detected shortly afterwards. Specify the minimum
	 * time, in seconds, that must transpire after dumping a state before another state dump is permitted. Ignored
	 * if dumpStateOnISS is false.
	 */
	public double secondsBetweenISSDumps = Duration.ofHours(6).toSeconds();

	/**
	 * If true then a single background thread is used to do validation of signed state hashes. Validation is on
	 * a best effort basis. If it takes too long to validate a state then new states will be skipped.
	 */
	public static boolean backgroundHashChecking = false;

	/**
	 * When logging debug information about the hashes in a merkle tree, do not display hash information
	 * for nodes deeper than this.
	 */
	public static int debugHashDepth = 5;

	/**
	 * If there are problems with state lifecycle then write errors to the log at most once per this period of time.
	 */
	public int stateDeletionErrorLogFrequencySeconds = 60;

	/**
	 * When enabled, hashes for the nodes are logged per round.
	 */
	public boolean enableHashStreamLogging = true;  // NOSONAR: Value is modified and updated by reflection.

	public StateSettings() {

	}

	/**
	 * Get the minimum amount of time between when errors about state deletion are logged.
	 */
	public int getStateDeletionErrorLogFrequencySeconds() {
		return stateDeletionErrorLogFrequencySeconds;
	}

	/**
	 * getter for the frequency of writes of a state to disk
	 *
	 * @return the frequency of writes of a state to disk
	 */
	public int getSaveStatePeriod() {
		return saveStatePeriod;
	}

	/**
	 * getter for the number of old complete signed states to be kept on disk
	 *
	 * @return the number of old complete signed states to be kept on disk
	 */
	public int getSignedStateDisk() {
		return signedStateDisk;
	}

	/**
	 * When logging debug information about the hashes in a merkle tree, do not display hash information
	 * for nodes deeper than this.
	 *
	 * @return the maximum depth when displaying debug information about the hash of the state
	 */
	public static int getDebugHashDepth() {
		return debugHashDepth;
	}

	/**
	 * Returns the oldest round that is non-ancient. If no round is ancient, then it will return the first round ever
	 *
	 * @param lastRoundDecided
	 * 		the last round that has fame decided
	 * @return oldest non-ancient number
	 */
	public long getOldestNonAncientRound(final long lastRoundDecided) {
		// if we have N non-ancient consensus rounds, and the last one is M, then the oldest non-ancient round is
		// M-(N-1) which is equal to M-N+1
		// if no rounds are ancient yet, then the oldest non-ancient round is the first round ever
		return Math.max(lastRoundDecided - roundsNonAncient + 1, EventConstants.MINIMUM_ROUND_CREATED);
	}

	/**
	 * Returns the minimum generation below which all events are ancient
	 *
	 * @param lastRoundDecided
	 * 		the last round that has fame decided
	 * @param roundGenerationProvider
	 * 		returns a round generation number for a given round number
	 * @return minimum non-ancient generation
	 */
	public long getMinGenNonAncient(final long lastRoundDecided, final LongUnaryOperator roundGenerationProvider) {
		// if a round generation is not defined for the oldest round, it will be EventConstants.GENERATION_UNDEFINED,
		// which is -1. in this case we will return FIRST_GENERATION, which is 0
		return Math.max(
				roundGenerationProvider.applyAsLong(getOldestNonAncientRound(lastRoundDecided)),
				GraphGenerations.FIRST_GENERATION);
	}
}
