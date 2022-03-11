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

package com.swirlds.jasperdb.files.hashmap;

import com.swirlds.common.Units;
import com.swirlds.jasperdb.Snapshotable;
import com.swirlds.jasperdb.collections.LongListBufferedWrapper;
import com.swirlds.jasperdb.collections.LongListDisk;
import com.swirlds.jasperdb.collections.LongListOffHeap;
import com.swirlds.jasperdb.files.DataFileCollection;
import com.swirlds.jasperdb.files.DataFileCollection.LoadedDataCallback;
import com.swirlds.jasperdb.files.DataFileReader;
import com.swirlds.virtualmap.VirtualKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.tuple.primitive.IntObjectPair;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.swirlds.jasperdb.files.DataFileCommon.formatSizeBytes;
import static com.swirlds.jasperdb.files.DataFileCommon.getSizeOfFiles;
import static com.swirlds.jasperdb.files.DataFileCommon.getSizeOfFilesByPath;
import static com.swirlds.jasperdb.files.DataFileCommon.logMergeStats;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JASPER_DB;

/**
 * This is a hash map implementation where the bucket index is in RAM and the buckets are on disk. It maps a VirtualKey
 * to a long value. This allows very large maps with minimal RAM usage and the best performance profile as by using an
 * in memory index we avoid the need for random disk writes. Random disk writes are horrible performance wise in our
 * testing.
 * <p>
 * This implementation depends on good hashCode() implementation on the keys, if there are too many hash collisions the
 * performance can get bad.
 * <p>
 * <b>IMPORTANT: This implementation assumes a single writing thread. There can be multiple readers while writing is
 * happening.</b>
 */
public class HalfDiskHashMap<K extends VirtualKey<? super K>> implements AutoCloseable, Snapshotable {
	private static final Logger LOG = LogManager.getLogger(HalfDiskHashMap.class);

	/**
	 * The version number for format of current data files
	 */
	private static final int METADATA_FILE_FORMAT_VERSION = 1;
	/**
	 * Each index change includes both a bucket index and bucket location.
	 */
	private static final int INDEX_CHANGE_COMPONENTS = 2;
	/**
	 * Nominal value for value to say please delete from map.
	 */
	protected static final long SPECIAL_DELETE_ME_VALUE = Long.MIN_VALUE;
	/** The amount of data used for storing key hash code */
	protected static final int KEY_HASHCODE_SIZE = Integer.BYTES;
	/** The amount of data used for storing value in bucket, our values are longs as this is a key to long map */
	protected static final int VALUE_SIZE = Long.BYTES;
	/**
	 * This is the average number of entries per bucket we aim for when filled to mapSize. It is a heuristic used
	 * alongside LOADING_FACTOR in calculation for how many buckets to create. The larger this number the slower lookups
	 * will be but the more even distribution of entries across buckets will be. So it is a matter of balance.
	 */
	private static final long GOOD_AVERAGE_BUCKET_ENTRY_COUNT = 20;
	/** how full should all available bins be if we are at the specified map size */
	public static final double LOADING_FACTOR = 0.6;
	/** Long list used for mapping bucketIndex(index into list) to disk location for latest copy of bucket */
	private final LongListBufferedWrapper bucketIndexToBucketLocation;
	/** DataFileCollection manages the files storing the buckets on disk */
	private final DataFileCollection<Bucket<K>> fileCollection;
	/** This is the number of buckets needed to store mapSize entries if we ere only LOADING_FACTOR percent full */
	private final int minimumBuckets;
	/**
	 * This is the next power of 2 bigger than minimumBuckets. It needs to be a power of two, so that we can optimize
	 * and avoid the cost of doing a % to find the bucket index from hash code.
	 */
	private final int numOfBuckets;
	/**
	 * The requested max size for the map, this is the maximum number of key/values expected to be stored in this map.
	 */
	private final long mapSize;
	/** The name to use for the files prefix on disk */
	private final String storeName;
	private final BucketSerializer<K> bucketSerializer;
	/** Store for session data during a writing transaction */
	private IntObjectHashMap<BucketMutation<K>> oneTransactionsData = null;
	/** The thread that called startWriting. We use it to check that other writing calls are done on same thread */
	private Thread writingThread;

