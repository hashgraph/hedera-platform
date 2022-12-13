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

public class MaxTest {

    private final DummyConfigSource dummyConfigSource = new DummyConfigSource();

    @Test
    public void testNoViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("max.intValue", "1");
        dummyConfigSource.setProperty("max.longValue", "1");
        dummyConfigSource.setProperty("max.doubleValue", "1");
        dummyConfigSource.setProperty("max.floatValue", "1");
        dummyConfigSource.setProperty("max.shortValue", "1");
        dummyConfigSource.setProperty("max.byteValue", "1");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(MaxTestConfigData.class);

        // then
        Assertions.assertDoesNotThrow(
                () -> ConfigurationProvider.init(), "No violation should happen");
    }

    @Test
    public void testEdgeCase() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("max.intValue", "2");
        dummyConfigSource.setProperty("max.longValue", "2");
        dummyConfigSource.setProperty("max.doubleValue", "2");
        dummyConfigSource.setProperty("max.floatValue", "2");
        dummyConfigSource.setProperty("max.shortValue", "2");
        dummyConfigSource.setProperty("max.byteValue", "2");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(MaxTestConfigData.class);

        // then
        Assertions.assertDoesNotThrow(
                () -> ConfigurationProvider.init(), "No violation should happen");
    }

    @Test
    public void testIntViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("max.intValue", "3");
        dummyConfigSource.setProperty("max.longValue", "1");
        dummyConfigSource.setProperty("max.doubleValue", "1");
        dummyConfigSource.setProperty("max.floatValue", "1");
        dummyConfigSource.setProperty("max.shortValue", "1");
        dummyConfigSource.setProperty("max.byteValue", "1");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(MaxTestConfigData.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Violation for @Min should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals("max.intValue", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("3", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be <= 2", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testLongViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("max.intValue", "1");
        dummyConfigSource.setProperty("max.longValue", "3");
        dummyConfigSource.setProperty("max.doubleValue", "1");
        dummyConfigSource.setProperty("max.floatValue", "1");
        dummyConfigSource.setProperty("max.shortValue", "1");
        dummyConfigSource.setProperty("max.byteValue", "1");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(MaxTestConfigData.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Violation for @Min should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "max.longValue", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("3", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be <= 2", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testShortViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("max.intValue", "1");
        dummyConfigSource.setProperty("max.longValue", "1");
        dummyConfigSource.setProperty("max.doubleValue", "1");
        dummyConfigSource.setProperty("max.floatValue", "1");
        dummyConfigSource.setProperty("max.shortValue", "3");
        dummyConfigSource.setProperty("max.byteValue", "1");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(MaxTestConfigData.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Violation for @Min should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "max.shortValue", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("3", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be <= 2", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testByteViolation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("max.intValue", "1");
        dummyConfigSource.setProperty("max.longValue", "1");
        dummyConfigSource.setProperty("max.doubleValue", "1");
        dummyConfigSource.setProperty("max.floatValue", "1");
        dummyConfigSource.setProperty("max.shortValue", "1");
        dummyConfigSource.setProperty("max.byteValue", "3");
        ConfigurationProvider.addSources(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(MaxTestConfigData.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Violation for @Min should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "max.byteValue", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("3", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be <= 2", exception.getViolations().get(0).getMessage());
    }
}
