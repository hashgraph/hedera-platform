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

package com.swirlds.common.test.stream;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.QueueThreadObjectStreamConfiguration;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.test.RandomUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueThreadObjectStreamTest {
	private static Cryptography cryptography;
	private static Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);
	private static WriteToStreamConsumer consumer;
	private static QueueThreadObjectStream<ObjectForTestStream> queueThread;
	private static RunningHashCalculatorForStream<ObjectForTestStream> runningHashCalculator;
	private static HashCalculatorForStream<ObjectForTestStream> hashCalculator;
	private final int intervalMs = 50;
	private final int totalNum = 50;
	private static Iterator<ObjectForTestStream> iterator;

	@BeforeAll
	static void init() {
		cryptography = mock(Cryptography.class);
		when(cryptography.digestSync(any(SelfSerializable.class))).thenReturn(RandomUtils.randomHash());
	}

	@BeforeEach
	void initLinkedObjectStreams() throws IOException {
		consumer = new WriteToStreamConsumer(
				new SerializableDataOutputStream(new BufferedOutputStream(new ByteArrayOutputStream())), initialHash);

		queueThread = new QueueThreadObjectStreamConfiguration<ObjectForTestStream>().setForwardTo(consumer).build();
		runningHashCalculator = new RunningHashCalculatorForStream<>(queueThread, cryptography);
		hashCalculator = new HashCalculatorForStream<>(runningHashCalculator, cryptography);
		hashCalculator.setRunningHash(initialHash);

		iterator = new ObjectForTestStreamGenerator(totalNum, intervalMs,
				Instant.now()).getIterator();

		assertTrue(queueThread.getQueue().isEmpty(), "the queue should be empty after initialized");
	}

	@Test
	void closeTest() {
		final int targetConsumedNum = 20;

		queueThread.start();

		int consumedNum = 0;
		while (consumedNum < targetConsumedNum) {
			consumedNum++;
			hashCalculator.addObject(iterator.next());
		}
		queueThread.close();

		ObjectForTestStream nextObject = iterator.next();

		assertTrue(consumer.isClosed, "consumer should also be closed");

		assertEquals(targetConsumedNum, consumer.consumedCount,
				"the number of objects the consumer have consumed should be the same as targetConsumedNum");
	}

	@Test
	void clearTest() {
		final int targetNumBeforeClear = 20;

		queueThread.start();

		int addedNum = 0;
		while (addedNum < targetNumBeforeClear) {
			addedNum++;
			hashCalculator.addObject(iterator.next());
		}
		// clear queueThread
		queueThread.clear();

		// continue consuming after clear
		while (iterator.hasNext()) {
			addedNum++;
			hashCalculator.addObject(iterator.next());
		}

		// close queueThread
		queueThread.stop();
		assertTrue(consumer.consumedCount <= addedNum,
				String.format("the number of objects the consumer have consumed should be less than or equal " +
								"to totalNum %d < %d",
						consumer.consumedCount,
						addedNum));
	}
}
