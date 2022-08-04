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

package com.swirlds.jasperdb.collections;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A wrapper for a LongList that has two modes, direct pass though or an overlaid cache buffer. Important, any changes
 * directly to the wrapped list while it is wrapped will cause this classes state to get out of sync.
 * <p>
 */
public class LongListBufferedWrapper extends LongList implements Closeable {

	private static final int PARALLELISM_THRESHOLD = 100_000;
	private static final long FLAG = (1L << 63);
	private static final long INCREMENT0 = 1L;
	private static final long MASK0 = 0xffffffffL;
	private static final long INCREMENT1 = (1L << 32);
	private static final long MASK1 = 0x7fffffff00000000L;


	/** The LongList we are wrapping and providing an overlay cache to */
	private final LongList wrappedLongList;

	class Overlay {

		/** Overlay cache, reference can be set to null if we are not currently using an overlay cache. */
		private final AtomicReference<ConcurrentHashMap<Long, Long>> cachedChanges = new AtomicReference<>();
		/**
		 * Indicates whether fresh changes should prefer being written directly to the wrappedLongList,
		 * even if the cache exists. The only time when this is true that a change will still update the
		 * overlay cache, is if the change is still in the cache and hasn't been written down to the
		 * wrappedLongList yet.
		 */
		volatile boolean skipCacheOnWrite = true;
		/**
		 * Operation barrier - a mechanism to count operations between switching state and ensure all
		 * current operations finish and all new operations see all changes preceding the switch.
		 */
		private final AtomicLong ops = new AtomicLong(0);

		private void flushOps() {
			long curValue = ops.get();
			for (;;) {
				final long oldValue = curValue;
				curValue = ops.compareAndExchange(curValue, curValue ^ FLAG);
				if (curValue == oldValue) break;
			}
			final long mask = (curValue & FLAG) == 0 ? MASK0 : MASK1;
			while ((curValue & mask) != 0) {
				curValue = ops.get();
			}
		}

		long acquire() {
			long curValue = ops.get();
			for (;;) {
				final long inc = (curValue & FLAG) == 0 ? INCREMENT0 : INCREMENT1;
				final long oldValue = curValue;
				curValue = ops.compareAndExchange(curValue, curValue + inc);
				if (curValue == oldValue) return inc;
			}
		}

		void release(long inc) {
			ops.addAndGet(-inc);
		}

		void start() {
			if (!skipCacheOnWrite) return;	// already started
			skipCacheOnWrite = false;
			cachedChanges.set(new ConcurrentHashMap<>());
			// Ensure all operations see cachedChanges != null
			flushOps();
		}

		void stop() {
			if (skipCacheOnWrite) return;	//already stopped
			skipCacheOnWrite = true;
			// Ensure all operations see skipCacheOnWrite == true
			flushOps();

			// Flush all cached values from overlay to wrappedLongList
			final ConcurrentHashMap<Long, Long> cache = cachedChanges.get();
			cache.forEachKey(PARALLELISM_THRESHOLD, k -> cache.compute(k, (key, value) -> {
				assert value != null : "We only iterate over known values and nobody else removes them";
				wrappedLongList.put(key, value);
				return null;
			}));
			assert cache.isEmpty() : "Cache not empty";
			cachedChanges.set(null);
			// Ensure all operations see cachedChanges == null
			flushOps();
		}

		ConcurrentHashMap<Long, Long> getCache() {
			return cachedChanges.get();
		}

		boolean skipCache() {
			return skipCacheOnWrite;
		}
	}

	private final Overlay overlay = new Overlay();

	/**
	 * Construct a new BufferedLongListWrapper wrapping the given LongList
	 *
	 * @param wrappedLongList
	 * 		The long list to wrap
	 */
	public LongListBufferedWrapper(final LongList wrappedLongList) {
		super(wrappedLongList.numLongsPerChunk, wrappedLongList.maxLongs);
		this.wrappedLongList = wrappedLongList;
		this.size.set(wrappedLongList.size());
	}

	/**
	 * Get the list that we are wrapping. This is helpful when logging and tracking down errors as it allows us to see
	 * if the data is different in base index.
	 *
	 * @return The wrapped long list
	 */
	public LongList getWrappedLongList() {
		return wrappedLongList;
	}

	/**
	 * Set if we are in pass though or overlay mode.
	 *
	 * <p><b>Important: it is require there be external locking to prevent this method and put methods being called at
	 * the same time.</b></p>
	 *
	 * @param useOverlay
	 * 		true puts us in overlay mode and false puts us in pass though mode.
	 */
	public synchronized void setUseOverlay(final boolean useOverlay) {
		if (useOverlay) {
			overlay.start();
		} else {
			overlay.stop();
		}
	}

