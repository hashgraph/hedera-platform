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

package com.swirlds.blob;

import com.swirlds.blob.internal.db.BlobStoragePipeline;
import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.Browser;
import com.swirlds.platform.Marshal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * Provides the primary standardized API for storing, retrieving, and manipulating arbitrary binary data backed by an
 * underlying data store that provides assurances with regards to integrity and automatic data de-duplication.
 */
public class BinaryObjectStore {

	/**
	 * the logger instance to be used for all messages
	 */
	private static final Logger log = LogManager.getLogger(BinaryObjectStore.class);

	/**
	 * the log marker used to log all initialization, recovery, and initialization errors
	 */
	private static final Marker LOGM_STARTUP = MarkerManager.getMarker("STARTUP");

	/**
	 * the log marker used for diagnostic logging related to lock timing
	 */
	private static final Marker LOGM_BLOB_LOCK_WAIT_TIME = MarkerManager.getMarker("BLOB_LOCK_WAIT_TIME");

	/**
	 * the eagerly initialized singleton instance of the {@link BinaryObjectStore}
	 */
	private static final BinaryObjectStore instance = new BinaryObjectStore();

	/**
	 * the read/write lock providing thread safety for all operations
	 */
	private static final StampedLock lock = new StampedLock();

	/**
	 * the number of nanoseconds in a millisecond
	 */
	private static final int NANO_TO_MS = 1_000_000;

	/**
	 * write to log when an operation waits for or holds a lock longer than this value(in ms)
	 */
	private static final int LOCK_LOG_DURATION = 500;

	/**
	 * the list of binary objects to be recovered from their hashes
	 */
	private List<BinaryObject> binaryObjectRecoveryList;

	/**
	 * the computed reference counts while recovering binary objects from their hashes
	 */
	private HashMap<Hash, Long> hashRefCountMap;

	/**
	 * Internal constructor used to construct the singleton instance.
	 */
	protected BinaryObjectStore() {

	}

	/**
	 * Gets the singleton instance of the {@link BinaryObjectStore}.
	 *
	 * @return the singleton instance of the {@link BinaryObjectStore}
	 */
	public static BinaryObjectStore getInstance() {
		return instance;
	}

	/**
	 * Generates the hash of the provided byte array. Uses the default hash algorithm as specified by {@link
	 * com.swirlds.common.crypto.Cryptography#digestSync(byte[])}.
	 *
	 * @param content
	 * 		the content for which the hash is to be computed
	 * @return the hash of the content
	 */
	public static Hash hashOf(final byte[] content) {
		return new Hash(CryptoFactory.getInstance().digestSync(content));
	}


	/**
	 * Utility method for safely throwing a {@link BinaryObjectDeletedException}.
	 *
	 * @param object
	 * 		the binary object to evaluate for deletion status
	 * @throws BinaryObjectDeletedException
	 * 		thrown if the provided {@link BinaryObject} is deleted
	 */
	private static void throwIfBinaryObjectDeleted(final BinaryObject object) {
		if (object != null && object.isReleased()) {
			throw new BinaryObjectDeletedException("BinaryObject has already been deleted");
		}
	}

	/**
	 * Registers a {@link BinaryObject} containing only a hash for recovery after a {@link com.swirlds.common.Platform}
	 * restart from a saved state. Must only be called after {@link #startInit()} and before {@link #stopInit()} has
	 * been called. This method repopulates the {@link BinaryObject} instance with the database primary key which is
	 * never serialized or hashed.
	 *
	 * If this method is called prior to {@link #startInit()} or after {@link #stopInit()} then no exception will be
	 * thrown and the call will be a no-op. This is necessary to support certain deserialization cases such as reconnect
	 * where the code that handles {@link BinaryObject} instances is unable to distinguish between deserialization
	 * due to a restart versus deserialization due to a reconnect.
	 *
	 * @param binaryObject
	 * 		the binary object instance to register for recovery
	 * @throws IllegalArgumentException
	 * 		if the provided {@code binaryObject} parameter is {@code null}
	 */
	public void registerForRecovery(final BinaryObject binaryObject) {
		// See javadoc above - this call should be a no-op if we are outside of the init window
		if (!isInitializing()) {
			return;
		}

		if (binaryObject == null) {
			throw new IllegalArgumentException("binaryObject");
		}

		binaryObjectRecoveryList.add(binaryObject);

		hashRefCountMap.compute(binaryObject.getHash(), (key, val) -> (val == null) ? 1 : val + 1);
	}

