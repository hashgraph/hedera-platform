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

package com.swirlds.jasperdb;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.jasperdb.settings.JasperDbSettings;
import com.swirlds.jasperdb.settings.JasperDbSettingsFactory;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import static com.swirlds.common.CommonUtils.hardLinkTree;

/**
 * An implementation of {@link VirtualDataSourceBuilder} for building {@link VirtualDataSourceJasperDB}
 * data sources. Some fields have default values that are read from settings, while others are required.
 * Please see each setter to understand which are optional and which are required.
 *
 * @param <K>
 * 		The key.
 * @param <V>
 * 		The value.
 */
public class JasperDbBuilder<K extends VirtualKey<? super K>, V extends VirtualValue>
		implements VirtualDataSourceBuilder<K, V> {
    
	private static final long CLASS_ID = 0xe3f6da254983b38cL;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
		public static final int REMOVE_MERGE_ENABLED_FLAG = 2;
	}
  
	/**
	 * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before
	 * any application classes that might instantiate a data source, the {@link JasperDbSettingsFactory}
	 * holder will have been configured by the time this static initializer runs.
	 */
	private final JasperDbSettings settings = JasperDbSettingsFactory.get();

	/**
	 * The base directory in which the database directory will be created. By default picked up from settings.
	 */
	private Path storageDir = Path.of(settings.getStoragePath());
	/**
	 * If the database should prefer to use the disk for its indexes. The default is false which should be correct for
	 * most production use cases. Only set to true if you need to open a database using the minimum RAM possible and can
	 * afford to take the performance hit.
	 */
	private boolean preferDiskBasedIndexes = false;
	/**
	 * The maximum number of unique keys we expect to be stored in this database. This is used for calculating in
	 * memory index sizes.
	 * <p>The default value is loaded from settings.</p>
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 */
	private long maxNumOfKeys = settings.getMaxNumOfKeys();
	/**
	 * Threshold where we switch from storing internal hashes in ram to storing them on disk. If it is 0 then everything
	 * is on disk, if it is Long.MAX_VALUE then everything is in ram. Any value in the middle is the path value at which
	 * we swap from ram to disk. This allows a tree where the lower levels of the tree nodes hashes are in ram and the
	 * upper larger less changing layers are on disk.
	 * <p>The default value is loaded from settings.</p>
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 */
	private long internalHashesRamToDiskThreshold = settings.getInternalHashesRamToDiskThreshold();
	/**
	 * Serializer for converting raw data to/from VirtualLeafRecords
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 * <p><b>must be specified</b></p>
	 */
	private VirtualLeafRecordSerializer<K, V> virtualLeafRecordSerializer;
	/**
	 * Serializer for converting raw data to/from VirtualInternalRecords.
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 * <p><b>must be specified</b></p>
	 */
	private VirtualInternalRecordSerializer virtualInternalRecordSerializer = new VirtualInternalRecordSerializer();
	/**
	 * Serializer for converting raw data to/from keys.
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 * <p><b>must be specified</b></p>
	 */
	private KeySerializer<K> keySerializer;

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
		return ClassVersion.REMOVE_MERGE_ENABLED_FLAG;
	}

	/**
	 * Specify the serializer for converting raw data to/from VirtualLeafRecords.
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 * <p><b>must be specified</b></p>
	 */
	public JasperDbBuilder<K, V> virtualLeafRecordSerializer(final VirtualLeafRecordSerializer<K, V> serializer) {
		this.virtualLeafRecordSerializer = Objects.requireNonNull(serializer);
		return this;
	}

	/**
	 * Specify the serializer for converting raw data to/from VirtualInternalRecords.
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 * <p><b>must be specified</b></p>
	 */
	public JasperDbBuilder<K, V> virtualInternalRecordSerializer(final VirtualInternalRecordSerializer serializer) {
		this.virtualInternalRecordSerializer = Objects.requireNonNull(serializer);
		return this;
	}

	/**
	 * Specify the serializer for converting raw data to/from keys.
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 * <p><b>must be specified</b></p>
	 */
	public JasperDbBuilder<K, V> keySerializer(final KeySerializer<K> serializer) {
		this.keySerializer = Objects.requireNonNull(serializer);
		return this;
	}

	/**
	 * Specify the base directory in which the database directory will be created. The database will be created as a
	 * sub-directory of this storage directory. By default, it is loaded from settings.
	 * @see com.swirlds.jasperdb.settings.DefaultJasperDbSettings
	 */
	public JasperDbBuilder<K, V> storageDir(final Path dir) {
		this.storageDir = Objects.requireNonNull(dir);
		return this;
	}

	/**
	 * Specify the maximum number of unique keys we expect to be stored in this database. This is used for
	 * calculating in
	 * memory index sizes.
	 * <p>The default value is loaded from settings.</p>
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 */
	public JasperDbBuilder<K, V> maxNumOfKeys(final long value) {
		if (value <= 0) {
			throw new IllegalArgumentException("maxNumOfKeys should be > 0");
		}
		this.maxNumOfKeys = value;
		return this;
	}

	/**
	 * Specify the threshold where we switch from storing internal hashes in ram to storing them on disk. If it is 0
	 * then everything is on disk, if it is Long.MAX_VALUE then everything is in ram. Any value in the middle is the
	 * path
	 * value at which we swap from ram to disk. This allows a tree where the lower levels of the tree nodes hashes
	 * are in
	 * ram and the upper larger less changing layers are on disk.
	 * <p>The default value is loaded from settings.</p>
	 * <p><b>IMPORTANT: This can only be set before a new database is created, changing on an existing database will
	 * break it.</b></p>
	 */
	public JasperDbBuilder<K, V> internalHashesRamToDiskThreshold(final long value) {
		if (value < 0) {
			throw new IllegalArgumentException("internalHashesRamToDiskThreshold should be >= 0");
		}

		this.internalHashesRamToDiskThreshold = value;
		return this;
	}

	/**
	 * Specify if the database should prefer to use the disk for its indexes. The default is false which should be
	 * correct for
	 * most production use cases. Only set to true if you need to open a database using the minimum RAM possible and can
	 * afford to take the performance hit.
	 */
	public JasperDbBuilder<K, V> preferDiskBasedIndexes(final boolean value) {
		this.preferDiskBasedIndexes = value;
		return this;
	}

	/**
	 * Build a database with the given name and label. The name is used for the directory name for the database, as a
	 * direct subdirectory of the specified storageDir. The label is used for logs and stats reporting.
	 *
	 * @param name
	 * 		The name. Cannot be null. If an existing directory exists, the returned
	 *        {@link VirtualDataSource} will be opened onto that database. This name
	 * 		must be posix compliant.
	 * @param label
	 * 		A label. Can be null.
	 * @param withDbCompactionEnabled
	 * 		If true then the new database will have background compaction enabled, false and the new database will not
	 * 		have background compaction enabled.
	 * @return New configured VirtualDataSourceJasperDB instance
	 */
	@Override
	public VirtualDataSourceJasperDB<K, V> build(final String name, final String label,
			final boolean withDbCompactionEnabled) {
		validateBuilderState();

		try {
			return createDataSource(
					virtualLeafRecordSerializer,
					virtualInternalRecordSerializer,
					keySerializer,
					storageDir.resolve(name),
					label,
					maxNumOfKeys,
					withDbCompactionEnabled,
					internalHashesRamToDiskThreshold,
					preferDiskBasedIndexes);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Build a database as a snapshot of the database "snapshotMe" with the given name and label. The name is used for
	 * the directory name for the database, as a direct subdirectory of the specified storageDir. The label is used for
	 * logs and stats reporting.
	 *
	 * @param name
	 * 		The name. Cannot be null. If an existing directory exists, the returned
	 *        {@link VirtualDataSource} will be opened onto that database. This name
	 * 		must be posix compliant.
	 * @param label
	 * 		A label. Can be null.
	 * @param snapshotMe
	 * 		The dataSource to invoke snapshot on. Cannot be null.
	 * @param withDbCompactionEnabled
	 * 		If true then the new database will have background compaction enabled, false and the new database will not
	 * 		have background compaction enabled.
	 * @return New configured VirtualDataSourceJasperDB instance
	 */
	@Override
	public VirtualDataSourceJasperDB<K, V> build(final String name, final String label,
			final VirtualDataSource<K, V> snapshotMe, boolean withDbCompactionEnabled) {
		return build(label, storageDir.resolve(name), snapshotMe, withDbCompactionEnabled);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualDataSourceJasperDB<K, V> build(final String label, final Path to,
			final VirtualDataSource<K, V> snapshotMe, boolean withDbCompactionEnabled) {
		validateBuilderState();

		if (!(snapshotMe instanceof VirtualDataSourceJasperDB)) {
			throw new IllegalArgumentException("The datasource must be compatible with the jasperdb");
		}

		try {
			snapshotMe.snapshot(to);
			return createDataSource(
					virtualLeafRecordSerializer,
					virtualInternalRecordSerializer,
					keySerializer,
					to,
					label,
					maxNumOfKeys,
					withDbCompactionEnabled,
					internalHashesRamToDiskThreshold,
					preferDiskBasedIndexes);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Build a database as a copy of the database in the directory "from" with the given name and label. The name is
	 * used for the directory name for the database, as a direct subdirectory of the specified storageDir. The label is
	 * used for logs and stats reporting.
	 *
	 * @param name
	 * 		The name. Cannot be null. This must not refer to an existing database. This name
	 * 		must be posix compliant.
	 * @param label
	 * 		A label. Can be null.
	 * @param from
	 * 		The path of the database from which to copy all the database files. Cannot be null.
	 * @param withDbCompactionEnabled
	 * @return New configured VirtualDataSourceJasperDB instance
	 */
	@Override
	public VirtualDataSource<K, V> build(final String name, final String label, final Path from,
			final boolean withDbCompactionEnabled) {
		final Path to = storageDir.resolve(name);
		hardLinkTree(from.toFile(), to.toFile());

		try {
			return createDataSource(
					virtualLeafRecordSerializer,
					virtualInternalRecordSerializer,
					keySerializer,
					to,
					label,
					maxNumOfKeys,
					withDbCompactionEnabled,
					internalHashesRamToDiskThreshold,
					preferDiskBasedIndexes);
		} catch (final IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Validate that all settings that have to be set have been set
	 */
	private void validateBuilderState() {
		if (virtualLeafRecordSerializer == null) {
			throw new IllegalArgumentException("virtualLeafRecordSerializer must be specified");
		}

		if (keySerializer == null) {
			throw new IllegalArgumentException("keySerializer must be specified");
		}

		if (storageDir == null) {
			throw new IllegalArgumentException("storageDir must be specified");
		}
	}

	/**
	 * Created to enable tests. I really don't like doing things this way, but I couldn't find a better way
	 * to test this because VirtualDataSourceJasperDB doesn't give access to these variables once processed
	 * in the constructor (and really shouldn't anyway). So I needed to intercept creation in a way that
	 * Mockito could interject.
	 */
	VirtualDataSourceJasperDB<K, V> createDataSource(
			final VirtualLeafRecordSerializer<K, V> virtualLeafRecordSerializer,
			final VirtualInternalRecordSerializer virtualInternalRecordSerializer,
			final KeySerializer<K> keySerializer,
			final Path storageDir, final String label, final long maxNumOfKeys, final boolean mergingEnabled,
			final long internalHashesRamToDiskThreshold, final boolean preferDiskBasedIndexes) throws IOException {

		return new VirtualDataSourceJasperDB<>(virtualLeafRecordSerializer, virtualInternalRecordSerializer,
				keySerializer, storageDir, label, maxNumOfKeys, mergingEnabled, internalHashesRamToDiskThreshold,
				preferDiskBasedIndexes);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeBoolean(preferDiskBasedIndexes);
		out.writeLong(maxNumOfKeys);
		out.writeLong(internalHashesRamToDiskThreshold);
		out.writeSerializable(virtualLeafRecordSerializer, false);
		out.writeSerializable(virtualInternalRecordSerializer, false);
		out.writeSerializable(keySerializer, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		if (version == ClassVersion.ORIGINAL) {
			in.readBoolean(); // ignored
		}
		preferDiskBasedIndexes = in.readBoolean();
		maxNumOfKeys = in.readLong();
		internalHashesRamToDiskThreshold = in.readLong();
		virtualLeafRecordSerializer = in.readSerializable(false, VirtualLeafRecordSerializer::new);
		virtualInternalRecordSerializer = in.readSerializable(false, VirtualInternalRecordSerializer::new);
		keySerializer = in.readSerializable();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final JasperDbBuilder<?, ?> that = (JasperDbBuilder<?, ?>) o;

		// Storage dir is intentionally omitted
		return  preferDiskBasedIndexes == that.preferDiskBasedIndexes &&
				maxNumOfKeys == that.maxNumOfKeys &&
				internalHashesRamToDiskThreshold == that.internalHashesRamToDiskThreshold &&
				Objects.equals(virtualLeafRecordSerializer, that.virtualLeafRecordSerializer) &&
				Objects.equals(virtualInternalRecordSerializer, that.virtualInternalRecordSerializer) &&
				Objects.equals(keySerializer, that.keySerializer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(storageDir, preferDiskBasedIndexes, maxNumOfKeys,
				internalHashesRamToDiskThreshold, virtualLeafRecordSerializer, virtualInternalRecordSerializer,
				keySerializer);
	}
}