	/**
	 * Loads the long at the given index.
	 *
	 * @param index
	 * 		the index of the long
	 * @param defaultValue
	 * 		The value to return if nothing is stored for the long
	 * @return the loaded long
	 * @throws IndexOutOfBoundsException
	 * 		if the index is negative or beyond current capacity of the list
	 */
	@Override
	public long get(final long index, final long defaultValue) {
		final ConcurrentHashMap<Long, Long> cache = overlay.getCache();
		if (cache != null) {
			final Long cachedValue = cache.get(index);
			if (cachedValue != null) {
				return cachedValue;
			}
		}
		return wrappedLongList.get(index, defaultValue);
	}

	/**
	 * Stores a long at the given index.
	 *
	 * @param index
	 * 		the index to use
	 * @param value
	 * 		the long to store
	 * @throws IndexOutOfBoundsException
	 * 		if the index is negative or beyond the max capacity of the list
	 * @throws IllegalArgumentException
	 * 		if the value is zero
	 */
	@Override
	public void put(final long index, final long value) {
		final long token = overlay.acquire();

		final ConcurrentHashMap<Long, Long> cache = overlay.getCache();
		if (cache != null) {
			cache.compute(index, (k, v) -> {
				if (overlay.skipCache() && v == null) {
					wrappedLongList.put(index, value);
					return null;
				} else {
					return value;
				}
			});
		} else {
			wrappedLongList.put(index, value);
		}

		overlay.release(token);
		size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
	}

	/**
	 * Stores a long at the given index, on the condition that the current long therein has a given value.
	 *
	 * @param index
	 * 		the index to use
	 * @param oldValue
	 * 		the value that must currently obtain at the index
	 * @param newValue
	 * 		the new value to store
	 * @return whether the newValue was set
	 * @throws IndexOutOfBoundsException
	 * 		if the index is negative or beyond the max capacity of the list
	 * @throws IllegalArgumentException
	 * 		if old value is zero (which could never be true)
	 */
	@Override
	public boolean putIfEqual(final long index, final long oldValue, final long newValue) {
		final long token = overlay.acquire();

		final AtomicBoolean valueWasSet = new AtomicBoolean(false);
		// get a read lock on cachedChanges state, so we know it is not changing
		final ConcurrentHashMap<Long, Long> cache = overlay.getCache();
		if (cache != null) {
			cache.compute(index, (k, v) -> {
				if (overlay.skipCache() && v == null) {
					// We're supposed to skip writing to the cache and go directly to the main index,
					// because the overlay is being written to the main index and there is no entry
					// in the overlay cache for this key, so we will just write directly to the
					// main index.
					valueWasSet.set(wrappedLongList.putIfEqual(index, oldValue, newValue));
					return null;
				} else {
					// Either we are supposed to write to the overlay OR the overlay is being flushed
					// to the main index but hasn't handled this key yet.
					if (v == null) {
						// There is no entry in the overlay. Check to see if there is a value in the main index.
						final long indexValue = wrappedLongList.get(index, LongList.IMPERMISSIBLE_VALUE);
						if (indexValue == LongList.IMPERMISSIBLE_VALUE) {
							// If there was not a value in the main index, then we have no match.
							valueWasSet.set(false);
							return null;
						} else {
							// There was a value in the main index, so compare it.
							final boolean matchesOldValue = indexValue == oldValue;
							valueWasSet.set(matchesOldValue);
							return matchesOldValue ? newValue : null;
						}
					} else {
						// There is an entry in the overlay.
						final boolean matchesOldValue = v == oldValue;
						valueWasSet.set(matchesOldValue);
						return matchesOldValue ? newValue : v;
					}
				}
			});
		} else {
			valueWasSet.set(wrappedLongList.putIfEqual(index, oldValue, newValue));
		}

		overlay.release(token);
		if (valueWasSet.get()) {
			size.getAndUpdate(oldSize -> index >= oldSize ? (index + 1) : oldSize);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Close wrapped LongLists if they need it
	 *
	 * @throws IOException
	 * 		if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		if (wrappedLongList instanceof Closeable) {
			((Closeable) wrappedLongList).close();
		}
	}

	/**
	 * Write all longs in this LongList into a file
	 * <p><b>
	 * It is not guaranteed what version of data will be written if the LongList is changed via put methods while
	 * this LongList is being written to a file. If you need consistency while calling put concurrently then use a
	 * BufferedLongListWrapper.
	 * </b></p>
	 *
	 * @param file
	 * 		The file to write into, it should not exist but its parent directory should exist and be writable.
	 * @throws IOException
	 * 		If there was a problem creating or writing to the file.
	 */
	@Override
	public void writeToFile(Path file) throws IOException {
		wrappedLongList.writeToFile(file);
	}

	/**
	 * Not needed for LongListBufferedWrapper
	 */
	@Override
	protected void writeLongsData(final FileChannel fc) {
		throw new UnsupportedOperationException("LongListBufferedWrapper does not write longs data");
	}

	/**
	 * Not needed for LongListBufferedWrapper
	 */
	@Override
	protected long lookupInChunk(final long chunkIndex, final long subIndex) {
		throw new UnsupportedOperationException("LongListBufferedWrapper does not read from chunks");
	}
}