	/**
	 * Called by the {@link Browser} class prior to the creation of the {@link com.swirlds.common.Platform} instances to
	 * notify the {@link BinaryObjectStore} to prepare for application initialization.
	 *
	 * <p>
	 * This will be replaced by {@link com.swirlds.common.notification.NotificationEngine} hooks in the future.
	 * </p>
	 */
	public void startInit() {
		binaryObjectRecoveryList = new ArrayList<>();
		hashRefCountMap = new HashMap<>();
	}

	/**
	 * Called by the {@link Browser} class after the successful creation of the {@link com.swirlds.common.Platform}
	 * instances to notify the {@link BinaryObjectStore} that the applications have been initialized.
	 *
	 * <p>
	 * This will be replaced by {@link com.swirlds.common.notification.NotificationEngine} hooks in the future.
	 * </p>
	 */
	public void stopInit() {
		recover();
	}


	/**
	 * Gets the current {@link BinaryObjectStore} initialization state. Returns true if {@link #startInit()} has been
	 * called but {@link #stopInit()} has not yet been called. Returns false if {@link #startInit()} has not yet been
	 * called or after {@link #stopInit()} has been called.
	 *
	 * @return true if currently initializing; otherwise false
	 */
	public boolean isInitializing() {
		return (binaryObjectRecoveryList != null && hashRefCountMap != null);
	}

	/**
	 * Called by the {@link #stopInit()} method implementation to perform {@link BinaryObject} recovery for each
	 * instance registered via the {@link #registerForRecovery(BinaryObject)} method.
	 *
	 * @throws BinaryObjectNotFoundException
	 * 		if one or more of the {@link BinaryObject} instances scheduled for recovery were not found in the underlying
	 * 		data store
	 */
	public void recover() {

		log.debug(LOGM_STARTUP, "Recovery Starting");

		if (!isInitializing()) {
			throw new BinaryObjectException(
					"BinaryObject: The startInit() method must be called before the recover() method.");
		}

//		if (hashRefCountMap.size() == 0) {
////			//no work to do
////			return;
////		}

		log.debug(LOGM_STARTUP, "Recovering Objects [ objectCount = {}, uniqueObjectCount = {} ]",
				binaryObjectRecoveryList.size(), hashRefCountMap.size());

		byte[][] hashes = new byte[hashRefCountMap.size()][Marshal.HASH_SIZE_BYTES];
		long[] counts = new long[hashRefCountMap.size()];

		if (hashRefCountMap.size() > 0) {
			int index = 0;
			for (Map.Entry<Hash, Long> entry : hashRefCountMap.entrySet()) {
				hashes[index] = entry.getKey().getValue();
				counts[index] = entry.getValue();
				index++;
			}
		}

		HashMap<Hash, Long> hashIdMap = new HashMap<>();

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();

			Long[] ids = pipeline.restore(counts, hashes);

			for (int i = 0; i < ids.length; i++) {
				hashIdMap.put(new Hash(hashes[i]), ids[i]);
			}

			pipeline.commit();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		for (BinaryObject b : binaryObjectRecoveryList) {
			Long retrievedId = hashIdMap.get(b.getHash());

			if (retrievedId == null) {
				throw new BinaryObjectException("Failed to retrieve Id for Hash of BinaryObject");
			}

			b.setId(retrievedId);
		}

		binaryObjectRecoveryList = null;
		hashRefCountMap = null;

		log.debug(LOGM_STARTUP, "Recovery Finished");
	}

