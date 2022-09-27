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

package com.swirlds.common.test.sequence;

import com.swirlds.common.sequence.map.ConcurrentSequenceMap;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.common.test.AssertionUtils;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.swirlds.common.test.AssertionUtils.completeBeforeTimeout;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Sequence Map Tests")
public class SequenceMapTests {


	private record SequenceMapKey(int key, long sequence) {
		@Override
		public int hashCode() {
			return key;
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj instanceof final SequenceMapKey that) {
				return this.key == that.key;
			}
			return false;
		}

		@Override
		public String toString() {
			return key + "[" + sequence + "]";
		}
	}

	private record MapBuilder(String name, BiFunction<Long, Long, SequenceMap<SequenceMapKey, Integer>> constructor) {
		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Arguments> testConfiguration() {
		return Stream.of(
				Arguments.of(new MapBuilder("standard",
						(min, max) -> new StandardSequenceMap<>(min, max, SequenceMapKey::sequence))),
				Arguments.of(new MapBuilder("concurrent",
						(min, max) -> new ConcurrentSequenceMap<>(min, max, SequenceMapKey::sequence)))
		);
	}

	private static boolean isKeyPresent(final SequenceMap<SequenceMapKey, Integer> map, final Long sequenceNumber) {
		return sequenceNumber != null
				&& sequenceNumber >= map.getLowestAllowedSequenceNumber()
				&& sequenceNumber <= map.getHighestAllowedSequenceNumber();
	}

	/**
	 * Do validation on a map.
	 *
	 * @param map
	 * 		the map being validated
	 * @param smallestKeyToCheck
	 * 		the smallest key to check
	 * @param keyToCheckUpperBound
	 * 		the upper bound (exclusive) of keys to check
	 * @param getSequenceNumber
	 * 		provides the expected sequence number for a key, or null if the key is not expected to be in the map
	 * @param getValue
	 * 		provides the expected value for a key (result is ignored if sequence number is reported as null or the
	 * 		sequence number falls outside the map's bounds)
	 */
	private void validateMapContents(
			final SequenceMap<SequenceMapKey, Integer> map,
			final int smallestKeyToCheck,
			final int keyToCheckUpperBound,
			final Function<Integer, Long> getSequenceNumber,
			final Function<Integer, Integer> getValue) {

		final Map<Long, Set<Integer>> keysBySequenceNumber = new HashMap<>();
		long smallestSequenceNumber = Long.MAX_VALUE;
		long largestSequenceNumber = Long.MIN_VALUE;
		int size = 0;

		// Query by key
		for (int key = smallestKeyToCheck; key < keyToCheckUpperBound; key++) {
			final Long sequenceNumber = getSequenceNumber.apply(key);

			if (isKeyPresent(map, sequenceNumber)) {
				assertEquals(getValue.apply(key), map.get(new SequenceMapKey(key, sequenceNumber)),
						"unexpected value");

				assertTrue(map.containsKey(new SequenceMapKey(key, getSequenceNumber.apply(key))),
						"should contain key");

				keysBySequenceNumber.computeIfAbsent(sequenceNumber, k -> new HashSet<>()).add(key);
				smallestSequenceNumber = Math.min(smallestSequenceNumber, sequenceNumber);
				largestSequenceNumber = Math.max(largestSequenceNumber, sequenceNumber);

				size++;

			} else {
				// Note: the sequence number in the key is unused when we are just querying. So it's
				// ok to lie and provide a sequence number of 0 here, even though 0 may not be the
				// correct sequence number for the given key.
				assertNull(map.get(new SequenceMapKey(key, 0)), "unexpected value");
				assertFalse(map.containsKey(new SequenceMapKey(key, 0)),
						"should not contain key");
			}
		}

		assertEquals(size, map.getSize(), "unexpected map size");
		if (size == 0) {
			// For the sake of sanity, we don't want to attempt to use the default values for these
			// variables under any conditions.
			smallestSequenceNumber = map.getLowestAllowedSequenceNumber();
			largestSequenceNumber = map.getHighestAllowedSequenceNumber();
		}


		// Query by sequence number
		// Start at 100 sequence numbers below the minimum, and query to 100 sequence numbers beyond the maximum
		for (long sequenceNumber = smallestSequenceNumber - 100;
			 sequenceNumber < largestSequenceNumber + 100;
			 sequenceNumber++) {


			final Set<Integer> expectedKeys = keysBySequenceNumber.get(sequenceNumber);
			if (expectedKeys == null) {
				assertTrue(map.getKeysWithSequenceNumber(sequenceNumber).isEmpty(),
						"map should not contain any keys");
				assertTrue(map.getEntriesWithSequenceNumber(sequenceNumber).isEmpty(),
						"map should not contain any entries");
			} else {
				final List<SequenceMapKey> keys = map.getKeysWithSequenceNumber(sequenceNumber);
				assertEquals(expectedKeys.size(), keys.size(),
						"unexpected number of keys returned");
				for (final SequenceMapKey key : keys) {
					assertTrue(expectedKeys.contains(key.key), "key not in expected set");
					assertEquals(getSequenceNumber.apply(key.key), key.sequence, "unexpected sequence number");
				}

				final List<Map.Entry<SequenceMapKey, Integer>> entries = map.getEntriesWithSequenceNumber(
						sequenceNumber);
				assertEquals(expectedKeys.size(), entries.size(),
						"unexpected number of entries returned");
				for (final Map.Entry<SequenceMapKey, Integer> entry : entries) {
					assertTrue(expectedKeys.contains(entry.getKey().key), "key not in expected set");
					assertEquals(getSequenceNumber.apply(entry.getKey().key), entry.getKey().sequence,
							"unexpected sequence number");
					assertEquals(getValue.apply(entry.getKey().key), entry.getValue(),
							"unexpected value");
				}
			}
		}
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("Simple Access Test")
	void simpleAccessTest(final MapBuilder mapBuilder) {
		final SequenceMap<SequenceMapKey, Integer> map = mapBuilder.constructor.apply(0L, Long.MAX_VALUE);

		// The number of things inserted into the map
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		for (int i = 0; i < size; i++) {
			map.put(new SequenceMapKey(i, i / keysPerSeq), -i);
			assertEquals(i + 1, map.getSize(), "unexpected size");
		}

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);
	}


	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("purge() Test")
	void purgeTest(final MapBuilder mapBuilder) {
		final SequenceMap<SequenceMapKey, Integer> map = mapBuilder.constructor.apply(0L, Long.MAX_VALUE);
		assertEquals(0, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");

		// The number of things inserted into the map
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		for (int i = 0; i < size; i++) {
			map.put(new SequenceMapKey(i, i / keysPerSeq), -i);
			assertEquals(i + 1, map.getSize(), "unexpected size");
		}

		map.purge(size / 2 / keysPerSeq);
		assertEquals(size / 2 / keysPerSeq, map.getLowestAllowedSequenceNumber(),
				"unexpected lower bound");
		assertEquals(Long.MAX_VALUE, map.getHighestAllowedSequenceNumber(), "unexpected upper bound");
		assertEquals(size / 2, map.getSize(), "unexpected size");

		validateMapContents(map, 0, 2 * size,
				key -> {
					if (key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("purge() With Callback Test")
	void purgeWithCallbackTest(final MapBuilder mapBuilder) {
		final SequenceMap<SequenceMapKey, Integer> map = mapBuilder.constructor.apply(0L, Long.MAX_VALUE);
		assertEquals(0, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");

		// The number of things inserted into the map
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		for (int i = 0; i < size; i++) {
			map.put(new SequenceMapKey(i, i / keysPerSeq), -i);
			assertEquals(i + 1, map.getSize(), "unexpected size");
		}

		final Set<Integer> purgedKeys = new HashSet<>();
		map.purge(size / 2 / keysPerSeq, (key, value) -> {
			assertTrue(key.sequence < size / 2 / keysPerSeq, "key should not be purged");
			assertEquals(key.key / keysPerSeq, key.sequence, "unexpected sequence number for key");
			assertEquals(value, -key.key, "unexpected value");
			assertTrue(purgedKeys.add(key.key), "callback should be invoked once per key");
		});

		assertEquals(size / 2, purgedKeys.size(), "unexpected number of keys purged");

		assertEquals(size / 2 / keysPerSeq, map.getLowestAllowedSequenceNumber(),
				"unexpected lower bound");
		assertEquals(size / 2, map.getSize(), "unexpected size");

		validateMapContents(map, 0, 2 * size,
				key -> {
					if (key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("Upper/Lower Bound Test")
	void upperLowerBoundTest(final MapBuilder mapBuilder) {
		// The number of things inserted into the map
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply(5L, 10L);

		assertEquals(5, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");
		assertEquals(10, map.getHighestAllowedSequenceNumber(), "unexpected upper bound");

		for (int i = 0; i < size; i++) {
			map.put(new SequenceMapKey(i, i / keysPerSeq), -i);
		}

		validateMapContents(map, 0, 2 * size,
				key -> (long) key / keysPerSeq,
				key -> -key);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("Shifting Window Test")
	void shiftingWindowTest(final MapBuilder mapBuilder) {
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		// The lowest permitted sequence number
		int lowerBound = 0;

		// The highest permitted sequence number
		int upperBound = size / keysPerSeq;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply((long) lowerBound, (long) upperBound);

		for (int iteration = 0; iteration < 10; iteration++) {
			if (iteration % 2 == 0) {
				// shift the lower bound
				lowerBound += size / 2 / keysPerSeq;
				map.purge(lowerBound);
			} else {
				// shift the upper bound
				upperBound += size / 2 / keysPerSeq;
				map.expand(upperBound);
			}

			assertEquals(lowerBound, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");
			assertEquals(upperBound, map.getHighestAllowedSequenceNumber(), "unexpected upper bound");

			// Add a bunch of values. Values outside the window should be ignored.
			for (int i = lowerBound * keysPerSeq - 100; i < upperBound * keysPerSeq + 100; i++) {
				map.put(new SequenceMapKey(i, i / keysPerSeq), -i);
			}

			validateMapContents(map, 0, upperBound * keysPerSeq + size,
					key -> {
						if (key >= 0) {
							return (long) key / keysPerSeq;
						} else {
							// key is not present
							return null;
						}
					},
					key -> -key);
		}
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("clear() Test")
	void clearTest(final MapBuilder mapBuilder) {

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		// The lowest permitted sequence number
		final int lowerBound = 50;

		// The highest permitted sequence number
		final int upperBound = 100;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply((long) lowerBound, (long) upperBound);

		assertEquals(lowerBound, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");
		assertEquals(upperBound, map.getHighestAllowedSequenceNumber(), "unexpected upper bound");

		for (int i = 0; i < upperBound * keysPerSeq + 100; i++) {
			map.put(new SequenceMapKey(i, i / 5), -i);
		}

		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> (long) key / keysPerSeq,
				key -> -key);

		// Shift the window.
		final int newLowerBound = lowerBound + 10;
		final int newUpperBound = upperBound + 10;
		map.purge(newLowerBound);
		map.expand(newUpperBound);

		assertEquals(newLowerBound, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");
		assertEquals(newUpperBound, map.getHighestAllowedSequenceNumber(), "unexpected upper bound");

		for (int i = 0; i < newUpperBound * keysPerSeq + 100; i++) {
			map.put(new SequenceMapKey(i, i / 5), -i);
		}

		validateMapContents(map, 0, newUpperBound * keysPerSeq + 100,
				key -> (long) key / keysPerSeq,
				key -> -key);

		map.clear();

		// should revert to original bounds
		assertEquals(lowerBound, map.getLowestAllowedSequenceNumber(), "unexpected lower bound");
		assertEquals(upperBound, map.getHighestAllowedSequenceNumber(), "unexpected upper bound");

		assertEquals(0, map.getSize(), "map should be empty");

		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> null, // no values permitted
				key -> -key);

		// Reinserting values should work the same way as when the map was "fresh"
		for (int i = 0; i < upperBound * keysPerSeq + 100; i++) {
			map.put(new SequenceMapKey(i, i / 5), -i);
		}

		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> (long) key / keysPerSeq,
				key -> -key);
	}


	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("remove() Test")
	void removeTest(final MapBuilder mapBuilder) {
		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		// The lowest permitted sequence number
		final int lowerBound = 0;

		// The highest permitted sequence number
		final int upperBound = 100;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply((long) lowerBound, (long) upperBound);

		// removing values from an empty map shouldn't cause problems
		assertNull(map.remove(new SequenceMapKey(-100, 0)), "value should not be in map");
		assertNull(map.remove(new SequenceMapKey(0, 0)), "value should not be in map");
		assertNull(map.remove(new SequenceMapKey(50, 0)), "value should not be in map");
		assertNull(map.remove(new SequenceMapKey(100, 0)), "value should not be in map");

		// Validate removal of an existing value
		assertEquals(0, map.getSize(), "map should be empty");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> null, // no values should be present
				key -> -key);

		map.put(new SequenceMapKey(10, 2), -10);
		assertEquals(-10, map.remove(new SequenceMapKey(10, 2)), "unexpected value");

		assertEquals(0, map.getSize(), "map should be empty");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> null, // no values should be present
				key -> -key);

		// Re-inserting after removal should work the same as regular insertion
		map.put(new SequenceMapKey(10, 2), -10);
		assertEquals(-10, map.get(new SequenceMapKey(10, 2)), "unexpected value");

		assertEquals(1, map.getSize(), "map should contain one thing");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> -key);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("Replacing put() Test")
	void replacingPutTest(final MapBuilder mapBuilder) {
		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		// The lowest permitted sequence number
		final int lowerBound = 0;

		// The highest permitted sequence number
		final int upperBound = 100;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply((long) lowerBound, (long) upperBound);

		assertNull(map.put(new SequenceMapKey(10, 2), -10), "no value should currently be in map");

		assertEquals(1, map.getSize(), "map should contain one thing");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> -key);

		assertEquals(-10, map.put(new SequenceMapKey(10, 2), 1234),
				"previous value should be returned");

		assertEquals(1, map.getSize(), "map should contain one thing");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> 1234);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("computeIfAbsent() Test")
	void computeIfAbsentTest(final MapBuilder mapBuilder) {
		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		// The lowest permitted sequence number
		final int lowerBound = 0;

		// The highest permitted sequence number
		final int upperBound = 100;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply((long) lowerBound, (long) upperBound);

		// Value that is in a legal range and not present
		assertEquals(-10, map.computeIfAbsent(new SequenceMapKey(10, 2), key -> -key.key),
				"incorrect value returned");

		assertEquals(1, map.getSize(), "unexpected size");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> -key);

		// Values that are in an illegal range
		assertNull(map.computeIfAbsent(new SequenceMapKey(-1000, -1000), key -> -key.key),
				"incorrect value returned");
		assertNull(map.computeIfAbsent(new SequenceMapKey(1000, 1000), key -> -key.key),
				"incorrect value returned");

		assertEquals(1, map.getSize(), "unexpected size");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> -key);

		// Value that is already present
		map.put(new SequenceMapKey(20, 4), -20);
		assertEquals(-20, map.computeIfAbsent(new SequenceMapKey(20, 2), key -> {
					fail("should never be called");
					return null;
				}),
				"incorrect value returned");

		assertEquals(2, map.getSize(), "unexpected size");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else if (key == 20) {
						return 4L;
					} else {
						return null;
					}
				},
				key -> -key);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("putIfAbsent() Test")
	void putIfAbsentTest(final MapBuilder mapBuilder) {
		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		// The lowest permitted sequence number
		final int lowerBound = 0;

		// The highest permitted sequence number
		final int upperBound = 100;

		final SequenceMap<SequenceMapKey, Integer> map =
				mapBuilder.constructor.apply((long) lowerBound, (long) upperBound);

		// Value that is in a legal range and not present
		assertNull(map.putIfAbsent(new SequenceMapKey(10, 2), -10),
				"value is not yet in the map");

		assertEquals(1, map.getSize(), "unexpected size");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> -key);

		// Values that are in an illegal range
		assertNull(map.putIfAbsent(new SequenceMapKey(-1000, -1000), 1000),
				"incorrect value returned");
		assertNull(map.putIfAbsent(new SequenceMapKey(1000, 1000), -1000),
				"incorrect value returned");

		assertEquals(1, map.getSize(), "unexpected size");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else {
						return null;
					}
				},
				key -> -key);

		// Value that is already present
		map.put(new SequenceMapKey(20, 4), -20);
		assertEquals(-20, map.putIfAbsent(new SequenceMapKey(20, 2), 1234),
				"incorrect value returned");

		assertEquals(2, map.getSize(), "unexpected size");
		validateMapContents(map, 0, upperBound * keysPerSeq + 100,
				key -> {
					if (key == 10) {
						return 2L;
					} else if (key == 20) {
						return 4L;
					} else {
						return null;
					}
				},
				key -> -key);
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("removeSequenceNumber() Test")
	void removeSequenceNumberTest(final MapBuilder mapBuilder) {
		final SequenceMap<SequenceMapKey, Integer> map = mapBuilder.constructor.apply(0L, Long.MAX_VALUE);

		// The number of things inserted into the map
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		for (int i = 0; i < size; i++) {
			map.put(new SequenceMapKey(i, i / 5), -i);
			assertEquals(i + 1, map.getSize(), "unexpected size");
		}

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// Remove sequence numbers that are not in the map
		map.removeSequenceNumber(-1000);
		map.removeSequenceNumber(1000);
		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// Remove a sequence number that is in the map
		map.removeSequenceNumber(1);

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						final long sequenceNumber = key / keysPerSeq;
						if (sequenceNumber == 1) {
							return null;
						}
						return sequenceNumber;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// Removing the same sequence number a second time shouldn't have any ill effects
		map.removeSequenceNumber(1);

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						final long sequenceNumber = key / keysPerSeq;
						if (sequenceNumber == 1) {
							return null;
						}
						return sequenceNumber;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// It should be ok to re-insert into the removed sequence number
		map.put(new SequenceMapKey(5, 1), 5);
		map.put(new SequenceMapKey(6, 1), 6);
		map.put(new SequenceMapKey(7, 1), 7);
		map.put(new SequenceMapKey(8, 1), 8);
		map.put(new SequenceMapKey(9, 1), 9);

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> {
					final long sequenceNumber = key / keysPerSeq;
					if (sequenceNumber == 1) {
						return key;
					}
					return -key;
				});
	}

	@ParameterizedTest
	@MethodSource("testConfiguration")
	@DisplayName("removeSequenceNumber() With Callback Test")
	void removeSequenceNumberWithCallbackTest(final MapBuilder mapBuilder) {
		final SequenceMap<SequenceMapKey, Integer> map = mapBuilder.constructor.apply(0L, Long.MAX_VALUE);

		// The number of things inserted into the map
		final int size = 100;

		// The number of keys for each sequence number
		final int keysPerSeq = 5;

		for (int i = 0; i < size; i++) {
			map.put(new SequenceMapKey(i, i / 5), -i);
			assertEquals(i + 1, map.getSize(), "unexpected size");
		}

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// Remove sequence numbers that are not in the map
		map.removeSequenceNumber(-1000, (key, value) -> fail("should not be called"));
		map.removeSequenceNumber(1000, (key, value) -> fail("should not be called"));
		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// Remove a sequence number that is in the map
		final Set<Integer> removedKeys = new HashSet<>();
		map.removeSequenceNumber(1, (key, value) -> {
			assertEquals(1, key.sequence, "key should not be removed");
			assertEquals(key.key / keysPerSeq, key.sequence, "unexpected sequence number for key");
			assertEquals(value, -key.key, "unexpected value");
			assertTrue(removedKeys.add(key.key), "callback should be invoked once per key");
		});
		assertEquals(5, removedKeys.size(), "unexpected number of keys removed");

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						final long sequenceNumber = key / keysPerSeq;
						if (sequenceNumber == 1) {
							return null;
						}
						return sequenceNumber;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// Removing the same sequence number a second time shouldn't have any ill effects
		map.removeSequenceNumber(1, (key, value) -> fail("should not be called"));

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						final long sequenceNumber = key / keysPerSeq;
						if (sequenceNumber == 1) {
							return null;
						}
						return sequenceNumber;
					} else {
						// key is not present
						return null;
					}
				},
				key -> -key);

		// It should be ok to re-insert into the removed sequence number
		map.put(new SequenceMapKey(5, 1), 5);
		map.put(new SequenceMapKey(6, 1), 6);
		map.put(new SequenceMapKey(7, 1), 7);
		map.put(new SequenceMapKey(8, 1), 8);
		map.put(new SequenceMapKey(9, 1), 9);

		validateMapContents(map, -size, 2 * size,
				key -> {
					if (key >= 0 && key < size) {
						return (long) key / keysPerSeq;
					} else {
						// key is not present
						return null;
					}
				},
				key -> {
					final long sequenceNumber = key / keysPerSeq;
					if (sequenceNumber == 1) {
						return key;
					}
					return -key;
				});
	}

	@Test
	@DisplayName("Parallel SequenceMap Test")
	void parallelSequenceMapTest() throws InterruptedException {
		final Random random = new Random();

		final AtomicInteger lowerBound = new AtomicInteger(0);
		final AtomicInteger upperBound = new AtomicInteger(100);

		final SequenceMap<SequenceMapKey, Integer> map = new ConcurrentSequenceMap<>(
				lowerBound.get(),
				upperBound.get(),
				SequenceMapKey::sequence);

		final AtomicBoolean error = new AtomicBoolean();

		final StoppableThread purgeThread = new StoppableThreadConfiguration<>()
				.setMinimumPeriod(Duration.ofMillis(10))
				.setExceptionHandler((t, e) -> {
					e.printStackTrace();
					error.set(true);
				})
				.setWork(() -> {

					// Verify that no data is present that should not be present
					for (int sequenceNumber = lowerBound.get() - 100;
						 sequenceNumber < upperBound.get() + 100;
						 sequenceNumber++) {

						if (sequenceNumber < lowerBound.get() || sequenceNumber > upperBound.get()) {
							final List<SequenceMapKey> keys = map.getKeysWithSequenceNumber(sequenceNumber);
							assertEquals(0, keys.size(), "no keys should be present for this round");
						}
					}

					// shift the window
					lowerBound.getAndAdd(5);
					upperBound.getAndAdd(5);
					map.purge(lowerBound.get());
					map.expand(upperBound.get());
				})
				.build(true);

		final int threadCount = 4;
		final List<StoppableThread> updaterThreads = new LinkedList<>();
		for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
			updaterThreads.add(new StoppableThreadConfiguration<>()
					.setExceptionHandler((t, e) -> {
						e.printStackTrace();
						error.set(true);
					})
					.setWork(() -> {

						final double choice = random.nextDouble();
						final int sequenceNumber =
								random.nextInt(lowerBound.get() - 50, upperBound.get() + 50);

						if (choice < 0.5) {
							// attempt to delete a value
							final int key = random.nextInt(sequenceNumber * 10, sequenceNumber * 10 + 10);
							map.remove(new SequenceMapKey(key, sequenceNumber));

						} else if (choice < 0.999) {
							// insert/replace a value
							final int key = random.nextInt(sequenceNumber * 10, sequenceNumber * 10 + 10);
							final int value = random.nextInt();
							map.put(new SequenceMapKey(key, sequenceNumber), value);

						} else {
							// very rarely, delete an entire sequence number
							map.removeSequenceNumber(sequenceNumber);
						}
					})
					.build(true));
		}

		// Let the threads fight each other for a little while. At the end, tear everything down and make sure
		// our constraints weren't violated.
		SECONDS.sleep(2);
		purgeThread.stop();
		updaterThreads.forEach(Stoppable::stop);

		completeBeforeTimeout(() -> purgeThread.join(), Duration.ofSeconds(1),
				"thread did not die on time");
		updaterThreads.forEach(thread -> {
			try {
				completeBeforeTimeout(() -> thread.join(), Duration.ofSeconds(1),
						"thread did not die on time");
			} catch (InterruptedException e) {
				fail(e);
			}
		});

		assertFalse(error.get(), "error(s) encountered");
	}
}
