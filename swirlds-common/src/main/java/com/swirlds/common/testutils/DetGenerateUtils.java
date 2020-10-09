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

package com.swirlds.common.testutils;

import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.common.events.ConsensusData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A class that has methods to deterministically generate objects
 */
public abstract class DetGenerateUtils {
	private static final DigestType DEFAULT_HASH_TYPE = DigestType.SHA_384;
	private static final long DEFAULT_MAX_EPOCH = 1583243881;
	private static final int DEFAULT_TRANSACTION_NUMBER = 10;
	private static final int DEFAULT_TRANSACTION_MAX_SIZE = 100;
	private static final int DEFAULT_SIGNATURE_SIZE = 384;
	private static final int DEFAULT_ROUND_MAX_DIFF = 10;

//	public static BaseEventHashedData generateBaseEventHashedData(Random random, long selfParentGeneration, long
//	otherParentGeneration) {
//		return new BaseEventHashedData(
//				nextLong(random, 0), // creatorId, must be positive
//				selfParentGeneration, // selfParentGen, must be positive
//				otherParentGeneration, // otherParentGen, must be positive
//				generateRandomHash(random, DEFAULT_HASH_TYPE), // selfParentHash
//				generateRandomHash(random, DEFAULT_HASH_TYPE), // otherParentHash
//				generateRandomInstant(random, DEFAULT_MAX_EPOCH), // timeCreated
//				generateTransactions(DEFAULT_TRANSACTION_NUMBER, DEFAULT_TRANSACTION_MAX_SIZE, random)
//						.toArray(new Transaction[0])); // transactions
//	}

	public static BaseEventHashedData generateBaseEventHashedData(Random random) {
		return new BaseEventHashedData(
				nextLong(random, 0), // creatorId, must be positive
				nextLong(random, 0), // selfParentGen, must be positive
				nextLong(random, 0), // otherParentGen, must be positive
				generateRandomHash(random, DEFAULT_HASH_TYPE), // selfParentHash
				generateRandomHash(random, DEFAULT_HASH_TYPE), // otherParentHash
				generateRandomInstant(random, DEFAULT_MAX_EPOCH), // timeCreated
				generateTransactions(DEFAULT_TRANSACTION_NUMBER, DEFAULT_TRANSACTION_MAX_SIZE, random)
						.toArray(new Transaction[0])); // transactions
	}

	public static BaseEventUnhashedData generateBaseEventUnhashedData(Random random) {
		return new BaseEventUnhashedData(
				nextLong(random, 0), // creatorSeq, must be positive
				nextLong(random, 0), // otherId, must be positive
				nextLong(random, 0), // otherSeq, must be positive
				generateRandomByteArray(random, DEFAULT_SIGNATURE_SIZE)); // signature
	}

	public static ConsensusData generateConsensusEventData(Random random) {
		ConsensusData data = new ConsensusData();

		data.setGeneration(nextLong(random, 0));
		data.setRoundCreated(nextLong(random, 0));
		data.setWitness(random.nextBoolean());
		data.setFamous(random.nextBoolean());
		data.setStale(random.nextBoolean());
		data.setConsensusTimestamp(generateRandomInstant(random, DEFAULT_MAX_EPOCH));
		data.setRoundReceived(nextLong(random,
				data.getRoundCreated() + 1, //RoundReceived must be higher than RoundCreated
				data.getRoundCreated() + DEFAULT_ROUND_MAX_DIFF));
		data.setConsensusOrder(nextLong(random, 0));

		return data;
	}

	public static List<Transaction> generateTransactions(int number, int maxSize, Random random) {
		List<Transaction> list = new ArrayList<>(number);
		for (int i = 0; i < number; i++) {
			int size = Math.max(1, random.nextInt(maxSize));
			byte[] bytes = new byte[size];
			random.nextBytes(bytes);
			boolean system = random.nextBoolean();
			list.add(new Transaction(bytes, system));
		}
		return list;
	}

	public static Hash generateRandomHash(Random random, DigestType type) {
		return new Hash(generateRandomByteArray(random, type.digestLength()), type);
	}

	public static Instant generateRandomInstant(Random random, long maxEpoch) {
		return Instant.ofEpochSecond(
				nextLong(random, 0, maxEpoch - 1),
				nextLong(random, 0, 1_000_000_000)
		);
	}

	public static long nextLong(Random random, long min) {
		return nextLong(random, min, Long.MAX_VALUE);
	}

	public static long nextLong(Random random, long min, long max) {
		return random.longs(1, min, max).findFirst().orElseThrow();
	}

	public static byte[] generateRandomByteArray(Random random, int size) {
		byte[] bytes = new byte[size];
		random.nextBytes(bytes);
		return bytes;
	}
}
