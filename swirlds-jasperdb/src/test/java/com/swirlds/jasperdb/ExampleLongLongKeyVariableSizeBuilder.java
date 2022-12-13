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
import java.io.IOException;

/** A self serializable supplier for {@link ExampleLongLongKeyVariableSize}. */
public class ExampleLongLongKeyVariableSizeBuilder
        implements SelfSerializableSupplier<ExampleLongLongKeyVariableSize> {

    private static final long CLASS_ID = 0xb047f0f9ee446037L;

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
    public ExampleLongLongKeyVariableSize get() {
        return new ExampleLongLongKeyVariableSize();
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
        return obj instanceof ExampleLongLongKeyVariableSizeBuilder;
    }
}
