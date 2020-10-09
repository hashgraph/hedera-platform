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

package com.swirlds.platform.event;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.Hash;
import com.swirlds.platform.SyncUtils;
import com.swirlds.platform.internal.CreatorSeqPair;

import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Temporary class until we remove events from state
 */
public class AbbreviatedStateEvent {
	public static EventImpl readAbbreviatedConsensusEvent(DataInputStream dis,
			Map<CreatorSeqPair, EventImpl> eventsByCreatorSeq)
			throws IOException {
		int[] byteCount = new int[1];

		// info sent during a normal sync
		long creatorId = dis.readLong();
		long creatorSeq = dis.readLong();
		long otherId = dis.readLong();
		long otherSeq = dis.readLong();

		Instant timeCreated = SyncUtils.readInstant(dis, byteCount);
		byte[] signature = SyncUtils.readByteArray(dis, byteCount, SignatureType.getMaxLength());

		// other info
		Hash hash = Hash.readHash(dis);

		creatorSeq = dis.readLong();
		Instant timeReceived = SyncUtils.readInstant(dis, byteCount);
		long generation = dis.readLong();
		long roundCreated = dis.readLong();

		boolean isWitness = dis.readBoolean();
		boolean isFameDecided = dis.readBoolean();
		boolean isFamous = dis.readBoolean();
		boolean isConsensus = dis.readBoolean();

		Instant consensusTimestamp = SyncUtils.readInstant(dis, byteCount);

		long roundReceived = dis.readLong();
		long consensusOrder = dis.readLong();
		boolean lastInRoundReceived = dis.readBoolean();

		long selfParentGen = -1;
		long otherParentGen = -1;
		com.swirlds.common.crypto.Hash selfParentHash = null;
		com.swirlds.common.crypto.Hash otherParentHash = null;

		EventImpl selfParent = null;
		EventImpl otherParent = null;
		if (eventsByCreatorSeq != null) {
			// find the parents, if they exist
			selfParent = eventsByCreatorSeq.get(new CreatorSeqPair(creatorId, creatorSeq - 1));
			otherParent = eventsByCreatorSeq.get(new CreatorSeqPair(otherId, otherSeq));
		}

		BaseEventHashedData hashedData = new BaseEventHashedData(
				creatorId,
				selfParentGen,
				otherParentGen,
				selfParentHash,
				otherParentHash,
				timeCreated,
				null);
		hashedData.setHash(new com.swirlds.common.crypto.Hash(hash.getValue()));
		BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
				creatorSeq,
				otherId,
				otherSeq,
				signature);

		EventImpl event = new EventImpl(hashedData, unhashedData, selfParent, otherParent);

		event.setTimeReceived(timeReceived);
		event.setGeneration(generation);
		event.setRoundCreated(roundCreated);
		event.setWitness(isWitness);
		event.setFameDecided(isFameDecided);
		event.setFamous(isFamous);
		event.setConsensus(isConsensus);
		event.setConsensusTimestamp(consensusTimestamp);
		event.setRoundReceived(roundReceived);
		event.setConsensusOrder(consensusOrder);
		event.setLastInRoundReceived(lastInRoundReceived);
		event.setAbbreviatedStateEvent(true);

		return event;
	}
}
