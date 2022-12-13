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
package com.swirlds.common.test.constructable;

import com.swirlds.common.constructable.RuntimeConstructable;

public class ConstructableExample implements RuntimeConstructable {
    // Note: CLASS_ID should be final, this is not final because of unit tests
    public static long CLASS_ID = 0x722e98dc5b8d52d7L;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }
}
