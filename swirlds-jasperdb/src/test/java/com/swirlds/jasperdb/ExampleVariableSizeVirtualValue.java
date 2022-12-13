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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class ExampleVariableSizeVirtualValue extends ExampleByteArrayVirtualValue {
    private static final int RANDOM_BYTES = 1024;
    private static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];
    private static final Random RANDOM = new Random(12234);
    public static final int SERIALIZATION_VERSION = 865;

    static {
        RANDOM.nextBytes(RANDOM_DATA);
    }

    private int id;
    private byte[] data;

    public ExampleVariableSizeVirtualValue() {}

    public ExampleVariableSizeVirtualValue(final int id) {
        this.id = id;
        data = new byte[256 + (id % 768)];
        System.arraycopy(RANDOM_DATA, 0, data, 0, data.length);
    }

    public int getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public void deserialize(final SerializableDataInputStream inputStream, final int dataVersion)
            throws IOException {
        assert dataVersion == getVersion()
                : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        id = inputStream.readInt();
        final int dataLength = inputStream.readInt();
        data = new byte[dataLength];
        inputStream.read(data);
    }

    @Override
    public void serialize(final SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(id);
        outputStream.writeInt(data.length);
        outputStream.write(data);
    }

    @Override
    public void serialize(final ByteBuffer buffer) {
        buffer.putInt(id);
        buffer.putInt(data.length);
        buffer.put(data);
    }

    @Override
    public void deserialize(final ByteBuffer buffer, final int dataVersion) {
        assert dataVersion == getVersion()
                : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        id = buffer.getInt();
        final int dataLength = buffer.getInt();
        assert dataLength > 0 : "DataLength[" + dataLength + "] should never be 0 or less";
        data = new byte[dataLength];
        buffer.get(data);
    }

    @Override
    public VirtualValue copy() {
        return this;
    }

    @Override
    public VirtualValue asReadOnly() {
        return this;
    }

    @Override
    public long getClassId() {
        return 846821551351L;
    }

    @Override
    public int getVersion() {
        return SERIALIZATION_VERSION;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExampleVariableSizeVirtualValue that = (ExampleVariableSizeVirtualValue) o;
        return id == that.id && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "ExampleVariableSizeVirtualValue{"
                + "id="
                + id
                + ", data="
                + Arrays.toString(data)
                + '}';
    }

    /** A self serializable supplier for {@link ExampleVariableSizeVirtualValue}. */
    public static class Builder
            implements SelfSerializableSupplier<ExampleVariableSizeVirtualValue> {

        private static final long CLASS_ID = 0x908902818427198eL;

        private static final class ClassVersion {
            public static final int ORIGINAL = 1;
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
        public void serialize(final SerializableDataOutputStream out) throws IOException {}

        /** {@inheritDoc} */
        @Override
        public void deserialize(final SerializableDataInputStream in, final int version)
                throws IOException {}

        /** {@inheritDoc} */
        @Override
        public ExampleVariableSizeVirtualValue get() {
            return new ExampleVariableSizeVirtualValue();
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(final Object obj) {
            // Since there is no class state, objects of the same type are considered to be equal
            return obj instanceof Builder;
        }
    }
}
