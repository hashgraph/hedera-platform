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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.nio.file.Path;

/**
 * Builds {@link VirtualDataSource}s. Each built data source has a "name". This name is used, along with the
 * path, to form a directory into which the database will be created (or loaded from).
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public interface VirtualDataSourceBuilder<K extends VirtualKey<? super K>, V extends VirtualValue>
		extends SelfSerializable {
	/**
	 * Builds a new {@link VirtualDataSource} using the configuration of this builder and
	 * the given name.
	 *
	 * @param name
	 * 		The name. Cannot be null. If an existing directory exists, the returned
	 * 		{@link VirtualDataSource} will be opened onto that database. This name
	 * 		must be posix compliant.
	 * @param label
	 * 		A label. Can be null.
	 * @param withDbCompactionEnabled
	 * 		If true then the new database will have background compaction enabled, false and the new database will not
	 * 		have background compaction enabled.
	 * @return
	 * 		An opened {@link VirtualDataSource}.
	 */
	VirtualDataSource<K, V> build(String name, String label, final boolean withDbCompactionEnabled);

	/**
	 * Builds a new {@link VirtualDataSource} using the configuration of this builder and
	 * the given name by creating a snapshot of the given data source.
	 *
	 * @param name
	 * 		The name. Cannot be null. If an existing directory exists, the returned
	 * 		{@link VirtualDataSource} will be opened onto that database. This name
	 * 		must be posix compliant.
	 * @param label
	 * 		A label. Can be null.
	 * @param snapshotMe
	 * 		The dataSource to invoke snapshot on. Cannot be null.
	 * @param withDbCompactionEnabled
	 * 		If true then the new database will have background compaction enabled, false and the new database will not
	 * 		have background compaction enabled.
	 * @return An opened {@link VirtualDataSource}.
	 */
	VirtualDataSource<K, V> build(String name, String label, VirtualDataSource<K, V> snapshotMe,
			boolean withDbCompactionEnabled);

	/**
	 * Builds a new {@link VirtualDataSource} using the configuration of this builder and
	 * the given name by creating a snapshot of the given data source.
	 *
	 * @param label
	 * 		A label. Can be null.
	 * @param to
	 * 		The path into which to snapshot the database
	 * @param snapshotMe
	 * 		The dataSource to invoke snapshot on. Cannot be null.
	 * @param withDbCompactionEnabled
	 * 		If true then the new database will have background compaction enabled, false and the new database will not
	 * 		have background compaction enabled.
	 * @return An opened {@link VirtualDataSource}.
	 */
	VirtualDataSource<K, V> build(String label, Path to, VirtualDataSource<K, V> snapshotMe,
			boolean withDbCompactionEnabled);

	/**
	 * Builds a new {@link VirtualDataSource} using the configuration of this builder and
	 * the given name by copying all the database files from the given path into the new
	 * database directory and then opening that database.
	 *
	 * @param name
	 * 		The name. Cannot be null. This must not refer to an existing database. This name
	 * 		must be posix compliant.
	 * @param label
	 * 		A label. Can be null.
	 * @param from
	 * 		The path of the database from which to copy all the database files. Cannot be null.
	 * @param withDbCompactionEnabled
	 * 		If true then the new database will have background compaction enabled, false and the new database will not
	 * 		have background compaction enabled.
	 * @return
	 * 		An opened {@link VirtualDataSource}.
	 */
	VirtualDataSource<K, V> build(String name, String label, Path from, final boolean withDbCompactionEnabled);
}
