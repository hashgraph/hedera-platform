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

package com.swirlds.common;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.internal.HashUtils;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.BadIOException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link TransactionSignature} objects. The
 * contents of the transaction is completely immutable; however, the list of signatures features controlled mutability
 * with a thread-safe and atomic implementation. The transaction internally uses a {@link ReadWriteLock} to provide
 * atomic reads and writes to the underlying list of signatures.
 * <p>
 * Selectively combining controlled mutability for certain aspects while using immutability for the rest grants a
 * significant performance improvement over a completely mutable or completely immutable object.
 * </p>
 */
public class Transaction implements Comparable<Transaction>, SelfSerializable {
	private static final long CLASS_ID = 0xa0eda13e329feccaL;
	private static final int CLASS_VERSION = 1;

	/** A per-transaction read/write lock to ensure thread safety of the signature list */
	private final ReadWriteLock readWriteLock;

	/** The content (payload) of the transaction */
	private byte[] contents;

	/** The list of optional signatures attached to this transaction */
	private List<TransactionSignature> signatures;

	/** A flag indicating whether this transaction was originated by the application or the platform */
	private boolean system;

	public Transaction() {
		this.readWriteLock = new ReentrantReadWriteLock(false);
	}

	/**
	 * Constructs a new application transaction with no associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	public Transaction(final byte[] contents) {
		this(contents, false, (List<TransactionSignature>) null);
	}

	/**
	 * Constructs a new application transaction with an optional list of signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	public Transaction(final byte[] contents, final TransactionSignature... signatures) {
		this(contents, false, signatures);
	}

	/**
	 * Constructs a new application transaction with an optional list of signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	Transaction(final byte[] contents, final List<TransactionSignature> signatures) {
		this(contents, false, signatures);
	}

	/**
	 * Constructs a new transaction with no associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param system
	 *        {@code true} if this is a system transaction; {@code false} if this is an application transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	public Transaction(final byte[] contents, final boolean system) {
		this(contents, system, (List<TransactionSignature>) null);
	}

	/**
	 * Constructs a new transaction with an optional list of associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param system
	 *        {@code true} if this is a system transaction; {@code false} if this is an application
	 * 		transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	private Transaction(final byte[] contents, final boolean system, final TransactionSignature... signatures) {
		this(contents, system, (signatures != null && signatures.length > 0) ? Arrays.asList(signatures) : null);
	}

	/**
	 * Constructs a new transaction with an optional list of associated signatures.
	 *
	 * @param contents
	 * 		the binary content/payload of the Swirld transaction
	 * @param system
	 *        {@code true} if this is a system transaction; {@code false} if this is an application
	 * 		transaction
	 * @param signatures
	 * 		an optional list of signatures to be included with this transaction
	 * @throws NullPointerException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	private Transaction(final byte[] contents, final boolean system, final List<TransactionSignature> signatures) {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		this.contents = contents.clone();
		this.system = system;

		if (signatures != null && !signatures.isEmpty()) {
			this.signatures = new ArrayList<>(signatures);
		}

		this.readWriteLock = new ReentrantReadWriteLock(false);
	}

	/**
	 * Writes a binary representation of this transaction and optional signatures to a {@link DataOutputStream}.
	 *
	 * Maximum number of bytes written is represented by the formula: contents.length + Byte.BYTES + (4 * Integer.BYTES)
	 * + (signatures.count * max(signature.persistedSize))
	 *
	 * The minimum number of bytes written is represented by the formula: contents.length + Byte.BYTES + (4 *
	 * Integer.BYTES)
	 *
	 * @param transaction
	 * 		the {@link Transaction} object to be serialized
	 * @param dos
	 * 		the {@link DataOutputStream} to which the binary representation should be written
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the
	 * 		existing value by the number of bytes written
	 * @param withSignatures
	 * 		if {@code true} then all signatures are written to the output stream, otherwise no
	 * 		signatures are written to the output stream
	 * @throws IOException
	 * 		if any error occurs while writing to the {@link DataOutputStream}
	 * @throws NullPointerException
	 * 		if the {@code transaction} or {@code dos} parameters are null
	 */
	@Deprecated
	private static void serialize(final Transaction transaction, final DataOutputStream dos, final int[] byteCount,
			final boolean withSignatures) throws IOException {
		if (transaction == null) {
			throw new NullPointerException("transaction");
		}

		if (dos == null) {
			throw new NullPointerException("dos");
		}

		final Lock readLock = transaction.readWriteLock.readLock();

		// Minimize read lock hold time - capture JIT picture of current state
		List<TransactionSignature> jitSnapshot;
		byte[] contents;
		boolean system;

		try {
			readLock.lock();
			jitSnapshot = (transaction.signatures != null) ? new ArrayList<>(transaction.signatures) : null;
			contents = transaction.contents;
			system = transaction.system;
		} finally {
			readLock.unlock();
		}

		final int[] totalBytes = new int[] { (4 * Integer.BYTES) + Byte.BYTES + (contents.length * Byte.BYTES) };

		// Write Content Length w/ Simple Prime Number Checksum
		dos.writeInt(contents.length);
		dos.writeInt(277 - contents.length);

		// Write Content
		dos.writeBoolean(system);
		dos.write(contents);

		// Write Signature Length w/ Simple Prime Number Checksum
		final int sigLen = (withSignatures && jitSnapshot != null) ? jitSnapshot.size() : 0;
		dos.writeInt(sigLen);
		dos.writeInt(353 - sigLen);

		// Write Signatures
		if (withSignatures && jitSnapshot != null) {
			for (TransactionSignature sig : jitSnapshot) {
				sig.writeTo(dos, totalBytes);
			}
		}

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}
	}

	/**
	 * Reconstructs a {@link Transaction} object from a binary representation read from a {@link DataInputStream}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which to read
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the existing
	 * 		value by the number of bytes written
	 * @return the {@link Transaction} that was read from the input stream
	 * @throws IOException
	 * 		if any error occurs while reading from the {@link DataInputStream}
	 * @throws NullPointerException
	 * 		if the {@code dis} parameter is null
	 * @throws BadIOException
	 * 		if the internal checksum cannot be
	 * 		validated
	 */
	@Deprecated
	private static Transaction deserialize(final DataInputStream dis, final int[] byteCount) throws IOException {
		if (dis == null) {
			throw new NullPointerException("dis");
		}

		final int[] totalBytes = new int[] { (4 * Integer.BYTES) + Byte.BYTES };

		// Read Content Length w/ Simple Prime Number Checksum
		final int txLen = dis.readInt();
		final int txChecksum = dis.readInt();

		if (txLen < 0 || txChecksum != (277 - txLen)) {
			throw new BadIOException("Transaction.deserialize tried to create contents array of length "
					+ txLen + " with wrong checksum.");
		}
		if (txLen > SettingsCommon.transactionMaxBytes) {
			throw new BadIOException(String.format(
					"Transaction.deserialize tried to create contents array of length (%d) which is larger than " +
							"maximum allowed size for a transaction (transactionMaxBytes = %d)",
					txLen, SettingsCommon.transactionMaxBytes
			));
		}

		// Read Content
		final boolean system = dis.readBoolean();
		final byte[] contents = new byte[txLen];
		dis.readFully(contents);
		totalBytes[0] += contents.length;

		// Read Signature Length w/ Simple Prime Number Checksum
		final int sigLen = dis.readInt();
		final int sigChecksum = dis.readInt();

		if (sigLen < 0 || sigChecksum != (353 - sigLen)) {
			throw new BadIOException("Transaction.deserialize tried to create signature array of length "
					+ txLen + " with wrong checksum.");
		}

		// Read Signatures
		final TransactionSignature[] sigs = (sigLen > 0) ? new TransactionSignature[sigLen] : null;

		if (sigLen > 0) {
			for (int i = 0; i < sigs.length; i++) {
				sigs[i] = TransactionSignature.deserialize(dis, totalBytes);
			}
		}

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}

		return new Transaction(contents, system, sigs);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		writeTo(out, null);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		Transaction t = deserialize(in, null);
		this.contents = t.contents;
		this.system = t.system;
		this.signatures = t.signatures;
	}

	/**
	 * Writes an array of {@link Transaction} objects to a {@link DataOutputStream}.
	 *
	 * @param trans
	 * 		the array of {@link Transaction} objects to be serialized
	 * @param dos
	 * 		the {@link DataOutputStream} to which the binary representation should be written
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the existing
	 * 		value by the number of bytes written
	 * @throws IOException
	 * 		if any error occurs while writing to the {@link DataOutputStream}
	 * @throws NullPointerException
	 * 		if the {@code trans} or {@code dos} parameters are null
	 */
	@Deprecated
	public static void writeArray(final Transaction[] trans, final DataOutputStream dos, final int[] byteCount)
			throws IOException {
		if (trans == null) {
			throw new NullPointerException("trans");
		}

		if (dos == null) {
			throw new NullPointerException("dos");
		}

		final int[] totalBytes = new int[] { 2 * Integer.BYTES };

		dos.writeInt(trans.length);
		dos.writeInt(1873 - trans.length);

		for (Transaction t : trans) {
			if (t != null) {
				t.writeTo(dos, totalBytes);
			}
		}

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}
	}

	/**
	 * Reconstructs an array of {@link Transaction} objects from a {@link DataInputStream}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which to read
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the existing
	 * 		value by the number of bytes written
	 * @return the array of {@link Transaction} objects that was read from the input stream
	 * @throws IOException
	 * 		if any error occurs while reading from the {@link DataInputStream}
	 * @throws NullPointerException
	 * 		if the {@code dis} parameter is null
	 * @throws BadIOException
	 * 		if the internal checksum cannot be
	 * 		validated
	 */
	@Deprecated
	public static Transaction[] readArray(final DataInputStream dis, final int[] byteCount) throws IOException {
		if (dis == null) {
			throw new NullPointerException("dis");
		}

		final int[] totalBytes = new int[] { 2 * Integer.BYTES };
		final int txLen = dis.readInt();
		final int txChecksum = dis.readInt();

		if (txLen < 0 || txChecksum != (1873 - txLen)) {
			throw new BadIOException("Transaction.readArray tried to create transaction array of length "
					+ txLen + " with wrong checksum.");
		}
		if (txLen > SettingsCommon.maxTransactionCountPerEvent) {
			throw new BadIOException(String.format(
					"Transaction.readArray tried to create contents array of length (%d) which is larger than " +
							"maximum allowed count for transactions (maxTransactionCountPerEvent = %d)",
					txLen, SettingsCommon.maxTransactionCountPerEvent
			));
		}

		final Transaction[] trans = new Transaction[txLen];

		for (int i = 0; i < trans.length; i++) {
			trans[i] = deserialize(dis, totalBytes);
		}

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}

		return trans;
	}

	/**
	 * Returns the transaction content (payload). This method returns a copy of the original content.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the transaction content/payload
	 */
	public byte[] getContents() {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		return contents.clone();
	}

	/**
	 * Returns the byte located at {@code index} position from the transaction content/payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param index
	 * 		the index of the byte to be returned
	 * @return the byte located at {@code index} position
	 * @throws NullPointerException
	 * 		if the underlying transaction content is null or a zero-length array
	 * @throws ArrayIndexOutOfBoundsException
	 * 		if the {@code index} parameter is less than zero or greater than the
	 * 		maximum length of the contents
	 */
	public byte getContents(final int index) {
		if (contents == null || contents.length == 0) {
			throw new NullPointerException("contents");
		}

		if (index < 0 || index >= contents.length) {
			throw new ArrayIndexOutOfBoundsException();
		}

		return contents[index];
	}

	/**
	 * Internal use accessor that returns a direct (mutable) reference to the transaction contents/payload. Care must be
	 * taken to never modify the array returned by this accessor. Modifying the array will result in undefined behaviors
	 * and will result in a violation of the immutability contract provided by the {@link Transaction} object.
	 *
	 * This method exists solely to allow direct access by the platform for performance reasons.
	 *
	 * @return a direct reference to the transaction content/payload
	 */
	public byte[] getContentsDirect() {
		return contents;
	}

	/**
	 * Returns the size of the transaction content/payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the length of the transaction content
	 */
	public int getLength() {
		return (contents != null) ? contents.length : 0;
	}

	/**
	 * Returns the size of the transaction content/payload.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return the length of the transaction content
	 */
	public int size() {
		return getLength();
	}

	/**
	 * Returns a {@link List} of {@link TransactionSignature} objects associated with this transaction. This method returns a
	 * shallow copy of the original list.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @return a shallow copy of the original signature list
	 */
	public List<TransactionSignature> getSignatures() {
		final Lock readLock = readWriteLock.readLock();

		try {
			readLock.lock();
			return (signatures != null) ? new ArrayList<>(signatures) : new ArrayList<>(1);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Internal use accessor that returns a flag indicating whether this is a system transaction.
	 *
	 * @return {@code true} if this is a system transaction; otherwise {@code false} if this is an application
	 * 		transaction
	 */
	public boolean isSystem() {
		return system;
	}

	/**
	 * Updates a cryptographic digest with the transaction payload and signatures.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param digest
	 * 		the message digest to update
	 * @throws NullPointerException
	 * 		if the {@code digest} parameter is null
	 */
	@Deprecated
	public void computeDigest(final MessageDigest digest) {
		computeDigest(digest, false);
	}

	/**
	 * Updates a cryptographic digest with the transaction payload and signatures.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param digest
	 * 		the message digest to update
	 * @param withSignatures
	 * 		if {@code true} then all signatures are included in the digest, otherwise no signatures are
	 * 		included in the digest
	 * @throws NullPointerException
	 * 		if the {@code digest} parameter is null
	 */
	@Deprecated
	private void computeDigest(final MessageDigest digest, final boolean withSignatures) {
		if (digest == null) {
			throw new NullPointerException("digest");
		}

		final Lock readLock = readWriteLock.readLock();

		try {
			readLock.lock();
			HashUtils.update(digest, (system) ? 1 : 0);
			HashUtils.update(digest, contents);
			HashUtils.update(digest, (signatures != null) ? signatures.size() : 0);

			if (withSignatures) {
				for (TransactionSignature s : signatures) {
					s.computeDigest(digest);
				}
			}
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Efficiently extracts and adds a new signature to this transaction bypassing the need to make copies of the
	 * underlying byte arrays.
	 *
	 * @param signatureOffset
	 * 		the offset in the transaction payload where the signature begins
	 * @param signatureLength
	 * 		the length in bytes of the signature
	 * @param publicKeyOffset
	 * 		the offset in the transaction payload where the public key begins
	 * @param publicKeyLength
	 * 		the length in bytes of the public key
	 * @param messageOffset
	 * 		the offset in the transaction payload where the message begins
	 * @param messageLength
	 * 		the length of the message in bytes
	 * @throws IllegalArgumentException
	 * 		if any of the provided offsets or lengths falls outside the array bounds
	 * @throws NullPointerException
	 * 		if the internal payload of this transaction is null or a zero length array
	 */
	public void extractSignature(final int signatureOffset, final int signatureLength, final int publicKeyOffset,
			final int publicKeyLength, final int messageOffset, final int messageLength) {
		add(new TransactionSignature(contents, signatureOffset, signatureLength, publicKeyOffset, publicKeyLength, messageOffset,
				messageLength));
	}

	/**
	 * Efficiently extracts and adds a new signature to this transaction bypassing the need to make copies of the
	 * underlying byte arrays. If the optional expanded public key is provided then the public key offset and length are
	 * indices into this array instead of the transaction payload.
	 *
	 * @param signatureOffset
	 * 		the offset in the transaction payload where the signature begins
	 * @param signatureLength
	 * 		the length in bytes of the signature
	 * @param expandedPublicKey
	 * 		an optional expanded form of the public key
	 * @param publicKeyOffset
	 * 		the offset where the public key begins
	 * @param publicKeyLength
	 * 		the length in bytes of the public key
	 * @param messageOffset
	 * 		the offset in the transaction payload where the message begins
	 * @param messageLength
	 * 		the length of the message in bytes
	 * @throws IllegalArgumentException
	 * 		if any of the provided offsets or lengths falls outside the array bounds
	 * @throws NullPointerException
	 * 		if the internal payload of this transaction is null or a zero length array
	 */
	public void extractSignature(final int signatureOffset, final int signatureLength, final byte[] expandedPublicKey,
			final int publicKeyOffset, final int publicKeyLength, final int messageOffset, final int messageLength) {
		add(new TransactionSignature(contents, signatureOffset, signatureLength, expandedPublicKey, publicKeyOffset,
				publicKeyLength, messageOffset, messageLength));
	}

	/**
	 * Adds a new {@link TransactionSignature} to this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signature
	 * 		the signature to be added
	 * @throws NullPointerException
	 * 		if the {@code signature} parameter is null
	 */
	public void add(final TransactionSignature signature) {
		if (signature == null) {
			throw new NullPointerException("signature");
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (signatures == null) {
				signatures = new ArrayList<>(5);
			}

			signatures.add(signature);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Adds a list of new {@link TransactionSignature} objects to this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signatures
	 * 		the list of signatures to be added
	 */
	public void addAll(final TransactionSignature... signatures) {
		if (signatures == null || signatures.length == 0) {
			return;
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (this.signatures == null) {
				this.signatures = new ArrayList<>(signatures.length);
			}

			this.signatures.addAll(Arrays.asList(signatures));
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Removes a {@link TransactionSignature} from this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signature
	 * 		the signature to be removed
	 * @return {@code true} if the underlying list was modified; {@code false} otherwise
	 */
	public boolean remove(final TransactionSignature signature) {
		if (signature == null) {
			return false;
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (signatures == null) {
				return false;
			}

			return signatures.remove(signature);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Removes a list of {@link TransactionSignature} objects from this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 *
	 * @param signatures
	 * 		the list of signatures to be removed
	 * @return {@code true} if the underlying list was modified; {@code false} otherwise
	 */
	public boolean removeAll(final TransactionSignature... signatures) {
		if (signatures == null || signatures.length == 0) {
			return false;
		}

		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (this.signatures == null) {
				return false;
			}

			return this.signatures.removeAll(Arrays.asList(signatures));
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Removes all signatures from this transaction.
	 *
	 * This method is thread-safe and guaranteed to be atomic in nature.
	 */
	public void clear() {
		final Lock writeLock = readWriteLock.writeLock();

		try {
			writeLock.lock();
			if (signatures == null) {
				return;
			}

			signatures.clear();
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Convenience method for serializing this transaction to a {@link DataOutputStream}. Utilizes the
	 * {@link #serialize(Transaction, DataOutputStream, int[], boolean)} method directly.
	 *
	 * Includes all signatures with the transaction.
	 *
	 * @param dos
	 * 		the {@link DataOutputStream} to which the binary representation should be written
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the existing
	 * 		value by the number of bytes written
	 * @return the cumulative number of bytes written to the output stream
	 * @throws IOException
	 * 		if any error occurs while writing to the {@link DataOutputStream}
	 * @throws NullPointerException
	 * 		if the {@code dos} parameter is null
	 */
	@Deprecated
	private int writeTo(final DataOutputStream dos, int[] byteCount) throws IOException {
		return writeTo(dos, byteCount, false);
	}

	/**
	 * Convenience method for serializing this transaction to a {@link DataOutputStream}. Utilizes the
	 * {@link #serialize(Transaction, DataOutputStream, int[], boolean)} method directly.
	 *
	 * @param dos
	 * 		the {@link DataOutputStream} to which the binary representation should be written
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the
	 * 		existing value by the number of bytes written
	 * @param withSignatures
	 * 		if {@code true} then all signatures are written to the output stream, otherwise no
	 * 		signatures are written to the output stream
	 * @return the cumulative number of bytes written to the output stream
	 * @throws IOException
	 * 		if any error occurs while writing to the {@link DataOutputStream}
	 * @throws NullPointerException
	 * 		if the {@code dos} parameter is null
	 */
	@Deprecated
	private int writeTo(final DataOutputStream dos, int[] byteCount, final boolean withSignatures) throws IOException {
		if (byteCount == null || byteCount.length == 0) {
			byteCount = new int[1];
		}

		serialize(this, dos, byteCount, withSignatures);

		return byteCount[0];
	}

	/**
	 * Returns a hash code value for the object. This method is supported for the benefit of hash tables such as those
	 * provided by {@link HashMap}.
	 * <p>
	 * The general contract of {@code hashCode} is:
	 * <ul>
	 * <li>Whenever it is invoked on the same object more than once during an execution of a Java application, the
	 * {@code hashCode} method must consistently return the same integer, provided no information used in {@code equals}
	 * comparisons on the object is modified. This integer need not remain consistent from one execution of an
	 * application to another execution of the same application.
	 * <li>If two objects are equal according to the {@code equals(Object)} method, then calling the {@code hashCode}
	 * method on each of the two objects must produce the same integer result.
	 * <li>It is <em>not</em> required that if two objects are unequal according to the {@link Object#equals(Object)}
	 * method, then calling the {@code hashCode} method on each of the two objects must produce distinct integer
	 * results. However, the programmer should be aware that producing distinct integer results for unequal objects may
	 * improve the performance of hash tables.
	 * </ul>
	 * <p>
	 * As much as is reasonably practical, the hashCode method defined by class {@code Object} does return distinct
	 * integers for distinct objects. (This is typically implemented by converting the internal address of the object
	 * into an integer, but this implementation technique is not required by the Java&trade; programming language.)
	 *
	 * @return a hash code value for this object.
	 * @see Object#equals(Object)
	 * @see System#identityHashCode
	 */
	@Override
	public int hashCode() {
		int result = Objects.hash(system);
		result = 31 * result + Arrays.hashCode(contents);
		return result;
	}

	/**
	 * Indicates whether some other object is "equal to" this one.
	 * <p>
	 * The {@code equals} method implements an equivalence relation on non-null object references:
	 * <ul>
	 * <li>It is <i>reflexive</i>: for any non-null reference value {@code x}, {@code x.equals(x)} should return
	 * {@code true}.
	 * <li>It is <i>symmetric</i>: for any non-null reference values {@code x} and {@code y}, {@code x.equals(y)} should
	 * return {@code true} if and only if {@code y.equals(x)} returns {@code true}.
	 * <li>It is <i>transitive</i>: for any non-null reference values {@code x}, {@code y}, and {@code z}, if
	 * {@code x.equals(y)} returns {@code true} and {@code y.equals(z)} returns {@code true}, then {@code x.equals(z)}
	 * should return {@code true}.
	 * <li>It is <i>consistent</i>: for any non-null reference values {@code x} and {@code y}, multiple invocations of
	 * {@code x.equals(y)} consistently return {@code true} or consistently return {@code false}, provided no
	 * information used in {@code equals} comparisons on the objects is modified.
	 * <li>For any non-null reference value {@code x}, {@code x.equals(null)} should return {@code false}.
	 * </ul>
	 * <p>
	 * The {@code equals} method for class {@code Object} implements the most discriminating possible equivalence
	 * relation on objects; that is, for any non-null reference values {@code x} and {@code y}, this method returns
	 * {@code true} if and only if {@code x} and {@code y} refer to the same object ({@code x == y} has the value
	 * {@code true}).
	 * <p>
	 * Note that it is generally necessary to override the {@code hashCode} method whenever this method is overridden,
	 * so as to maintain the general contract for the {@code hashCode} method, which states that equal objects must have
	 * equal hash codes.
	 *
	 * @param obj
	 * 		the reference object with which to compare.
	 * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise.
	 * @see #hashCode()
	 * @see HashMap
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Transaction)) {
			return false;
		}
		Transaction that = (Transaction) obj;
		return system == that.system && Arrays.equals(contents, that.contents);
	}

	/**
	 * Compares this object with the specified object for order. Returns a negative integer, zero, or a positive integer
	 * as this object is less than, equal to, or greater than the specified object.
	 *
	 * <p>
	 * The implementor must ensure <code>sgn(x.compareTo(y)) ==
	 * -sgn(y.compareTo(x))</code> for all <code>x</code> and <code>y</code>. (This implies that
	 * <code>x.compareTo(y)</code> must
	 * throw an exception iff <code>y.compareTo(x)</code> throws an exception.)
	 *
	 * <p>
	 * The implementor must also ensure that the relation is transitive:
	 * <code>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</code> implies <code>x.compareTo(z)&gt;0</code>.
	 *
	 * <p>
	 * Finally, the implementor must ensure that <code>x.compareTo(y)==0</code> implies that
	 * <code>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</code>, for all <code>z</code>.
	 *
	 * <p>
	 * It is strongly recommended, but <i>not</i> strictly required that <code>(x.compareTo(y)==0) ==
	 * (x.equals(y))</code>.
	 * Generally speaking, any class that implements the <code>Comparable</code> interface and violates this condition
	 * should clearly indicate this fact. The recommended language is "Note: this class has a natural ordering that is
	 * inconsistent with equals."
	 *
	 * <p>
	 * In the foregoing description, the notation <code>sgn(</code><i>expression</i><code>)</code> designates the
	 * mathematical
	 * <i>signum</i> function, which is defined to return one of <code>-1</code>, <code>0</code>, or <code>1</code>
	 * according to
	 * whether the value of <i>expression</i> is negative, zero or positive.
	 *
	 * @param that
	 * 		the object to be compared.
	 * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
	 * 		the specified object.
	 * @throws NullPointerException
	 * 		if the specified object is null
	 * @throws ClassCastException
	 * 		if the specified object's type prevents it from being compared to this object.
	 */
	@Override
	public int compareTo(final Transaction that) {
		if (this == that) {
			return 0;
		}

		if (that == null) {
			throw new NullPointerException();
		}

		int result = Boolean.compare(system, that.system);

		if (result != 0) {
			return result;
		}

		return Arrays.compare(contents, that.contents);
	}

	@Override
	public String toString() {
		return "Transaction{" +
				"contents=" + Arrays.toString(contents) +
				", signatures=" + signatures +
				", system=" + system +
				'}';
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}
}