	/**
	 * Construct a new HalfDiskHashMap
	 *
	 * @param mapSize
	 * 		The maximum map number of entries. This should be more than big enough to avoid too many key collisions.
	 * @param keySerializer
	 * 		Serializer for converting raw data to/from keys
	 * @param storeDir
	 * 		The directory to use for storing data files.
	 * @param storeName
	 * 		The name for the data store, this allows more than one data store in a single directory.
	 * @param preferDiskBasedIndexes
	 * 		When true we will use disk based indexes rather than ram where possible. This will
	 * 		come with a significant performance cost, especially for writing. It is possible to
	 * 		load a data source that was written with memory indexes with disk based indexes and
	 * 		via versa.
	 * @throws IOException
	 * 		If there was a problem creating or opening a set of data files.
	 */
	public HalfDiskHashMap(
			final long mapSize,
			final KeySerializer<K> keySerializer,
			final Path storeDir,
			final String storeName,
			final boolean preferDiskBasedIndexes
	) throws IOException {
		this.mapSize = mapSize;
		this.storeName = storeName;
		Path indexFile = storeDir.resolve(storeName + "_bucket_index.ll");
		// create bucket serializer
		this.bucketSerializer = new BucketSerializer<>(keySerializer);
		// load or create new
		LoadedDataCallback loadedDataCallback;
		if (Files.exists(storeDir)) {
			// load metadata
			Path metaDataFile = storeDir.resolve(storeName + "_metadata.hdhm");
			if (Files.exists(metaDataFile)) {
				try (DataInputStream metaIn = new DataInputStream(Files.newInputStream(metaDataFile))) {
					final int fileVersion = metaIn.readInt();
					if (fileVersion != METADATA_FILE_FORMAT_VERSION) {
						throw new IOException("Tried to read a file with incompatible file format version [" +
								fileVersion + "], expected [" + METADATA_FILE_FORMAT_VERSION + "].");
					}
					minimumBuckets = metaIn.readInt();
					numOfBuckets = metaIn.readInt();
				}
			} else {
				LOG.error(EXCEPTION.getMarker(),
						"Loading existing set of data files but now metadata file was found in [{}]",
						storeDir.toAbsolutePath());
				throw new IOException("Can not load an existing HalfDiskHashMap from [" + storeDir.toAbsolutePath() +
						"] because metadata file is missing");
			}
			// load or rebuild index
			if (Files.exists(indexFile)) {
				bucketIndexToBucketLocation = new LongListBufferedWrapper(
						preferDiskBasedIndexes ? new LongListDisk(indexFile) : new LongListOffHeap(indexFile));
				loadedDataCallback = null;
			} else {
				// create new index and setup call back to rebuild
				bucketIndexToBucketLocation = new LongListBufferedWrapper(
						preferDiskBasedIndexes ? new LongListDisk(indexFile) : new LongListOffHeap());
				loadedDataCallback = (key, dataLocation, dataValue) -> bucketIndexToBucketLocation.put(key,
						dataLocation);
			}
		} else {
			// create store dir
			Files.createDirectories(storeDir);
			// create new index
			bucketIndexToBucketLocation = new LongListBufferedWrapper(
					preferDiskBasedIndexes ? new LongListDisk(indexFile) : new LongListOffHeap());
			// calculate number of entries we can store in a disk page
			minimumBuckets = (int) Math.ceil((mapSize / LOADING_FACTOR) / GOOD_AVERAGE_BUCKET_ENTRY_COUNT);
			// numOfBuckets is the nearest power of two greater than minimumBuckets with a min of 4096
			numOfBuckets = Integer.highestOneBit(minimumBuckets) * 2;
			// we are new so no need for a loadedDataCallback
			loadedDataCallback = null;
			LOG.info(JASPER_DB.getMarker(), "HalfDiskHashMap [{}] created with minimumBuckets={} and numOfBuckets={}",
					storeName, minimumBuckets, numOfBuckets);
		}
		// create file collection
		fileCollection = new DataFileCollection<>(storeDir, storeName, bucketSerializer, loadedDataCallback);
	}

