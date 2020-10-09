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

package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.Transaction;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.internal.CreatorSeqPair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

/**
 * A class used to hold information about an event that is yet to be created and the event itself when it is created
 */
class ValidateEventTask extends EventIntakeTask {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private final BaseEventHashedData hashedData;
	private final BaseEventUnhashedData unhashedData;

	private final CreatorSeqPair creatorSeqPair;


	/** indicates whether the event is valid or not, will be null until the validity can be determined */
	private volatile Boolean validEvent = null;

	public ValidateEventTask(BaseEventHashedData hashedData, BaseEventUnhashedData unhashedData) {
		this.hashedData = hashedData;
		this.unhashedData = unhashedData;

		creatorSeqPair = new CreatorSeqPair(hashedData.getCreatorId(), unhashedData.getCreatorSeq());
	}

	/**
	 * Constructs an object to hold the information about an event received from another node. If this constructor is
	 * used, newEvent should be false.
	 *
	 * @param otherId
	 * 		member who created the Event that is the other-parent
	 * @param creatorId
	 * 		id of event creator
	 * @param creatorSeq
	 * 		sequence number for this Event for this creator (they create 0 then 1 then 2...)
	 * @param otherSeq
	 * 		sequence number for the Event that is the other-parent (or -1 if none)
	 * @param transactions
	 * 		array of transactions in this Event (possibly zero length, but not null)
	 * @param timeCreated
	 * 		the Instant at which this Event was first created by its creator
	 * @param signature
	 * 		digital signature for this Event by its creator
	 * @param selfParentGen
	 * 		the claimed generation for the self parent
	 * @param otherParentGen
	 * 		the claimed generation for the other parent
	 * @param selfParentHash
	 * 		the hash of the self parent
	 * @param otherParentHash
	 * 		the hash of the other parent
	 */
	ValidateEventTask(long otherId, long creatorId, long creatorSeq, long otherSeq,
			Transaction[] transactions, Instant timeCreated, byte[] signature,
			long selfParentGen, long otherParentGen, byte[] selfParentHash, byte[] otherParentHash) {
		super();
		hashedData = new BaseEventHashedData(
				creatorId,
				selfParentGen,
				otherParentGen,
				selfParentHash,
				otherParentHash,
				timeCreated,
				transactions);
		unhashedData = new BaseEventUnhashedData(
				creatorSeq,
				otherId,
				otherSeq,
				signature);

		creatorSeqPair = new CreatorSeqPair(creatorId, creatorSeq);
	}

	/**
	 * Creates an EventInfo object with an already existing @{@link EventImpl} object
	 *
	 * @param event
	 */
	ValidateEventTask(EventImpl event) {
		hashedData = event.getBaseEventHashedData();
		unhashedData = event.getBaseEventUnhashedData();

		creatorSeqPair = new CreatorSeqPair(event.getCreatorId(), event.getCreatorSeq());

		this.setEvent(event);
		this.validEvent = true;
	}

	public BaseEventHashedData getHashedData() {
		return hashedData;
	}

	public BaseEventUnhashedData getUnhashedData() {
		return unhashedData;
	}

	/**
	 * Gets the validity of the event
	 *
	 * @param requester
	 * 		the EventInfo that depends on this valid check
	 * @return true if the event is valid, false otherwise
	 * @implNote requester is no longer used due to the change to move validation to single threaded model and need
	 * 		not wait to get validity of event. This will be re-evaluated in the future
	 */
	@Override
	public boolean isValidWait(EventIntakeTask requester) {
		return validEvent;
	}

	@Override
	public CreatorSeqPair getCreatorSeqPair() {
		return creatorSeqPair;
	}

	/**
	 * Creates a new event object from the info contained in this object
	 *
	 * @param selfParent
	 * 		the Event that is the self parent of this Event
	 * @param otherParent
	 * 		the Event that is the other parent of this Event
	 * @return a newly created event
	 */
	EventImpl createEventFromInfo(EventImpl selfParent, EventImpl otherParent) {
		return new EventImpl(hashedData, unhashedData, selfParent, otherParent);
	}


	/**
	 * Sets the event to null and the validity to false, releasing any threads waiting at getEventWait() or
	 * isValidWait()
	 */
	void setEventNull() {
		setEvent(null);
		setEventValidity(false);
	}

	/**
	 * Sets the event validity and releases any threads waiting on isValidWait()
	 *
	 * @param validity
	 * 		true if the event is valid, false otherwise
	 */
	void setEventValidity(boolean validity) {
		validEvent = validity;
	}

	@Override
	public boolean isSelfEvent() {
		return false;
	}

	public long getCreatorId() {
		return hashedData.getCreatorId();
	}

	long getOtherId() {
		return unhashedData.getOtherId();
	}

	long getCreatorSeq() {
		return unhashedData.getCreatorSeq();
	}

	long getOtherSeq() {
		return unhashedData.getOtherSeq();
	}

	Transaction[] getTransactions() {
		return hashedData.getTransactions();
	}

	Instant getTimeCreated() {
		return hashedData.getTimeCreated();
	}

	byte[] getSignature() {
		return unhashedData.getSignature();
	}

	long getSelfParentGen() {
		return hashedData.getSelfParentGen();
	}

	long getOtherParentGen() {
		return hashedData.getOtherParentGen();
	}

	public byte[] getSelfParentHash() {
		return hashedData.getSelfParentHash().getValue();
	}

	public Hash getSelfParentHashInstance() {
		return hashedData.getSelfParentHash();
	}

	public byte[] getOtherParentHash() {
		return hashedData.getOtherParentHash().getValue();
	}

	public Hash getOtherParentHashInstance() {
		return hashedData.getOtherParentHash();
	}

	boolean hasSelfParent() {
		return getCreatorSeq() != 0;
	}

	boolean hasOtherParent() {
		return getOtherId() != -1 && getOtherSeq() != -1;
	}

	@Override
	public String toString() {
		return "(" + getCreatorId() + "," + getCreatorSeq() + ")" +
				" other (" + getOtherId() + "," + getOtherSeq() + ")";
	}
}
