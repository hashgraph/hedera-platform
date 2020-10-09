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

import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.internal.SubSetting;

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
	// TODO (06 October 2020) Restore from parent branch.
	public int roundsExpired = 15;

	/**
	 * Events this many rounds old are ancient, so if they don't have consensus yet, they're stale, and will
	 * never have their transactions handled.
	 */
	// TODO (06 October 2020) Restore from parent branch.
	public int roundsStale = 8;

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
	 * If one ISS is detected, it is likely that others will be detected shortly afterwards. Specify the minimum
	 * time, in seconds, that must transpire after dumping a state before another state dump is permitted. Ignored
	 * if dumpStateOnISS is false.
	 */
	public double secondsBetweenISSDumps = 60;

	/**
	 * Used for debugging the ISS that appears due to concurrent modification of the SignedStateLeaf
	 */
	public static volatile boolean compareSSLeafSnapshots = true;

	public StateSettings() {

	}

	public StateSettings(int saveStatePeriod, int signedStateKeep, int signedStateDisk, int roundsStale) {
		this.saveStatePeriod = saveStatePeriod;
		this.signedStateKeep = signedStateKeep;
		this.signedStateDisk = signedStateDisk;
		this.roundsStale = roundsStale;
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
	 * getter for number of rounds that are old are stale in events and will never have their transactions handled
	 *
	 * @return number of rounds that are old are stale
	 */
	public int getRoundsStale() {
		return roundsStale;
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
	 */
	public void setEnableStateRecovery(boolean enableStateRecovery) {
		this.enableStateRecovery = enableStateRecovery;
	}
}
