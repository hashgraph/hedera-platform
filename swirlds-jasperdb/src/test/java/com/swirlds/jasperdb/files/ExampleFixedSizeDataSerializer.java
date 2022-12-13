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
package com.swirlds.jasperdb.files;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Very simple DataItem that is fixed size and has a long key and long value. Designed for testing
 */
public class ExampleFixedSizeDataSerializer implements DataItemSerializer<long[]> {
    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return Long.BYTES;
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader(Long.BYTES * 2, buffer.getLong());
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return Long.BYTES * 2;
    }

    /** Get the current data item serialization version */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer The buffer to read from containing the data item including its header
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public long[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        return new long[] {buffer.getLong(), buffer.getLong()};
    }

    /**
     * Serialize a data item including header to the output stream returning the size of the data
     * written
     *
     * @param data The data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(long[] data, SerializableDataOutputStream outputStream)
            throws IOException {
        Objects.requireNonNull(data);
        Objects.requireNonNull(outputStream);
        outputStream.writeLong(data[0]);
        outputStream.writeLong(data[1]);
        return getSerializedSize();
    }
}
