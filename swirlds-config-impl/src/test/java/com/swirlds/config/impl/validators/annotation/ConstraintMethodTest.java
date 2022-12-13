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
package com.swirlds.config.impl.validators.annotation;

import com.swirlds.config.api.ConfigurationProvider;
import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.impl.DummyConfigSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConstraintMethodTest {

    private final DummyConfigSource dummyConfigSource = new DummyConfigSource();

    @Test
    public void testNoViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("method.valueA", "true");
        dummyConfigSource.setProperty("method.valueB", "true");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(ConstraintMethodConfigData.class);

        // then
        Assertions.assertDoesNotThrow(
                () -> ConfigurationProvider.init(), "No violation should happen");
    }

    @Test
    public void testViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("method.valueA", "false");
        dummyConfigSource.setProperty("method.valueB", "true");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(ConstraintMethodConfigData.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Violation for @ConstraintMethod should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "method.checkA", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("false", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals("error", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testError() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("method.valueA", "true");
        dummyConfigSource.setProperty("method.valueB", "false");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(ConstraintMethodConfigData.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.init(),
                "Error in validation should end in illegal state");
    }

    @Test
    public void testErrorInvalidMethod() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("method.value", "true");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(BrokenConstraintMethodConfigData.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.init(),
                "Error in validation should end in illegal state");
    }
}
