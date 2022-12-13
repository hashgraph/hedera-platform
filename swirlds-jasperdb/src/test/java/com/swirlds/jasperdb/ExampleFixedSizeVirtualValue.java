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
public final class ExampleFixedSizeVirtualValue extends ExampleByteArrayVirtualValue {
    public static final int RANDOM_BYTES = 32;
    public static final int SIZE_BYTES = Integer.BYTES + RANDOM_BYTES;
    static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];
    public static final int SERIALIZATION_VERSION = 287;

    static {
        new Random(12234).nextBytes(RANDOM_DATA);
    }

    private int id;
    private byte[] data;

    public ExampleFixedSizeVirtualValue() {}

    public ExampleFixedSizeVirtualValue(final int id) {
        this.id = id;
        this.data = RANDOM_DATA;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public void deserialize(final SerializableDataInputStream inputStream, final int dataVersion)
            throws IOException {
        assert dataVersion == getVersion()
                : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        id = inputStream.readInt();
        data = new byte[RANDOM_BYTES];
        inputStream.read(data);
    }

    @Override
    public void serialize(final SerializableDataOutputStream outputStream) throws IOException {
        outputStream.writeInt(id);
        outputStream.write(data);
    }

    @Override
    public void serialize(final ByteBuffer buffer) {
        buffer.putInt(id);
        buffer.put(data);
    }

    @Override
    public void deserialize(final ByteBuffer buffer, final int dataVersion) {
        assert dataVersion == getVersion()
                : "dataVersion=" + dataVersion + " != getVersion()=" + getVersion();
        id = buffer.getInt();
        data = new byte[RANDOM_BYTES];
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
        return 1438455686395468L;
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
        final ExampleFixedSizeVirtualValue that = (ExampleFixedSizeVirtualValue) o;
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
        return "TestLeafData{" + "id=" + id + ", data=" + Arrays.toString(data) + '}';
    }

    /** A self serializable supplier for {@link ExampleFixedSizeVirtualValue}. */
    public static class Builder implements SelfSerializableSupplier<ExampleFixedSizeVirtualValue> {

        private static final long CLASS_ID = 0x492808bd5404571eL;

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
        public ExampleFixedSizeVirtualValue get() {
            return new ExampleFixedSizeVirtualValue();
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
