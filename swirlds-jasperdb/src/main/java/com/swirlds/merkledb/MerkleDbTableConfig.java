/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.merkledb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.merkledb.settings.MerkleDbSettings;
import com.swirlds.merkledb.settings.MerkleDbSettingsFactory;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.util.Objects;

/**
 * Virtual database table configuration. It describes how to store virtual keys and values and a few
 * other params like whether to prefer disk based indexes or not. These table configs are stored in
 * virtual database metadata and persisted across JVM runs.
 *
 * @param <K> Virtual key type
 * @param <V> Virtual value type
 */
public final class MerkleDbTableConfig<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements SelfSerializable {

    private static final long CLASS_ID = 0xbb41e7eb9fcad23cL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link MerkleDbSettingsFactory}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbSettings settings = MerkleDbSettingsFactory.get();

    /** Hash version. */
    private short hashVersion;

    /** Hash type. Only SHA_384 is supported yet. */
    private DigestType hashType;

    /** Key version. Used for data version updates and migration. */
    private short keyVersion;

    /** Key serializer. Used to serialize and deserialize keys in data files. */
    private KeySerializer<K> keySerializer;

    /** Value version. Used for data version updates and migration. */
    private short valueVersion;

    /** Value serializer. Used to serialize and deserialize keys in data files. */
    private ValueSerializer<V> valueSerializer;

    /** Max number of keys that can be stored in a table. */
    private long maxNumberOfKeys = settings.getMaxNumOfKeys();

    /**
     * Threshold where we switch from storing internal hashes in ram to storing them on disk. If it
     * is 0 then everything is on disk, if it is Long.MAX_VALUE then everything is in ram. Any value
     * in the middle is the path value at which we swap from ram to disk. This allows a tree where
     * the lower levels of the tree nodes hashes are in ram and the upper larger less changing
     * layers are on disk.
     */
    private long internalHashesRamToDiskThreshold = settings.getInternalHashesRamToDiskThreshold();

    /** Indicates whether to store indexes on disk or in Java heap/off-heap memory. */
    private boolean preferDiskBasedIndices = false;

    /**
     * Creates a new virtual table config with default values. This constructor should only be used
     * for deserialization.
     */
    public MerkleDbTableConfig() {
        // required for deserialization
    }

    /**
     * Creates a new virtual table config with the specified params.
     *
     * @param hashVersion Hash version
     * @param hashType Hash type
     * @param keyVersion Key version
     * @param keySerializer Key serializer. Must not be null
     * @param valueVersion Value version
     * @param valueSerializer Value serialzier. Must not be null
     */
    public MerkleDbTableConfig(
            final short hashVersion,
            final DigestType hashType,
            final short keyVersion,
            final KeySerializer<K> keySerializer,
            final short valueVersion,
            final ValueSerializer<V> valueSerializer) {
        this.hashVersion = hashVersion;
        this.hashType = hashType;
        this.keyVersion = keyVersion;
        if (keySerializer == null) {
            throw new IllegalArgumentException("Null key serializer");
        }
        this.keySerializer = keySerializer;
        this.valueVersion = valueVersion;
        if (valueSerializer == null) {
            throw new IllegalArgumentException("Null value serializer");
        }
        this.valueSerializer = valueSerializer;
    }

    /**
     * Hash version.
     *
     * @return Hash version
     */
    public short getHashVersion() {
        return hashVersion;
    }

    /**
     * Hash type.
     *
     * @return Hash type
     */
    public DigestType getHashType() {
        return hashType;
    }

    /**
     * Key version
     *
     * @return Key version
     */
    public short getKeyVersion() {
        return keyVersion;
    }

    /**
     * Key serializer.
     *
     * @return Key serializer
     */
    public KeySerializer<K> getKeySerializer() {
        return keySerializer;
    }

    /**
     * Value version.
     *
     * @return Value version
     */
    public short getValueVersion() {
        return valueVersion;
    }

    /**
     * Value serializer
     *
     * @return Value serializer
     */
    public ValueSerializer<V> getValueSerializer() {
        return valueSerializer;
    }

    /**
     * Max number of keys that can be stored in the table.
     *
     * @return Max number of keys
     */
    public long getMaxNumberOfKeys() {
        return maxNumberOfKeys;
    }

    /**
     * Specifies the max number of keys that can be stored in the table.
     *
     * @param maxNumberOfKeys Max number of keys
     * @return This table config object
     */
    public MerkleDbTableConfig<K, V> maxNumberOfKeys(final long maxNumberOfKeys) {
        this.maxNumberOfKeys = maxNumberOfKeys;
        return this;
    }

    /**
     * Internal hashes RAM/disk threshold. Value {@code 0} means all hashes are to be stored on
     * disk. Value {@link Integer#MAX_VALUE} indicates that all hashes are to be stored in memory.
     *
     * @return Internal hashes RAM/disk threshold
     */
    public long getInternalHashesRamToDiskThreshold() {
        return internalHashesRamToDiskThreshold;
    }

    /**
     * Specifies internal hashes RAM/disk threshold.
     *
     * @param internalHashesRamToDiskThreshold Internal hashes RAM/disk threshold
     * @return This table config object
     */
    public MerkleDbTableConfig<K, V> internalHashesRamToDiskThreshold(
            final long internalHashesRamToDiskThreshold) {
        this.internalHashesRamToDiskThreshold = internalHashesRamToDiskThreshold;
        return this;
    }

    /**
     * Whether indexes are stored on disk or in Java heap/off-heap memory.
     *
     * @return Whether disk based indexes are preferred
     */
    public boolean isPreferDiskBasedIndices() {
        return preferDiskBasedIndices;
    }

    /**
     * Specifies whether indexes are to be stored on disk or in Java heap/off-heap memory.
     *
     * @param preferDiskBasedIndices Whether disk based indexes are preferred
     * @return This table config object
     */
    public MerkleDbTableConfig<K, V> preferDiskIndices(final boolean preferDiskBasedIndices) {
        this.preferDiskBasedIndices = preferDiskBasedIndices;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(preferDiskBasedIndices);
        out.writeLong(maxNumberOfKeys);
        out.writeLong(internalHashesRamToDiskThreshold);
        out.writeShort(hashVersion);
        out.writeInt(hashType.id());
        out.writeShort(keyVersion);
        out.writeSerializable(keySerializer, true);
        out.writeShort(valueVersion);
        out.writeSerializable(valueSerializer, true);
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        preferDiskBasedIndices = in.readBoolean();
        maxNumberOfKeys = in.readLong();
        internalHashesRamToDiskThreshold = in.readLong();
        hashVersion = in.readShort();
        hashType = DigestType.valueOf(in.readInt());
        keyVersion = in.readShort();
        keySerializer = in.readSerializable();
        valueVersion = in.readShort();
        valueSerializer = in.readSerializable();
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(
                hashVersion,
                hashType,
                keyVersion,
                keySerializer,
                valueVersion,
                valueSerializer,
                preferDiskBasedIndices,
                maxNumberOfKeys,
                internalHashesRamToDiskThreshold);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof MerkleDbTableConfig<?, ?> other)) {
            return false;
        }
        return (preferDiskBasedIndices == other.preferDiskBasedIndices)
                && (maxNumberOfKeys == other.maxNumberOfKeys)
                && (internalHashesRamToDiskThreshold == other.internalHashesRamToDiskThreshold)
                && (hashVersion == other.hashVersion)
                && Objects.equals(hashType, other.hashType)
                && (keyVersion == other.keyVersion)
                && Objects.equals(keySerializer, other.keySerializer)
                && (valueVersion == other.valueVersion)
                && Objects.equals(valueSerializer, other.valueSerializer);
    }
}
