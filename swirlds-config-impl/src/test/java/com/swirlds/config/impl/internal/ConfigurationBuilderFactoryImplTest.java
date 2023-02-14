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
package com.swirlds.config.impl.internal;

import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigurationBuilderFactoryImplTest {

    @Test
    public void testNotSameResults() {
        // given
        final ConfigurationBuilderFactory factory = new ConfigurationBuilderFactoryImpl();

        // when
        final ConfigurationBuilder configurationBuilder1 = factory.create();
        final ConfigurationBuilder configurationBuilder2 = factory.create();

        // then
        Assertions.assertNotNull(configurationBuilder1);
        Assertions.assertNotNull(configurationBuilder2);
        Assertions.assertNotSame(configurationBuilder1, configurationBuilder2);
    }
}
