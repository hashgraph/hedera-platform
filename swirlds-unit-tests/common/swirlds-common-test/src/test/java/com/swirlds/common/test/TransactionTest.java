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

package com.swirlds.common.test;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.test.io.InputOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionTest {
	private static final int MAX_TRANSACTIONS = 100;
	private static final int MAX_TRANSACTION_BYTES = 1000;
	private static final int MAX_ADDRESSBOOK_SIZE = 2048;
	private static final int DEFAULT_SIGNATURE_SIZE = 384;

	@BeforeAll
	public static void setup() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructables("com.swirlds.common");
		ConstructableRegistry.registerConstructables("com.swirlds.common.system.transaction.internal");
		SettingsCommon.maxTransactionCountPerEvent = MAX_TRANSACTIONS;
		SettingsCommon.transactionMaxBytes = MAX_TRANSACTION_BYTES;
		SettingsCommon.maxAddressSizeAllowed = MAX_ADDRESSBOOK_SIZE;
	}

	/**
	 * Randomly generate a list of transaction object
	 *
	 * @param number
	 * 		how many transaction to generate
	 * @param maxSize
	 * 		maxiumyum payload size a transaction could have
	 * @param random
	 * 		random seed generator
	 * @return a list of transaction objects
	 *
	 * 		Note: This routine is copied directly from
	 * 		./swirlds-platform-core/src/test/java/com/swirlds/platform/event/DetGenerateUtils.java .
	 * 		Please keep the two versions in sync.
	 */
	public static List<Transaction> generateTransactions(final int number, final int maxSize, final Random random) {
		final List<Transaction> list = new ArrayList<>(number);
		for (int i = 0; i < number; i++) {
			final int size = Math.max(1, random.nextInt(maxSize));
			final byte[] bytes = new byte[size];
			random.nextBytes(bytes);
			final boolean system = random.nextBoolean();
			if (system) {
				final byte[] sigature = new byte[DEFAULT_SIGNATURE_SIZE];
				random.nextBytes(sigature);
				list.add(new StateSignatureTransaction(random.nextLong(), sigature));
			} else {
				list.add(new SwirldTransaction(bytes));
			}
		}
		return list;
	}

	@Test
	public void serializeDeserialize() throws IOException {
		final Random random = getRandomPrintSeed();
		final List<Transaction> original = generateTransactions(MAX_TRANSACTIONS, MAX_TRANSACTION_BYTES, random);
		final InputOutputStream io = new InputOutputStream();

		io.getOutput().writeSerializableList(original, true, false);
		io.startReading();
		final List<Transaction> copy = io.getInput().readSerializableList(MAX_TRANSACTIONS);

		assertEquals(original, copy);
	}

	private Random getRandomPrintSeed() {
		final long seed = new Random().nextLong();
		System.out.println("Seed: " + seed);
		return new Random(seed);
	}

}