	/**
	 * Retrieves the actual {@link BinaryObject} content from the underlying data store.
	 *
	 * @param binaryObject
	 * 		the instance for which the actual content is to be returned
	 * @return the raw content as a byte array
	 * @throws BinaryObjectDeletedException
	 * 		if the provided {@link BinaryObject} instance has been marked as deleted
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 */
	public byte[] get(final BinaryObject binaryObject) {
		throwIfBinaryObjectDeleted(binaryObject);

		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::get: waiting for readLock");
		long readLock = lock.readLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::get: get readLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::get: Time spent on waiting for readLock: {} ms",
					timeWaitLock);
		}

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();
			final long id = binaryObject.getId();

			final byte[] content = pipeline.get(id);

			pipeline.commit();
			return content;
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to get BinaryObject", e);
		} finally {
			lock.unlock(readLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::get: unlock readLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::get: Time spent on holding readLock: {} ms",
						lockDuration);
			}
		}
	}

	/**
	 * Inserts the raw content into the underlying data store and returns an associated {@link BinaryObject} instance
	 * containing the {@link Hash} of the data inserted. If the hash of the content already exists in the underlying
	 * data store, then this method will increase the reference count and return a {@link BinaryObject} instance
	 * associated with the existing content.
	 *
	 * @param bytes
	 * 		the raw content to be into the underlying data store
	 * @return a {@link BinaryObject} instance associated with the data inserted
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 */
	public BinaryObject put(final byte[] bytes) {
		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::put: waiting for writeLock");
		long writeLock = lock.writeLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::put: get writeLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::put: Time spent on waiting for writeLock: {} ms",
					timeWaitLock);
		}

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();

			final BinaryObject object = pipeline.put(hashOf(bytes), bytes);

			pipeline.commit();
			return object;
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to insert BinaryObject", e);
		} finally {
			lock.unlock(writeLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::put: unlock writeLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::put: Time spent on holding writeLock: {} ms",
						lockDuration);
			}
		}
	}

	/**
	 * Appends content to an existing {@link BinaryObject} instance without modifying the original content and returns
	 * the new {@link BinaryObject} instance representing the result of the append operation.
	 *
	 * @param binaryObject
	 * 		the binary object instance to which additional binary content should be appended
	 * @param bytes
	 * 		the additional binary content to be appended
	 * @return the new {@link BinaryObject} instance representing the result of the append operation
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 * @throws BinaryObjectDeletedException
	 * 		thrown if the provided {@link BinaryObject} is deleted
	 */
	public BinaryObject append(final BinaryObject binaryObject, final byte[] bytes) {
		throwIfBinaryObjectDeleted(binaryObject);
		throwIfImmutable(binaryObject);

		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::append: waiting for writeLock");
		long writeLock = lock.writeLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::append: get writeLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::append: Time spent on waiting for writeLock: {} ms",
					timeWaitLock);
		}

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();
			long id = binaryObject.getId();

			final byte[] originalContent = pipeline.get(id);
			final byte[] newContent = new byte[originalContent.length + bytes.length];

			System.arraycopy(originalContent, 0, newContent, 0, originalContent.length);
			System.arraycopy(bytes, 0, newContent, originalContent.length, bytes.length);

			final BinaryObject newObject = pipeline.put(hashOf(newContent), newContent);

			pipeline.commit();

			return newObject;
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to append BinaryObject", e);
		} finally {
			lock.unlock(writeLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::append: unlock writeLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::append: Time spent on holding writeLock: {} ms",
						lockDuration);
			}
		}
	}

	/**
	 * Replaces content contained by an existing {@link BinaryObject} instance without modifying the original content
	 * and returns the new {@link BinaryObject} instance representing the result of the update operation. This method is
	 * identical to the {@link #put(byte[])} method except it verifies that the prior {@link BinaryObject} instances
	 * exists and is not deleted.
	 *
	 * @param binaryObject
	 * 		the binary object instance for which the content should be replaced
	 * @param bytes
	 * 		the additional binary content used to replace the existing content
	 * @return the new {@link BinaryObject} instance representing the result of the update operation
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 * @throws BinaryObjectDeletedException
	 * 		thrown if the provided {@link BinaryObject} is deleted
	 */
	public BinaryObject update(final BinaryObject binaryObject, final byte[] bytes) {
		throwIfBinaryObjectDeleted(binaryObject);
		throwIfImmutable(binaryObject);

		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::update: waiting for writeLock");
		long writeLock = lock.writeLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::update: get writeLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::update: Time spent on waiting for writeLock: {} ms",
					timeWaitLock);
		}

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();

			final BinaryObject newObject = pipeline.put(hashOf(bytes), bytes);
			pipeline.commit();

			return newObject;
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to update BinaryObject", e);
		} finally {
			lock.unlock(writeLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::update: unlock writeLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::update: Time spent on holding writeLock: {} ms",
						lockDuration);
			}
		}
	}

	/**
	 * Retrieves the total number of unique {@link BinaryObject} instances contained in the underlying data store.
	 *
	 * @return the total number of the unique (de-duplicated) {@link BinaryObject} instances contained in the data store
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 */
	public long retrieveNumberOfBinaryObjects() {
		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::retrieveNumberOfBinaryObjects: waiting for readLock");
		long readLock = lock.readLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::retrieveNumberOfBinaryObjects: get readLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME,
					"BinaryObjectStore::retrieveNumberOfBinaryObjects: Time spent on waiting for readLock: {} ms",
					timeWaitLock);
		}

		try (final BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			return pipeline.retrieveNumberOfBlobs();
		} catch (final SQLException ex) {
			throw new BinaryObjectException("Failed to retrieve number of binary objects", ex);
		} finally {
			lock.unlock(readLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::retrieveNumberOfBinaryObjects: unlock readLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME,
						"BinaryObjectStore::retrieveNumberOfBinaryObjects: Time spent on holding readLock: {} ms",
						lockDuration);
			}
		}
	}

	/**
	 * Arbitrarily increases the reference count of a {@link BinaryObject} instance in the underlying data store.
	 *
	 * @param binaryObject
	 * 		the binary object instance for which the reference count should be increased
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 * @throws BinaryObjectDeletedException
	 * 		thrown if the provided {@link BinaryObject} is deleted
	 */
	public void increaseReferenceCount(final BinaryObject binaryObject) {
		throwIfBinaryObjectDeleted(binaryObject);

		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::increaseReferenceCount: waiting for writeLock");
		long writeLock = lock.writeLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::increaseReferenceCount: get writeLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME,
					"BinaryObjectStore::increaseReferenceCount: Time spent on waiting for writeLock: {} ms",
					timeWaitLock);
		}

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();

			long id = binaryObject.getId();

			pipeline.increaseReferenceCount(id);

			pipeline.commit();
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to increase reference count for BinaryObject", e);
		} finally {
			lock.unlock(writeLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::increaseReferenceCount: unlock writeLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME,
						"BinaryObjectStore::increaseReferenceCount: Time spent on holding writeLock: {} ms",
						lockDuration);
			}
		}
	}

	/**
	 * Dereferences the provided {@link BinaryObject} instance thereby decreasing the reference count in the underlying
	 * data store. If the resulting reference count is zero, then the object will be deleted from the underlying data
	 * store.
	 *
	 * <p>
	 * If the provided {@link BinaryObject} instance is {@code null}, already de-referenced, or deleted, then this
	 * method will return silently.
	 * </p>
	 *
	 * @param binaryObject
	 * 		the binary object instance to be de-referenced or deleted
	 * @throws BinaryObjectException
	 * 		if an error occurs while accessing the underlying data store
	 */
	public void delete(final BinaryObject binaryObject) {
		if (binaryObject == null || binaryObject.isReleased()) {
			return;
		}

		final long startTime = System.nanoTime();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::delete: waiting for writeLock");
		long writeLock = lock.writeLock();
		log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::delete: get writeLock");
		final long lockTime = System.nanoTime();
		final long timeWaitLock = (lockTime - startTime) / NANO_TO_MS;
		if (timeWaitLock > LOCK_LOG_DURATION) {
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::delete: Time spent on waiting for writeLock: {} ms",
					timeWaitLock);
		}

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {
			pipeline.withTransaction();

			binaryObject.delete(pipeline);

			pipeline.commit();
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to delete BinaryObject", e);
		} finally {
			lock.unlock(writeLock);
			log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::delete: unlock writeLock");
			final long unlockTime = System.nanoTime();
			final long lockDuration = (unlockTime - lockTime) / NANO_TO_MS;
			if (lockDuration > LOCK_LOG_DURATION) {
				log.info(LOGM_BLOB_LOCK_WAIT_TIME, "BinaryObjectStore::delete: Time spent on holding writeLock: {} ms",
						lockDuration);
			}
		}
	}

	private void throwIfImmutable(final BinaryObject binaryObject) {
		binaryObject.throwIfImmutable();
	}
}
