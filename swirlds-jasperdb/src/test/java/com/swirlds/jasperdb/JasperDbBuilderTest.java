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
package com.swirlds.jasperdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.jasperdb.settings.DefaultJasperDbSettings;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasperDbBuilderTest {

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    Path testDirectory;

    // Creates DIR_PREFIX/testBuilder/wombat
    private VirtualLeafRecordSerializer<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>
            leafSerializer;
    private VirtualInternalRecordSerializer internalSerializer;
    private KeySerializer<ExampleLongKeyFixedSize> keySerializer;
    private TestBuilder builder;

    @BeforeEach
    public void setup() {
        // Creates DIR_PREFIX/testBuilder/wombat
        leafSerializer =
                new VirtualLeafRecordSerializer<>(
                        (short) 1,
                        DigestType.SHA_384,
                        (short) 1,
                        Long.BYTES,
                        new ExampleLongKeyFixedSize.Builder(),
                        (short) 1,
                        Long.BYTES,
                        new ExampleFixedSizeVirtualValue.Builder(),
                        true);

        internalSerializer = new VirtualInternalRecordSerializer();
        keySerializer = new ExampleLongKeyFixedSize.Serializer();

        builder = new TestBuilder();
    }

    @AfterEach
    public void afterCheckNoDbLeftOpen() {
        // check db count
        assertEquals(
                0, VirtualDataSourceJasperDB.getCountOfOpenDatabases(), "Expected no open dbs");
    }

    /** Test that all the builder state ends up being passed along to the data source */
    @Test
    @DisplayName("Test builder state is passed to data source")
    void testBuilder() {
        builder.maxNumOfKeys(10)
                .virtualLeafRecordSerializer(leafSerializer)
                .virtualInternalRecordSerializer(internalSerializer)
                .keySerializer(keySerializer)
                .storageDir(testDirectory.resolve("testBuilder"))
                .internalHashesRamToDiskThreshold(25)
                .preferDiskBasedIndexes(true)
                .build("wombat", "wombat", true);

        assertEquals(10, builder.maxNumOfKeysRef.get(), "Unexpected maxNumOfKeys");
        assertEquals(leafSerializer, builder.leafSerializerRef.get(), "Unexpected leafSerializer");
        assertEquals(
                internalSerializer,
                builder.internalSerializerRef.get(),
                "Unexpected internalSerializer");
        assertEquals(keySerializer, builder.keySerializerRef.get(), "Unexpected keySerializer");
        assertEquals(
                testDirectory.resolve("testBuilder/wombat"),
                builder.storageDirRef.get(),
                "The path should have included the path and name");
        assertEquals(
                25,
                builder.internalHashesRamToDiskThresholdRef.get(),
                "Unexpected internalHashesRamToDiskThreshold");
        assertTrue(builder.preferDiskBasedIndexesRef.get(), "Unexpected preferDiskBasedIndexes");
    }

    /**
     * Test that all the DefaultJasperDbSettings (storageDir, maxNumOfKeys, and
     * internalHashesRamToDiskThreshold) end up being passed along to the data source.
     */
    @Test
    @DisplayName("Test default settings (when not overridden) are passed to data source")
    void testDefaultJasperDbSettingsAreIncludedInBuilder() {
        // these two serializers are the minimum *required* set of specifications for the builder.
        builder.virtualLeafRecordSerializer(leafSerializer)
                .keySerializer(keySerializer)
                .build("wombat", "wombat", true);

        final DefaultJasperDbSettings defaultSettings = new DefaultJasperDbSettings();
        assertEquals(
                defaultSettings.getMaxNumOfKeys(),
                builder.maxNumOfKeysRef.get(),
                "Unexpected maxNumOfKeys");
        assertEquals(
                defaultSettings.getInternalHashesRamToDiskThreshold(),
                builder.internalHashesRamToDiskThresholdRef.get(),
                "Unexpected internalHashesRamToDiskThreshold");
    }

    @Test
    @DisplayName("leafSerializer cannot be null")
    void leafSerializer_cannotBeNull() {
        assertThrows(
                NullPointerException.class,
                () -> builder.virtualLeafRecordSerializer(null),
                "Leaf serializer was null!");
    }

    @Test
    @DisplayName("leafSerializer must be specified")
    void leafSerializer_mustBeSpecified() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.maxNumOfKeys(10)
                                .virtualInternalRecordSerializer(internalSerializer)
                                .keySerializer(keySerializer)
                                .storageDir(testDirectory.resolve("testBuilder"))
                                .internalHashesRamToDiskThreshold(25)
                                .preferDiskBasedIndexes(true)
                                .build("wombat", "wombat", false),
                "Leaf serializer should have been specified");
    }

    @Test
    @DisplayName("internalSerializer cannot be null")
    void internalSerializer_cannotBeNull() {
        assertThrows(
                NullPointerException.class,
                () -> builder.virtualInternalRecordSerializer(null),
                "Internal serializer initializer was null!");
    }

    @Test
    @DisplayName("internalSerializer is optional")
    void internalSerializer_mustBeSpecified() {
        builder.maxNumOfKeys(10)
                .virtualLeafRecordSerializer(leafSerializer)
                .keySerializer(keySerializer)
                .storageDir(testDirectory.resolve("testBuilder"))
                .internalHashesRamToDiskThreshold(25)
                .preferDiskBasedIndexes(true)
                .build("wombat", "wombat", false);

        assertNotNull(builder.internalSerializerRef.get(), "A default value should have existed");
    }

    @Test
    @DisplayName("keySerializer cannot be null")
    void keySerializer_cannotBeNull() {
        assertThrows(
                NullPointerException.class,
                () -> builder.keySerializer(null),
                "Key serializer was null!");
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("keySerializer must be specified")
    void keySerializer_mustBeSpecified() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.maxNumOfKeys(10)
                                .virtualLeafRecordSerializer(leafSerializer)
                                .virtualInternalRecordSerializer(internalSerializer)
                                .storageDir(testDirectory.resolve("testBuilder"))
                                .internalHashesRamToDiskThreshold(25)
                                .preferDiskBasedIndexes(true)
                                .build("wombat", "wombat", false),
                "Key serializer should have been specified");
    }

    @Test
    @DisplayName("storageDir cannot be null")
    void storageDir_cannotBeNull() {
        assertThrows(
                NullPointerException.class, () -> builder.storageDir(null), "storageDir was null!");
    }

    @Test
    @DisplayName("Background compaction(merging) is set correctly defaults to true")
    void checkBackgroundCompaction() {
        builder.maxNumOfKeys(10)
                .virtualLeafRecordSerializer(leafSerializer)
                .virtualInternalRecordSerializer(internalSerializer)
                .keySerializer(keySerializer)
                .storageDir(testDirectory.resolve("testBuilder"))
                .internalHashesRamToDiskThreshold(25)
                .preferDiskBasedIndexes(true)
                .build("wombat", "wombat", true);
        assertTrue(builder.mergingEnabledRef.get(), "Should have been true");
        builder.build("wombat", "wombat", false);
        assertFalse(builder.mergingEnabledRef.get(), "Should have been false");
    }

    @Test
    @DisplayName("preferDiskBasedIndexes defaults to false")
    void preferDiskBasedIndexes_defaultsToFalse() {
        builder.maxNumOfKeys(10)
                .virtualLeafRecordSerializer(leafSerializer)
                .virtualInternalRecordSerializer(internalSerializer)
                .keySerializer(keySerializer)
                .storageDir(testDirectory.resolve("testBuilder"))
                .internalHashesRamToDiskThreshold(25)
                .build("wombat", "wombat", false);

        assertFalse(builder.preferDiskBasedIndexesRef.get(), "Should have been false");
    }

    @Test
    @DisplayName("maxNumOfKeys must be positive")
    void maxNumOfKeys_mustBePositive() {
        builder.maxNumOfKeys(1); // OK
        assertThrows(IllegalArgumentException.class, () -> builder.maxNumOfKeys(-1), "Must be > 0");
        assertThrows(IllegalArgumentException.class, () -> builder.maxNumOfKeys(0), "Must be > 0");
    }

    @Test
    @DisplayName("internalHashesRamToDiskThreshold must be non-negative")
    void internalHashesRamToDiskThreshold_mustBeNonNegative() {
        builder.internalHashesRamToDiskThreshold(0); // OK
        builder.internalHashesRamToDiskThreshold(1); // OK
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.internalHashesRamToDiskThreshold(-1),
                ">= 0");
    }

    /**
     * Used for testing purposes, captures the args that would be sent to the data source
     * constructor.
     */
    private static final class TestBuilder
            extends JasperDbBuilder<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> {

        AtomicReference<
                        VirtualLeafRecordSerializer<
                                ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>>
                leafSerializerRef = new AtomicReference<>();

        AtomicReference<VirtualInternalRecordSerializer> internalSerializerRef =
                new AtomicReference<>();
        AtomicReference<KeySerializer<ExampleLongKeyFixedSize>> keySerializerRef =
                new AtomicReference<>();
        AtomicReference<Path> storageDirRef = new AtomicReference<>();
        AtomicReference<String> labelRef = new AtomicReference<>();
        AtomicLong maxNumOfKeysRef = new AtomicLong();
        AtomicBoolean mergingEnabledRef = new AtomicBoolean(true);
        AtomicLong internalHashesRamToDiskThresholdRef = new AtomicLong();
        AtomicBoolean preferDiskBasedIndexesRef = new AtomicBoolean(false);

        @Override
        VirtualDataSourceJasperDB<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>
                createDataSource(
                        VirtualLeafRecordSerializer<
                                        ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>
                                leafSerializer,
                        VirtualInternalRecordSerializer internalSerializer,
                        KeySerializer<ExampleLongKeyFixedSize> keySerializer,
                        Path storageDir,
                        String label,
                        long maxNumOfKeys,
                        boolean mergingEnabled,
                        long internalHashesRamToDiskThreshold,
                        boolean preferDiskBasedIndexes) {
            leafSerializerRef.set(leafSerializer);
            internalSerializerRef.set(internalSerializer);
            keySerializerRef.set(keySerializer);
            storageDirRef.set(storageDir);
            labelRef.set(label);
            maxNumOfKeysRef.set(maxNumOfKeys);
            mergingEnabledRef.set(mergingEnabled);
            internalHashesRamToDiskThresholdRef.set(internalHashesRamToDiskThreshold);
            preferDiskBasedIndexesRef.set(preferDiskBasedIndexes);
            return null;
        }

        @Override
        public VirtualDataSourceJasperDB<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>
                build(
                        final String name,
                        final String label,
                        final VirtualDataSource<
                                        ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue>
                                snapshotMe,
                        final boolean withDbCompactionEnabled) {
            throw new UnsupportedOperationException("Not implemented or tested yet");
        }
    }
}
