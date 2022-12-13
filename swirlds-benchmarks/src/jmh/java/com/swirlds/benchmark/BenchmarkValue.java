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
package com.swirlds.benchmark;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongUnaryOperator;

public class BenchmarkValue implements VirtualValue {

    static final long CLASS_ID = 0x2af5b26682153acfL;
    static final int VERSION = 1;

    private static int valueSize = 16;
    private byte[] valueBytes;

    public static void setValueSize(int size) {
        valueSize = size;
    }

    public BenchmarkValue() {
        // default constructor for deserialize
    }

    public BenchmarkValue(long seed) {
        valueBytes = Utils.toBytes(seed, valueSize);
    }

    public BenchmarkValue(BenchmarkValue other) {
        valueBytes = Arrays.copyOf(other.valueBytes, other.valueBytes.length);
    }

    public long toLong() {
        return Utils.fromBytes(valueBytes);
    }

    public void update(LongUnaryOperator updater) {
        long value = Utils.fromBytes(valueBytes);
        value = updater.applyAsLong(value);
        valueBytes = Utils.toBytes(value, valueSize);
    }

    @Override
    public VirtualValue copy() {
        return new BenchmarkValue(this);
    }

    @Override
    public VirtualValue asReadOnly() {
        return new BenchmarkValue(this);
    }

    @Override
    public void serialize(ByteBuffer buffer) throws IOException {
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
    }

    @Override
    public void deserialize(ByteBuffer buffer, int dataVersion) throws IOException {
        assert dataVersion == getVersion()
                : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        int n = buffer.getInt();
        valueBytes = new byte[n];
        buffer.get(valueBytes);
    }

    public static int getSerializedSize() {
        return Integer.BYTES + valueSize;
    }

    @Override
    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(valueBytes.length);
        outputStream.write(valueBytes);
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int dataVersion)
            throws IOException {
        assert dataVersion == getVersion()
                : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        int n = inputStream.readInt();
        valueBytes = new byte[n];
        while (n > 0) {
            n -= inputStream.read(valueBytes, valueBytes.length - n, n);
        }
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BenchmarkValue that)) return false;
        return Arrays.equals(this.valueBytes, that.valueBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(valueBytes);
    }
}
