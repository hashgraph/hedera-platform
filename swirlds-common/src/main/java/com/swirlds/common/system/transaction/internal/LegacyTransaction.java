/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.system.transaction.internal;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.exceptions.BadIOException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.TransactionType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;

import static com.swirlds.common.io.streams.SerializableStreamConstants.BOOLEAN_BYTES;
import static com.swirlds.common.system.transaction.TransactionType.APPLICATION;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link TransactionSignature}
 * objects. The contents of the transaction is completely immutable; however, the list of signatures features controlled
 * mutability with a thread-safe and atomic implementation. The transaction internally uses a {@link ReadWriteLock} to
 * provide atomic reads and writes to the underlying list of signatures.
 * <p>
 * Selectively combining controlled mutability for certain aspects while using immutability for the rest grants a
 * significant performance improvement over a completely mutable or completely immutable object.
 * </p>
 *
 * @deprecated
 */
@Deprecated(forRemoval = true)
public class LegacyTransaction implements Transaction {
	private static final long CLASS_ID = 0xa0eda13e329feccaL;
	private static final int CLASS_VERSION = 1;

	private static final int CHECKSUM_CONSTANT_277 = 277;
	private static final int CHECKSUM_CONSTANT_353 = 353;
	private static final String CONTENT_ERROR = "content is null or length is 0";

	/** The content (payload) of the transaction */
	private byte[] contents;

	/** The list of optional signatures attached to this transaction */
	private List<TransactionSignature> signatures;

	/** A flag indicating whether this transaction was originated by the application or the platform */
	private boolean system;

	/**
	 * No-argument constructor used by ConstructableRegistry
	 */
	public LegacyTransaction() {
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
	 * @throws IllegalArgumentException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	private LegacyTransaction(final byte[] contents, final boolean system, final TransactionSignature... signatures) {
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
	 * @throws IllegalArgumentException
	 * 		if the {@code contents} parameter is null or a zero-length array
	 */
	private LegacyTransaction(final byte[] contents, final boolean system,
			final List<TransactionSignature> signatures) {
		if (contents == null || contents.length == 0) {
			throw new IllegalArgumentException(CONTENT_ERROR);
		}

		this.contents = contents.clone();
		this.system = system;

		if (signatures != null && !signatures.isEmpty()) {
			this.signatures = new ArrayList<>(signatures);
		}

	}

	/**
	 * Reconstructs a {@link LegacyTransaction} object from a binary representation read from a {@link DataInputStream}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which to read
	 * @param byteCount
	 * 		returns the number of bytes written as the first element in the array or increments the existing
	 * 		value by the number of bytes written
	 * @return the {@link LegacyTransaction} that was read from the input stream
	 * @throws IOException
	 * 		if any error occurs while reading from the {@link DataInputStream}
	 * @throws IllegalArgumentException
	 * 		if the {@code dis} parameter is null
	 * @throws BadIOException
	 * 		if the internal checksum cannot be
	 * 		validated
	 * @deprecated
	 */
	@Deprecated(forRemoval = true)
	private static LegacyTransaction deserialize(
			final SerializableDataInputStream dis, final int[] byteCount) throws IOException {
		if (dis == null) {
			throw new IllegalArgumentException("dis");
		}

		final int[] totalBytes = new int[] { (4 * Integer.BYTES) + Byte.BYTES };

		// Read Content Length w/ Simple Prime Number Checksum
		final int txLen = dis.readInt();
		final int txChecksum = dis.readInt();

		if (txLen < 0 || txChecksum != (CHECKSUM_CONSTANT_277 - txLen)) {
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

		if (sigLen < 0 || sigChecksum != (CHECKSUM_CONSTANT_353 - sigLen)) {
			throw new BadIOException("Transaction.deserialize tried to create signature array of length "
					+ txLen + " with wrong checksum.");
		}

		// Read Signatures
		final TransactionSignature[] sigs = (sigLen > 0) ? new TransactionSignature[sigLen] : null;

		if (sigs != null) {
			for (int i = 0; i < sigs.length; i++) {
				sigs[i] = TransactionSignature.deserialize(dis, totalBytes);
			}
		}

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}

		return new LegacyTransaction(contents, system, sigs);
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
	 * 		the {@link LegacyTransaction} object to be serialized
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
	 * @throws IllegalArgumentException
	 * 		if the {@code transaction} or {@code dos} parameters are null
	 * @deprecated
	 */
	@Deprecated(forRemoval = true)
	private static void serialize(final LegacyTransaction transaction, final DataOutputStream dos,
			final int[] byteCount,
			final boolean withSignatures) throws IOException {
		if (transaction == null) {
			throw new IllegalArgumentException("transaction");
		}

		if (dos == null) {
			throw new IllegalArgumentException("dos");
		}

		byte[] contents;
		boolean system;
		contents = transaction.contents;
		system = transaction.system;

		final int[] totalBytes = new int[] { (4 * Integer.BYTES) + Byte.BYTES + (contents.length * Byte.BYTES) };

		// Write Content Length w/ Simple Prime Number Checksum
		dos.writeInt(contents.length);
		dos.writeInt(CHECKSUM_CONSTANT_277 - contents.length);

		// Write Content
		dos.writeBoolean(system);
		dos.write(contents);

		// Write Signature Length w/ Simple Prime Number Checksum
		final int sigLen = 0;
		dos.writeInt(0);
		dos.writeInt(CHECKSUM_CONSTANT_353 - sigLen);

		if (byteCount != null && byteCount.length > 0) {
			byteCount[0] += totalBytes[0];
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		// enabled to make unit tests of reading old event stream file working
		serialize(this, out, new int[0], false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		LegacyTransaction t = deserialize(in, null);
		this.contents = t.contents;
		this.system = t.system;
		this.signatures = t.signatures;
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
			throw new IllegalArgumentException(CONTENT_ERROR);
		}

		return contents.clone();
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
		if (!(obj instanceof LegacyTransaction)) {
			return false;
		}
		LegacyTransaction that = (LegacyTransaction) obj;
		return system == that.system && Arrays.equals(contents, that.contents);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	@Override
	public int getSerializedLength() {
		return Integer.BYTES        // size of content length
				+ Integer.BYTES        // size of checksum
				+ BOOLEAN_BYTES    // size of system
				+ (contents == null ? 0 : contents.length)
				+ Integer.BYTES        // size of signature length
				+ Integer.BYTES;    // size of checksum
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransactionType getTransactionType() {
		return APPLICATION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSize() {
		return contents == null ? 0 : contents.length;
	}
}