	/**
	 * Merge all read only files that match provided filter. Important the set of files must be contiguous in time
	 * otherwise the merged data will be invalid.
	 *
	 * @param filterForFilesToMerge
	 * 		filter to choose which subset of files to merge
	 * @param mergingPaused
	 * 		AtomicBoolean to monitor if we should pause merging
	 * @throws IOException
	 * 		if there was a problem merging
	 */
	public void merge(final Function<List<DataFileReader<Bucket<K>>>,
			List<DataFileReader<Bucket<K>>>> filterForFilesToMerge, final AtomicBoolean mergingPaused,
			final int minNumberOfFilesToMerge) throws IOException {
		final long START = System.currentTimeMillis();
		final List<DataFileReader<Bucket<K>>> allFilesBefore = fileCollection.getAllFilesAvailableForMerge();
		final List<DataFileReader<Bucket<K>>> filesToMerge = filterForFilesToMerge.apply(allFilesBefore);
		if (filesToMerge == null) {
			// nothing to do
			return;
		}
		final int size = filesToMerge.size();
		if (size < minNumberOfFilesToMerge) {
			LOG.info(JASPER_DB.getMarker(),
					"[{}] No meed to merge as {} is less than the minimum {} files to merge.",
					storeName, size, minNumberOfFilesToMerge);
			return;
		}
		final long filesToMergeSize = getSizeOfFiles(filesToMerge);
		LOG.info(JASPER_DB.getMarker(),
				"[{}] Starting merging {} files total {} Gb",
				storeName, size, formatSizeBytes(filesToMergeSize));
		final List<Path> newFilesCreated = fileCollection.mergeFiles(
				// update index with all moved data
				moves -> moves.forEach(bucketIndexToBucketLocation::putIfEqual),
				filesToMerge, mergingPaused);
		logMergeStats(
				storeName, (System.currentTimeMillis() - START) * Units.MILLISECONDS_TO_SECONDS,
				filesToMergeSize, getSizeOfFilesByPath(newFilesCreated),
				fileCollection, filesToMerge, allFilesBefore, LOG);
	}

	/**
	 * Start snapshot, this is called while saving is blocked. It is expected to complete as fast as possible and only
	 * do the minimum needed to capture/write state that could be changed by saving.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	@Override
	public void startSnapshot(Path snapshotDirectory) throws IOException {
		bucketIndexToBucketLocation.setUseOverlay(true);
		fileCollection.startSnapshot(snapshotDirectory);
	}

	/**
	 * Do the bulk of snapshot work, as much as possible. Saving is not blocked while this method is running, and it is
	 * expected that saving can happen concurrently without problems. This will block till the snapshot is completely
	 * created.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	@Override
	public void middleSnapshot(Path snapshotDirectory) throws IOException {
		// create snapshot directory if needed
		Files.createDirectories(snapshotDirectory);
		// write index to file
		bucketIndexToBucketLocation.writeToFile(snapshotDirectory.resolve(storeName + "_bucket_index.ll"));
		// snapshot files
		fileCollection.middleSnapshot(snapshotDirectory);
		// write metadata
		try (DataOutputStream metaOut = new DataOutputStream(
				Files.newOutputStream(snapshotDirectory.resolve(storeName + "_metadata.hdhm")))) {
			metaOut.writeInt(METADATA_FILE_FORMAT_VERSION);
			metaOut.writeInt(minimumBuckets);
			metaOut.writeInt(numOfBuckets);
			metaOut.flush();
		}
	}

	/**
	 * End snapshot, this is called while saving is blocked. It is expected to complete as fast as possible and only do
	 * the minimum needed to finish any work and return state after snapshotting.
	 *
	 * @param snapshotDirectory
	 * 		Directory to put snapshot into, it will be created if it doesn't exist.
	 * @throws IOException
	 * 		If there was a problem snapshotting
	 */
	@Override
	public void endSnapshot(Path snapshotDirectory) throws IOException {
		fileCollection.endSnapshot(snapshotDirectory);
		bucketIndexToBucketLocation.setUseOverlay(false);
	}

	/**
	 * Get statistics for sizes of all files
	 *
	 * @return statistics for sizes of all fully written files, in bytes
	 */
	public LongSummaryStatistics getFilesSizeStatistics() {
		return fileCollection.getAllFullyWrittenFilesSizeStatistics();
	}

	/**
	 * Close this HalfDiskHashMap's data files. Once closed this HalfDiskHashMap can not be reused. You should make
	 * sure you call close before system exit otherwise any files being written might not be in a good state.
	 *
	 * @throws IOException
	 * 		If there was a problem closing the data files.
	 */
	@Override
	public void close() throws IOException {
		bucketIndexToBucketLocation.close();
		fileCollection.close();
	}

	// =================================================================================================================
	// Writing API - Single thead safe

