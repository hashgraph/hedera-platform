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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CountDownLatchStreamTest {

	@Test
	void countDownLatchStreamTest() {
		CountDownLatch countDownLatch = mock(CountDownLatch.class);
		final int expectedCount = 10;
		CountDownLatchStream<ObjectForTestStream> stream = new CountDownLatchStream<>(countDownLatch, expectedCount);
		for (int i = 0; i < expectedCount; i++) {
			// when the stream haven't received enough objects, countDown() is not called
			verify(countDownLatch, never()).countDown();
			stream.addObject(ObjectForTestStream.getRandomObjectForTestStream(4));
		}
		// only when the stream have received enough objects, countDown() is called
		verify(countDownLatch).countDown();
	}
}
