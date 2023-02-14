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
package com.swirlds.common.test.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.EmptyHashValueException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.common.test.io.SerializationUtils;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

public class HashTests {

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common.crypto");
    }

    @Test
    public void exceptionTests() {
        final byte[] nonZeroHashValue = new byte[DigestType.SHA_384.digestLength()];
        Arrays.fill(nonZeroHashValue, Byte.MAX_VALUE);

        final Hash hash = new Hash(DigestType.SHA_384);
        final ImmutableHash immutableHash = new ImmutableHash(hash);

        assertDoesNotThrow((ThrowingSupplier<Hash>) Hash::new);
        assertDoesNotThrow(() -> new Hash(nonZeroHashValue));
        assertDoesNotThrow(() -> new Hash(DigestType.SHA_384));
        assertDoesNotThrow(() -> new Hash(DigestType.SHA_512));

        assertThrows(NullPointerException.class, () -> new Hash((DigestType) null));
        assertThrows(IllegalArgumentException.class, () -> new Hash((byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> new Hash((Hash) null));
        assertThrows(EmptyHashValueException.class, () -> new Hash(new byte[48]));

        assertThrows(IllegalArgumentException.class, () -> new Hash(nonZeroHashValue, null));
        assertThrows(
                IllegalArgumentException.class, () -> new Hash(new byte[0], DigestType.SHA_384));
        assertThrows(
                IllegalArgumentException.class, () -> new Hash(new byte[47], DigestType.SHA_384));
        assertThrows(
                IllegalArgumentException.class, () -> new Hash(new byte[71], DigestType.SHA_512));
        assertThrows(
                EmptyHashValueException.class,
                () -> new Hash(new byte[DigestType.SHA_384.digestLength()], DigestType.SHA_384));
    }

    @Test
    public void serializeDeserialize() throws IOException {
        final InputOutputStream ioStream = new InputOutputStream();
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x0f87da12).mutable();

        ioStream.getOutput().writeSerializable(original, true);
        ioStream.startReading();

        final Hash copy = ioStream.getInput().readSerializable(true, Hash::new);
        assertEquals(original, copy);
    }

    @Test
    public void accessorCorrectness() {
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x1d88a790).immutable();
        final Hash copy = original.copy();
        final Hash recalculated = builder.update(0x1d88a790).mutable();
        final Hash different = builder.update(0x1d112233).mutable();

        assertNotNull(original.toString());
        assertEquals(96, original.toString().length());
        assertEquals(original.toString(), copy.toString());
        assertEquals(original.toString(), recalculated.toString());
        assertEquals(copy.toString(), recalculated.toString());
        assertNotEquals(original.toString(), different.toString());
        assertNotEquals(copy.toString(), different.toString());

        assertFalse(original.equals(null));
        assertNotEquals(original, new Object());
        assertTrue(original.equals(original));
        assertEquals(0, original.compareTo(original));
        assertNotEquals(0, original.compareTo(new Hash(DigestType.SHA_512)));

        ////////
        assertArrayEquals(original.getValue(), copy.getValue());
        assertArrayEquals(original.getValue(), recalculated.getValue());
        assertArrayEquals(copy.getValue(), recalculated.getValue());
        assertFalse(Arrays.equals(original.getValue(), different.getValue()));
        assertFalse(Arrays.equals(copy.getValue(), different.getValue()));

        assertEquals(original, copy);
        assertEquals(original, recalculated);
        assertEquals(copy, recalculated);
        assertNotEquals(original, different);
        assertNotEquals(copy, different);

        assertEquals(0, original.compareTo(copy));
        assertEquals(0, original.compareTo(recalculated));
        assertEquals(0, copy.compareTo(recalculated));
        assertThrows(NullPointerException.class, () -> original.compareTo(null));
        assertNotEquals(0, original.compareTo(different));
        assertNotEquals(0, copy.compareTo(different));

        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(original.hashCode(), recalculated.hashCode());
        assertEquals(copy.hashCode(), recalculated.hashCode());
        assertNotEquals(original.hashCode(), different.hashCode());
        assertNotEquals(copy.hashCode(), different.hashCode());
    }

    @Test
    public void serializeAndDeserializeTest() throws IOException {
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x1d88a790).build();
        SerializationUtils.checkSerializeDeserializeEqual(original);
    }

    @Test
    public void serializeAndDeserializeImmutableHashTest() throws IOException {
        final HashBuilder builder = new HashBuilder(DigestType.SHA_384);

        final Hash original = builder.update(0x1d88a790).immutable();
        SerializationUtils.checkSerializeDeserializeEqual(original);
    }
}
