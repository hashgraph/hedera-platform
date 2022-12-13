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
package com.swirlds.constructable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.utility.MerkleLong;
import org.junit.jupiter.api.Test;

public class ConstructableRegistryTest {
    @Test
    public void testRegisterClassesFromAnotherJPMSModule() throws ConstructableRegistryException {
        long classId = new MerkleLong().getClassId();
        ConstructableRegistry.registerConstructables(MerkleLong.class.getPackageName());
        assertNotNull(ConstructableRegistry.createObject(classId));
        assertEquals(classId, ConstructableRegistry.createObject(classId).getClassId());
    }
}
