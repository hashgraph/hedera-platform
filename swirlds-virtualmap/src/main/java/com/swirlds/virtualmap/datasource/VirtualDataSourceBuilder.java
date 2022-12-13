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
package com.swirlds.virtualmap.datasource;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import java.nio.file.Path;

/**
 * Builds {@link VirtualDataSource}s. Each built data source has a "name". This name is used, along
 * with the path, to form a directory into which the database will be created (or loaded from).
 *
 * @param <K> The key
 * @param <V> The value
 */
public interface VirtualDataSourceBuilder<K extends VirtualKey<? super K>, V extends VirtualValue>
        extends SelfSerializable {
    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and the given
     * name.
     *
     * @param name The name. Cannot be null. If an existing directory exists, the returned {@link
     *     VirtualDataSource} will be opened onto that database. This name must be posix compliant.
     * @param label A label. Can be null.
     * @param withDbCompactionEnabled If true then the new database will have background compaction
     *     enabled, false and the new database will not have background compaction enabled.
     * @return An opened {@link VirtualDataSource}.
     */
    VirtualDataSource<K, V> build(String name, String label, final boolean withDbCompactionEnabled);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and the given
     * name by creating a snapshot of the given data source.
     *
     * @param name The name. Cannot be null. If an existing directory exists, the returned {@link
     *     VirtualDataSource} will be opened onto that database. This name must be posix compliant.
     * @param label A label. Can be null.
     * @param snapshotMe The dataSource to invoke snapshot on. Cannot be null.
     * @param withDbCompactionEnabled If true then the new database will have background compaction
     *     enabled, false and the new database will not have background compaction enabled.
     * @return An opened {@link VirtualDataSource}.
     */
    VirtualDataSource<K, V> build(
            String name,
            String label,
            VirtualDataSource<K, V> snapshotMe,
            boolean withDbCompactionEnabled);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and the given
     * name by creating a snapshot of the given data source.
     *
     * @param label A label. Can be null.
     * @param to The path into which to snapshot the database
     * @param snapshotMe The dataSource to invoke snapshot on. Cannot be null.
     * @param withDbCompactionEnabled If true then the new database will have background compaction
     *     enabled, false and the new database will not have background compaction enabled.
     * @return An opened {@link VirtualDataSource}.
     */
    VirtualDataSource<K, V> build(
            String label,
            Path to,
            VirtualDataSource<K, V> snapshotMe,
            boolean withDbCompactionEnabled);

    /**
     * Builds a new {@link VirtualDataSource} using the configuration of this builder and the given
     * name by copying all the database files from the given path into the new database directory
     * and then opening that database.
     *
     * @param name The name. Cannot be null. This must not refer to an existing database. This name
     *     must be posix compliant.
     * @param label A label. Can be null.
     * @param from The path of the database from which to copy all the database files. Cannot be
     *     null.
     * @param withDbCompactionEnabled If true then the new database will have background compaction
     *     enabled, false and the new database will not have background compaction enabled.
     * @return An opened {@link VirtualDataSource}.
     */
    VirtualDataSource<K, V> build(
            String name, String label, Path from, final boolean withDbCompactionEnabled);
}