	/**
	 * Start a writing session to the map. Each new writing session results in a new data file on disk, so you should
	 * ideally batch up map writes.
	 */
	public void startWriting() {
		oneTransactionsData = new IntObjectHashMap<>();
		writingThread = Thread.currentThread();
	}

	/**
	 * Put a key/value during the current writing session. The value will not be retrievable until it is committed in
	 * the endWriting() call.
	 *
	 * @param key
	 * 		the key to store the value for
	 * @param value
	 * 		the value to store for given key
	 */
	public void put(final K key, final long value) {
		if (key == null) {
			throw new IllegalArgumentException("Can not write a null key");
		}
		if (oneTransactionsData == null) {
			throw new IllegalStateException(
					"Trying to write to a HalfDiskHashMap when you have not called startWriting().");
		}
		if (Thread.currentThread() != writingThread) {
			throw new IllegalStateException("Tried to write with different thread to startWriting()");
		}
		// store key and value in transaction cache
		final int bucketIndex = computeBucketIndex(key.hashCode());
		final BucketMutation<K> bucketMap = oneTransactionsData.getIfAbsentPut(
				bucketIndex, () -> new BucketMutation<>(key, value));
		bucketMap.put(key, value);
	}

	/**
	 * Delete a key entry from map
	 *
	 * @param key
	 * 		The key to delete entry for
	 */
	public void delete(final K key) {
		put(key, SPECIAL_DELETE_ME_VALUE);
	}

	/**
	 * End current writing session, committing all puts to data store.
	 *
	 * @throws IOException
	 * 		If there was a problem committing data to store
	 */
	public void endWriting() throws IOException {
		/* FUTURE WORK - https://github.com/swirlds/swirlds-platform/issues/3943 */
		if (Thread.currentThread() != writingThread) {
			throw new IllegalStateException("Tried calling endWriting with different thread to startWriting()");
		}
		writingThread = null;
		LOG.info(JASPER_DB.getMarker(),
				"Finishing writing to {}, num of changed bins = {}, num of changed keys = {}",
				storeName,
				oneTransactionsData.size(),
				oneTransactionsData.stream().mapToLong(BucketMutation::size).sum());
		// iterate over transaction cache and save it all to file
		if (!oneTransactionsData.isEmpty()) {
			//  write to files
			fileCollection.startWriting();
			// for each changed bucket, write the new buckets to file but do not update index yet
			final LongArrayList indexChanges = new LongArrayList();
			int oldBucketIndex = -1;
			for (IntObjectPair<BucketMutation<K>> keyValue : oneTransactionsData.keyValuesView().toList().sortThis()) {
				final int bucketIndex = keyValue.getOne();
				if (bucketIndex < oldBucketIndex) {
					throw new IllegalStateException("Somehow we got our bucket indexes out of order: old="
							+ oldBucketIndex + ", new =" + bucketIndex);
				}
				oldBucketIndex = bucketIndex;
				final BucketMutation<K> bucketMap = keyValue.getTwo();
				try {
					Bucket<K> bucket = fileCollection.readDataItemUsingIndex(bucketIndexToBucketLocation, bucketIndex);
					if (bucket == null) {
						// create a new bucket
						bucket = bucketSerializer.getReusableEmptyBucket();
						bucket.setBucketIndex(bucketIndex);
					}
					final Bucket<K> finalBucket = bucket;
					// for each changed key in bucket, update bucket
					bucketMap.forEachKeyValue((k, v) -> finalBucket.putValue(k.hashCode(), k, v));
					// save bucket
					final long bucketLocation = fileCollection.storeDataItem(bucket);

					// stash update bucketIndexToBucketLocation
					indexChanges.add(bucketIndex);
					indexChanges.add(bucketLocation);
				} catch (IllegalStateException e) {
					printStats();
					debugDumpTransactionCacheCondensed();
					debugDumpTransactionCache();
					throw e;
				}
			}
			// close files session
			final DataFileReader<Bucket<K>> dataFileReader = fileCollection.endWriting(0, numOfBuckets);
			// for each changed bucket update index
			for (int i = 0; i < indexChanges.size(); i += INDEX_CHANGE_COMPONENTS) {
				final long bucketIndex = indexChanges.get(i);
				final long bucketLocation = indexChanges.get(i + 1);
				// update bucketIndexToBucketLocation
				bucketIndexToBucketLocation.put(bucketIndex, bucketLocation);
			}
			// we have updated all indexes so the data file can now be included in merges
			dataFileReader.setFileAvailableForMerging(true);
		}
		// clear put cache
		oneTransactionsData = null;
	}

