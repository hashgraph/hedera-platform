/*
 * (c) 2016-2022 Swirlds, Inc.
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

import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.internal.SubSetting;

import java.util.function.LongUnaryOperator;

/**
 * Settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors.
 */
public class StateSettings extends SubSetting {

	/**
	 * The frequency of writes of a state to disk every this many seconds (0 to never write).
	 */
	public int saveStatePeriod = 0;

	/**
	 * Keep at least this many of the old complete signed states.
	 * (1 to keep only the most recent, 0 to not sign states)
	 */
	public int signedStateKeep = 10; // 10 is good for signedStateFreq==1, or <10 if it's >1

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
	 * Enabling the process of recovering signed state by playing back event files
	 */
	public boolean enableStateRecovery = false;

	/**
	 * If true, save the state to disk when an ISS is detected. May negatively effect the performance
	 * of the node where the ISS occurs.
	 *
	 * This feature is for debugging purposes and should not be active in production systems.
	 */
	public boolean dumpStateOnISS = false;

	/**
	 * If true, a snapshot of all events will be saved after every round. This can be used in conjunction with
	 * dumpStateOnISS so that an ISS state will be saved with events.
	 */
	public boolean saveEventsForEveryState = false;

	/**
	 * If one ISS is detected, it is likely that others will be detected shortly afterwards. Specify the minimum
	 * time, in seconds, that must transpire after dumping a state before another state dump is permitted. Ignored
	 * if dumpStateOnISS is false.
	 */
	public double secondsBetweenISSDumps = 60;

	/**
	 * Whether to generate the file LocalEvents.evn or not.
	 * This file is generated every time a SignedState is saved.
	 */
	public boolean saveLocalEvents = false;

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

	public StateSettings() {

	}

	public StateSettings(int saveStatePeriod, int signedStateKeep, int signedStateDisk, int roundsNonAncient) {
		this.saveStatePeriod = saveStatePeriod;
		this.signedStateKeep = signedStateKeep;
		this.signedStateDisk = signedStateDisk;
		this.roundsNonAncient = roundsNonAncient;
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
	 * getter for the number of old complete signed states to be kept
	 *
	 * @return number of old complete signed states
	 */
	public int getSignedStateKeep() {
		return signedStateKeep;
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
	 * getter if recovering signed state by playing back event files is enabled
	 *
	 * @return true if state recovery is enabled
	 */
	public boolean isEnableStateRecovery() {
		return enableStateRecovery;
	}

	/**
	 * setter for enabling signed state recovery by playing back event files
	 *
	 * @param enableStateRecovery
	 * 		if true then state recovery will be enabled, if false then it will be disabled
	 */
	public void setEnableStateRecovery(boolean enableStateRecovery) {
		this.enableStateRecovery = enableStateRecovery;
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
