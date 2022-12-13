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
package com.swirlds.platform.test.chatter.protocol.input;

import com.swirlds.platform.chatter.protocol.input.InputDelegateBuilder;
import com.swirlds.platform.chatter.protocol.input.MessageTypeHandler;
import com.swirlds.platform.stats.PerSecondStat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InputDelegateTest {

    @Test
    @SuppressWarnings("unchecked")
    void builderTest() {
        final InputDelegateBuilder builder = InputDelegateBuilder.builder();
        Assertions.assertThrows(Exception.class, builder::build, "should throw if nothing is set");
        builder.addHandler(Mockito.mock(MessageTypeHandler.class));
        Assertions.assertThrows(Exception.class, builder::build, "should throw if stat is not set");
        builder.setStat(Mockito.mock(PerSecondStat.class));

        Assertions.assertDoesNotThrow(builder::build, "build now");
    }
}