	// =================================================================================================================
	// Reading API - Multi thead safe

	/**
	 * Get a value from this map
	 *
	 * @param key
	 * 		The key to get value for
	 * @param notFoundValue
	 * 		the value to return if the key was not found
	 * @return the value retrieved from the map or {notFoundValue} if no value was stored for the given key
	 * @throws IOException
	 * 		If there was a problem reading from the map
	 */
	public long get(final K key, final long notFoundValue) throws IOException {
		if (key == null) {
			throw new IllegalArgumentException("Can not get a null key");
		}
		final int keyHash = key.hashCode();
		final int bucketIndex = computeBucketIndex(keyHash);
		final Bucket<K> bucket = fileCollection.readDataItemUsingIndex(bucketIndexToBucketLocation, bucketIndex);
		if (bucket != null) {
			return bucket.findValue(keyHash, key, notFoundValue);
		}
		return notFoundValue;
	}

	// =================================================================================================================
	// Debugging Print API

	/** Debug dump stats for this map */
	public void printStats() {
		LOG.info(JASPER_DB.getMarker(), "HalfDiskHashMap Stats {\n" +
						"    mapSize = {}\n" +
						"    minimumBuckets = {}\n" +
						"    numOfBuckets = {}\n" +
						"    GOOD_AVERAGE_BUCKET_ENTRY_COUNT = {}\n" +
						"}"
				, mapSize, minimumBuckets, numOfBuckets, GOOD_AVERAGE_BUCKET_ENTRY_COUNT);
	}

	/** Useful debug method to print the current state of the transaction cache */
	public void debugDumpTransactionCacheCondensed() {
		LOG.info(JASPER_DB.getMarker(), "=========== TRANSACTION CACHE ==========================");
		for (int bucketIndex = 0; bucketIndex < numOfBuckets; bucketIndex++) {
			final BucketMutation<K> bucketMap = oneTransactionsData.get(bucketIndex);
			if (bucketMap != null) {
				final String tooBig = (bucketMap.size() > GOOD_AVERAGE_BUCKET_ENTRY_COUNT) ?
						("TOO MANY! > " + GOOD_AVERAGE_BUCKET_ENTRY_COUNT) : "";
				LOG.info(JASPER_DB.getMarker(),
						"bucketIndex [{}] , count={} {}",
						bucketIndex, bucketMap.size(), tooBig);
			} else {
				LOG.info(JASPER_DB.getMarker(), "bucketIndex [{}] , EMPTY!", bucketIndex);
			}
		}
		LOG.info(JASPER_DB.getMarker(), "========================================================");
	}

	/** Useful debug method to print the current state of the transaction cache */
	public void debugDumpTransactionCache() {
		LOG.info(JASPER_DB.getMarker(), "=========== TRANSACTION CACHE ==========================");
		for (int bucketIndex = 0; bucketIndex < numOfBuckets; bucketIndex++) {
			final BucketMutation<K> bucketMap = oneTransactionsData.get(bucketIndex);
			if (bucketMap != null) {
				final String tooBig = (bucketMap.size() > GOOD_AVERAGE_BUCKET_ENTRY_COUNT) ?
						("TOO MANY! > " + GOOD_AVERAGE_BUCKET_ENTRY_COUNT) : "";
				LOG.info(JASPER_DB.getMarker(),
						"bucketIndex [{}] , count={} {}",
						bucketIndex, bucketMap.size(), tooBig);
				bucketMap.forEachKeyValue((k, l) ->
						LOG.info(JASPER_DB.getMarker(),
								"        keyHash [{}] bucket [{}]  key [{}] value [{}]",
								k.hashCode(), computeBucketIndex(k.hashCode()), k, l));
			}
		}
		LOG.info(JASPER_DB.getMarker(), "========================================================");
	}

	// =================================================================================================================
	// Private API

	/**
	 * Computes which bucket a key with the given hash falls. Depends on the fact the numOfBuckets is a power of two.
	 * Based on same calculation that is used in java HashMap.
	 *
	 * @param keyHash
	 * 		the int hash for key
	 * @return the index of the bucket that key falls in
	 */
	private int computeBucketIndex(final int keyHash) {
		return (numOfBuckets - 1) & keyHash;
	}
}
