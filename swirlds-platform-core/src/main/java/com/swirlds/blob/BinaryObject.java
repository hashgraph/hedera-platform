/*
 * (c) 2016-2021 Swirlds, Inc.
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
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.sql.SQLException;

/**
 * {@link BinaryObject} instances are created by and used with the {@link BinaryObjectStore} API methods. These
 * instances are used to hold the {@link Hash} and the data store primary key associated with the arbitrary binary data
 * inserted into the underlying data store using the {@link BinaryObjectStore#put(byte[])} API method.
 *
 * <p>
 * {@link BinaryObject} instances are immutable, with the exception of deletions.
 * </p>
 */
public class BinaryObject extends AbstractMerkleLeaf implements MerkleExternalLeaf {

	public static final class ClassVersion {
		private ClassVersion() {
		}

		public static final int ORIGINAL = 1;
	}

	/** class identifier for the purposes of serialization */
	public static final long CLASS_ID = 0x12CAC1L;

	/** the database identifier which is not serialized and does not impact the hash */
	private Long id;

	/**
	 * the hash of raw binary object content
	 */
	private Hash hash;

	/**
	 * Constructs a new {@link BinaryObject}
	 */
	public BinaryObject() {
	}

	/**
	 * Constructs a new {@link BinaryObject} instance with the given database identifier and hash.
	 *
	 * @param id
	 * 		the database identifier
	 * @param hash
	 * 		the hash of the contents
	 */
	public BinaryObject(final Long id, final Hash hash) {
		this.id = id;
		this.hash = hash;
	}

	/**
	 * Constructs a new {@link BinaryObject} instance with the given hash.
	 *
	 * @param hash
	 * 		the hash of the contents
	 */
	public BinaryObject(final Hash hash) {
		this.id = null;
		this.hash = hash;
	}

	/**
	 * Copy constructor that increases the reference count of the underlying object but does not make an actual copy.
	 *
	 * @param binaryObject
	 * 		the object to be copied
	 */
	private BinaryObject(final BinaryObject binaryObject) {
		super(binaryObject);
		this.id = binaryObject.getId();
		this.hash = new Hash(binaryObject.getHash());
		this.setImmutable(false);
		binaryObject.setImmutable(true);
		BinaryObjectStore.getInstance().increaseReferenceCount(this);
	}

	/**
	 * Gets the database identifier associated with this {@link BinaryObject}.
	 *
	 * @return the database identifier
	 */
	public long getId() {
		return id;
	}

	/**
	 * Sets the database identifier of the {@link BinaryObject}.
	 *
	 * @param id
	 * 		the database identifier
	 */
	void setId(final Long id) {
		throwIfImmutable();
		this.id = id;
	}

	/**
	 * {@inheritDoc}
	 */
	public Hash getHash() {
		return hash;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setHash(final Hash hash) {
		throw new UnsupportedOperationException("Can't mutate hash of BinaryObject");
	}

	/**
	 * This method is intentionally a no-op.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void invalidateHash() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized BinaryObject copy() {
		throwIfImmutable();
		throwIfReleased();
		return new BinaryObject(this);
	}

	/**
	 * Dereferences the underlying data store and marks this instance as deleted. If the underlying reference count
	 * drops to zero, then the underlying database objects are deleted.
	 *
	 * @param pipeline
	 * 		the existing {@link BlobStoragePipeline} to be used during the deletion
	 * @throws SQLException
	 * 		if an error occurs while deleting this {@link BinaryObject}
	 */
	synchronized void delete(final BlobStoragePipeline pipeline) throws SQLException {
		if (id != null) {
			pipeline.delete(id);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		final BinaryObjectStore bos = BinaryObjectStore.getInstance();
		byte[] content = bos.get(this);
		dos.writeInt(content.length);
		dos.write(content);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream inputStream, final int version) throws IOException {
		final int contentLength = inputStream.readInt();
		final byte[] content = new byte[contentLength];
		inputStream.readFully(content);

		final BinaryObjectStore bos = BinaryObjectStore.getInstance();
		final BinaryObject bo = bos.put(content);
		this.setId(bo.getId());
		this.hash = bo.getHash();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isDataExternal() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof BinaryObject)) {
			return false;
		}

		final BinaryObject object = (BinaryObject) o;

		return new EqualsBuilder()
				.append(hash, object.hash)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(hash)
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return (hash != null) ? hash.toString() : "";
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
	public void serializeAbbreviated(SerializableDataOutputStream out) {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserializeAbbreviated(SerializableDataInputStream in, Hash hash, int version) {
		this.hash = hash;
		BinaryObjectStore.getInstance().registerForRecovery(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onRelease() {
		BinaryObjectStore.getInstance().delete(this);
	}
}
