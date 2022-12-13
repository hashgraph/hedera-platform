/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.swirlds.config.impl;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationProvider;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.api.validation.PropertyMetadata;
import com.swirlds.config.impl.converters.DurationConverter;
import com.swirlds.config.impl.converters.IntegerConverter;
import com.swirlds.config.impl.sources.DefaultValueConfigSource;
import com.swirlds.config.impl.sources.LegacyFileConfigSource;
import com.swirlds.config.impl.sources.PropertyFileConfigSource;
import com.swirlds.config.impl.sources.SystemPropertiesConfigSource;
import com.swirlds.config.impl.validators.DefaultConfigViolation;
import com.swirlds.config.impl.validators.MinConstraint;
import com.swirlds.config.impl.validators.PropertyExistsConstraint;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigApiTests {

    private final DummyConfigSource dummyConfigSource = new DummyConfigSource();

    @BeforeEach
    public void initConfig() {
        if (ConfigurationProvider.isInitialized()) {
            ConfigurationProvider.dispose();
        }
        dummyConfigSource.clear();
        ConfigurationProvider.init();
    }

    private void addPropertyToConfig(final String key, final String value) {
        if (ConfigurationProvider.isInitialized()) {
            ConfigurationProvider.dispose();
        }
        dummyConfigSource.setProperty(key, value);
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.init();
    }

    @Test
    public void checkNotExistingProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertFalse(
                configuration.exists("someName"), "A not defined config property should not exist");
    }

    @Test
    public void checkExistingProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        addPropertyToConfig("someName", "123");

        // then
        Assertions.assertTrue(
                configuration.exists("someName"), "A defined config property should exist");
    }

    @Test
    public void getAllPropertyNamesEmpty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final List<String> names = configuration.getPropertyNames().collect(Collectors.toList());

        // then
        Assertions.assertNotNull(
                names, "Even for an empty config the returned collection should not be null");
        Assertions.assertEquals(0, names.size(), "The config should not contain any properties");
    }

    @Test
    public void getAllPropertyNames() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("someName", "123");
        addPropertyToConfig("someOtherName", "test");
        addPropertyToConfig("company.url", "dummy");

        // when
        final List<String> names = configuration.getPropertyNames().collect(Collectors.toList());

        // then
        Assertions.assertNotNull(names, "The collection of config properties should never be null");
        Assertions.assertEquals(3, names.size(), "The config should contain 3 properties");
        Assertions.assertTrue(
                names.contains("someName"), "The config should contain the defined property");
        Assertions.assertTrue(
                names.contains("someOtherName"), "The config should contain the defined property");
        Assertions.assertTrue(
                names.contains("company.url"), "The config should contain the defined property");
    }

    @Test
    public void readNotExistingStringProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertThrows(
                NoSuchElementException.class,
                () -> configuration.getValue("someName"),
                "The config should throw an exception when trying to access a property that is not"
                        + " defined");
    }

    @Test
    public void readDefaultStringProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final String value = configuration.getValue("someName", "default");

        // then
        Assertions.assertEquals(
                "default",
                value,
                "The default value should be returned for a property that is not defined");
    }

    @Test
    public void readStringProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("someName", "123");

        // when
        final String value = configuration.getValue("someName");

        // then
        Assertions.assertEquals(
                "123", value, "The defined value of the property should be returned");
    }

    @Test
    public void readStringPropertyWithDefaultProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("someName", "123");

        // when
        final String value = configuration.getValue("someName", "default");

        // then
        Assertions.assertEquals(
                "123", value, "If a property is defined the default value should be ignored");
    }

    @Test
    public void readMissingStringPropertyWithNullProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final String value = configuration.getValue("someName", String.class, null);

        // then
        Assertions.assertNull(value, "Null should be an allowed default value");
    }

    @Test
    public void readIntProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("timeout", "12");

        // when
        final int value = configuration.getValue("timeout", Integer.class);

        // then
        Assertions.assertEquals(12, value, "Integer value should be supported");
    }

    @Test
    public void readNotExistingIntProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertThrows(
                NoSuchElementException.class,
                () -> configuration.getValue("timeout", Integer.class),
                "The config should throw an exception when trying to access a property that is not"
                        + " defined");
    }

    @Test
    public void readBadIntProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("timeout", "NO-INT");

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValue("timeout", Integer.class),
                "The config should throw an exception when trying to access a property that can not"
                        + " be converted to it's defined type");
    }

    @Test
    public void readIntPropertyWithDefaultValue() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("timeout", "12");

        // when
        final int value = configuration.getValue("timeout", Integer.class, 1_000);

        // then
        Assertions.assertEquals(
                12, value, "Default value should be ignored for a defined property");
    }

    @Test
    public void readMissingIntPropertyWithDefaultValue() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final int value = configuration.getValue("timeout", Integer.class, 1_000);

        // then
        Assertions.assertEquals(
                1_000,
                value,
                "The default value should be used for a property that is not defined");
    }

    @Test
    public void readMissingIntPropertyWithNullDefaultValue() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        Integer value = configuration.getValue("timeout", Integer.class, null);

        // then
        Assertions.assertNull(value, "Null should be allowed as default value");
    }

    @Test
    public void readMissingIntPropertyWithNullDefaultValueAutoboxing() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertThrows(
                NullPointerException.class,
                () -> {
                    int timeout = configuration.getValue("timeout", Integer.class, null);
                },
                "Autoboxing of null as default value should throw an exception for int");
    }

    @Test
    public void readListProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("testNumbers", "1,2,3");

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class);

        // then
        Assertions.assertEquals(
                3, values.size(), "A property that is defined as list should be parsed correctly");
        Assertions.assertEquals(
                1,
                values.get(0),
                "A property that is defined as list should contain the defined values");
        Assertions.assertEquals(
                2,
                values.get(1),
                "A property that is defined as list should contain the defined values");
        Assertions.assertEquals(
                3,
                values.get(2),
                "A property that is defined as list should contain the defined values");
    }

    @Test
    public void readListPropertyWithOneEntry() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("testNumbers", "123");

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class);

        // then
        Assertions.assertEquals(
                1, values.size(), "A property that is defined as list should be parsed correctly");
        Assertions.assertEquals(
                123,
                values.get(0),
                "A property that is defined as list should contain the defined values");
    }

    @Test
    public void readBadListProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();
        addPropertyToConfig("testNumbers", "1,2,   3,4");

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValues("testNumbers", Integer.class),
                "given list property should not be parsed correctly");
    }

    @Test
    public void readDefaultListProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final List<Integer> values =
                configuration.getValues("testNumbers", Integer.class, List.of(6, 7, 8));

        // then
        Assertions.assertEquals(
                3,
                values.size(),
                "The default value should be used since no value is defined by the config");
        Assertions.assertEquals(
                6, values.get(0), "Should be part of the list since it is part of the default");
        Assertions.assertEquals(
                7, values.get(1), "Should be part of the list since it is part of the default");
        Assertions.assertEquals(
                8, values.get(2), "Should be part of the list since it is part of the default");
    }

    @Test
    public void readNullDefaultListProperty() {
        // given
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class, null);

        // then
        Assertions.assertNull(values, "Null should be a valid default value");
    }

    @Test
    public void checkListPropertyImmutable() {
        // given
        addPropertyToConfig("testNumbers", "1,2,3");
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class);

        // then
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> values.add(10),
                "List properties should always be immutable");
    }

    @Test
    public void getConfigProxy() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("network.port", "8080");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        Assertions.assertEquals(
                8080, networkConfig.port(), "Config data objects should be configured correctly");
    }

    @Test
    public void getNotRegisteredDataObject() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.init(),
                "It should not be possible to create a config data object with undefined values");
    }

    @Test
    public void getConfigProxyUndefinedValue() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getConfigData(NetworkConfig.class),
                "It should not be possible to create an object of a not registered config data"
                        + " type");
    }

    @Test
    public void getConfigProxyDefaultValue() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("network.port", "8080");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        Assertions.assertEquals(
                "localhost",
                networkConfig.server(),
                "Default values of config data objects should be used");
    }

    @Test
    public void getConfigProxyDefaultValuesList() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.clear();
        dummyConfigSource.setProperty("network.port", "8080");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);
        final List<Integer> errorCodes = networkConfig.errorCodes();

        // then
        Assertions.assertNotNull(
                errorCodes, "Default values of config data objects should be used");
        Assertions.assertEquals(
                2,
                errorCodes.size(),
                "List values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(404),
                "List values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(500),
                "List values should be supported for default values in config data objects");
    }

    @Test
    public void getConfigProxyValuesList() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("network.port", "8080");
        dummyConfigSource.setProperty("network.errorCodes", "1,2,3");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);
        final List<Integer> errorCodes = networkConfig.errorCodes();

        // then
        Assertions.assertNotNull(
                errorCodes, "List values should be supported in config data objects");
        Assertions.assertEquals(
                3, errorCodes.size(), "List values should be supported in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(1), "List values should be supported in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(2), "List values should be supported in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(3), "List values should be supported in config data objects");
    }

    @Test
    public void invalidDataRecordWillFailInit() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                ConfigurationProvider::init,
                "values must be defined for all properties that are defined by registered config"
                        + " data types");
    }

    @Test
    public void getConfigProxyOverwrittenDefaultValue() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.clear();
        dummyConfigSource.setProperty("network.port", "8080");
        dummyConfigSource.setProperty("network.server", "example.net");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        Assertions.assertEquals(
                "example.net",
                networkConfig.server(),
                "It must be possible to overwrite default values in object data types");
    }

    @Test
    public void getSystemProperty() {
        // given
        ConfigurationProvider.dispose();
        System.setProperty("test.config.sample", "qwerty");
        ConfigurationProvider.addSource(SystemPropertiesConfigSource.getInstance());
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        String value = configuration.getValue("test.config.sample");

        // then
        Assertions.assertEquals(
                "qwerty", value, "It must be possible to use system variables for the config");
    }

    @Test
    public void getPropertyFromFile() throws IOException, URISyntaxException {
        // given
        ConfigurationProvider.dispose();
        final Path configFile =
                Paths.get(ConfigApiTests.class.getResource("test.properties").toURI());
        ConfigurationProvider.addSource(new PropertyFileConfigSource(configFile));
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        String value = configuration.getValue("app.name");

        // then
        Assertions.assertEquals(
                "ConfigTest",
                value,
                "It must be possible to read variables for the config from a property file");
    }

    @Test
    public void getPropertyFromFileOverwritesDefault() throws IOException, URISyntaxException {
        // given
        ConfigurationProvider.dispose();
        final Path configFile =
                Paths.get(ConfigApiTests.class.getResource("test.properties").toURI());
        ConfigurationProvider.addSource(new PropertyFileConfigSource(configFile));
        DefaultValueConfigSource.setDefaultValue("app.name", "unknown");
        ConfigurationProvider.addSource(DefaultValueConfigSource.getInstance());
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        String value = configuration.getValue("app.name");

        // then
        Assertions.assertEquals(
                "ConfigTest",
                value,
                "the used value must be based on the ordinal of the underlying config sources");
    }

    @Test
    public void checkValidationForNotExistingProperty() {
        // given
        ConfigurationProvider.dispose();
        final ConfigValidator validator =
                configuration -> {
                    if (!configuration.exists("app.name")) {
                        return Stream.of(
                                new DefaultConfigViolation(
                                        "app.name",
                                        null,
                                        false,
                                        "Property 'app.name' must not be " + "null"));
                    }
                    return Stream.empty();
                };
        ConfigurationProvider.addValidator(validator);

        // when
        final IllegalStateException exception =
                Assertions.assertThrows(
                        IllegalStateException.class,
                        ConfigurationProvider::init,
                        "Config must not be initialzed if a validation fails");

        // then
        Assertions.assertEquals(
                ConfigViolationException.class,
                exception.getClass(),
                "The specific exception type is used for a failed validation");
        final ConfigViolationException configViolationException =
                (ConfigViolationException) exception;
        Assertions.assertEquals(
                1,
                configViolationException.getViolations().size(),
                "The given init should only end in 1 violation");
        Assertions.assertEquals(
                "app.name",
                configViolationException.getViolations().get(0).getPropertyName(),
                "The violation should contain the correct property name");
        Assertions.assertEquals(
                "Property 'app.name' must not be null",
                configViolationException.getViolations().get(0).getMessage(),
                "The violation must contain the correct error message");
    }

    @Test
    public void loadLegacySettings() throws IOException, URISyntaxException {
        // given
        ConfigurationProvider.dispose();
        final Path configFile =
                Paths.get(ConfigApiTests.class.getResource("legacy-settings.txt").toURI());
        @SuppressWarnings("removal")
        final ConfigSource configSource = new LegacyFileConfigSource(configFile);
        ConfigurationProvider.addSource(configSource);
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertEquals(
                8,
                configuration.getPropertyNames().count(),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("maxOutgoingSyncs"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("state.saveStatePeriod"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("showInternalStats"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("doUpnp"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("useLoopbackIp"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("csvFileName"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("checkSignedStateFromDisk"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("loadKeysFromPfxFiles"),
                "It must be possible to read config properties from the old file format");

        Assertions.assertEquals(
                1,
                configuration.getValue("maxOutgoingSyncs", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                0,
                configuration.getValue("state.saveStatePeriod", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                1,
                configuration.getValue("showInternalStats", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertFalse(
                configuration.getValue("doUpnp", Boolean.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertFalse(
                configuration.getValue("useLoopbackIp", Boolean.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                "PlatformTesting",
                configuration.getValue("csvFileName"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                1,
                configuration.getValue("checkSignedStateFromDisk", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                0,
                configuration.getValue("loadKeysFromPfxFiles", Integer.class),
                "It must be possible to read config properties from the old file format");
    }

    @Test
    public void checkNotInitialized() {
        // given
        ConfigurationProvider.dispose();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                configuration::getPropertyNames,
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configuration.exists("app.name"),
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configuration.getValue("app.name"),
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configuration.getValue("app.name", String.class),
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configuration.getValue("app.name", String.class, "default"),
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configuration.getValues("app.services", String.class),
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () ->
                        configuration.getValues(
                                "app.services", String.class, Collections.emptyList()),
                "It must not be possible to access functionallity of a disposes configuration");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configuration.getConfigData(NetworkConfig.class),
                "It must not be possible to access functionallity of a disposes configuration");
    }

    @Test
    public void checkConstraintForNotExistingProperty() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.addConstraint(
                "app.name", String.class, new PropertyExistsConstraint<>());

        // when
        final IllegalStateException exception =
                Assertions.assertThrows(
                        IllegalStateException.class,
                        ConfigurationProvider::init,
                        "It must not be possible to init the config with a failed contraint");

        // then
        Assertions.assertEquals(ConfigViolationException.class, exception.getClass());
        final ConfigViolationException configViolationException =
                (ConfigViolationException) exception;
        Assertions.assertEquals(
                1,
                configViolationException.getViolations().size(),
                "Based o the contraint we should only have 1 violation");
        Assertions.assertEquals(
                "app.name",
                configViolationException.getViolations().get(0).getPropertyName(),
                "The violation should define the corret property");
    }

    @Test
    public void checkConstraintForMinIntegerProperty() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.addConstraint("app.version", Integer.class, new MinConstraint<>(10));
        DefaultValueConfigSource.setDefaultValue("app.version", "9");
        ConfigurationProvider.addSource(DefaultValueConfigSource.getInstance());

        // when
        final IllegalStateException exception =
                Assertions.assertThrows(
                        IllegalStateException.class,
                        ConfigurationProvider::init,
                        "Configuration initialization must fail based on the registered "
                                + "contraint");

        // then
        Assertions.assertEquals(
                ConfigViolationException.class,
                exception.getClass(),
                "Configuration initialization must fail with a ConfigViolationException");
        final ConfigViolationException configViolationException =
                (ConfigViolationException) exception;
        Assertions.assertEquals(
                1,
                configViolationException.getViolations().size(),
                "The given contraint must end in exactly 1 violation");
        Assertions.assertEquals(
                "app.version",
                configViolationException.getViolations().get(0).getPropertyName(),
                "The violation must contain the correct property");
    }

    @Test
    public void testInitalizedConfigCanNotBeConfigured() {
        // given
        final ConfigSource source = new DummyConfigSource();
        final ConfigConverter<Integer> converter = new IntegerConverter();
        final ConfigValidator validator = c -> Stream.empty();
        final ConfigPropertyConstraint<String> constraint =
                metadata -> DefaultConfigViolation.of(metadata, "");

        // when
        ConfigurationProvider.dispose();
        ConfigurationProvider.init();

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.addSource(source),
                "add config sources after config has been initialized must not be possible");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.addConverter(converter),
                "add converter after config has been initialized must not be possible");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.addValidator(validator),
                "add validator after config has been initialized must not be possible");
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.addConstraint("property", String.class, constraint),
                "add constraint after config has been initialized must not be possible");
    }

    @Test
    public void testInitIsThreadSafe() {
        // given
        ConfigurationProvider.dispose();
        final AtomicInteger initCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final Runnable task =
                () -> {
                    try {
                        ConfigurationProvider.init();
                        initCount.incrementAndGet();
                    } catch (final Exception e) {
                        failCount.incrementAndGet();
                    }
                };
        final ExecutorService executorService = Executors.newFixedThreadPool(10);

        // when
        IntStream.range(0, 100)
                .mapToObj(i -> executorService.submit(task))
                .forEach(
                        f -> {
                            try {
                                f.get();
                            } catch (Exception e) {
                            }
                        });

        // then
        Assertions.assertEquals(1, initCount.get());
        Assertions.assertEquals(99, failCount.get());
    }

    @Test
    public void testCreationIsThreadSafe() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        final Callable<Configuration> task = () -> ConfigurationProvider.getConfig();
        final ExecutorService executorService = Executors.newFixedThreadPool(10);

        // then
        IntStream.range(0, 100)
                .mapToObj(i -> executorService.submit(task))
                .map(
                        f -> {
                            try {
                                return f.get();
                            } catch (Exception e) {
                                throw new RuntimeException("Error", e);
                            }
                        })
                .filter(c -> c != configuration)
                .findAny()
                .ifPresent(c -> Assertions.fail("We should only have 1 config instance"));
    }

    @Test
    public void registerConverterForTypeMultipleTimes() {
        // given
        ConfigurationProvider.dispose();

        // when
        ConfigurationProvider.addConverter(new DurationConverter());

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> ConfigurationProvider.init(),
                "One 1 converter for a specific type / class can be registered");
    }

    @Test
    public void registerCustomConverter() {
        // given
        ConfigurationProvider.dispose();
        ConfigurationProvider.addConverter(new TestDateConverter());
        DefaultValueConfigSource.setDefaultValue("app.start", "1662367513551");
        ConfigurationProvider.addSource(DefaultValueConfigSource.getInstance());
        ConfigurationProvider.init();
        final Configuration configuration = ConfigurationProvider.getConfig();

        // when
        final Date date = configuration.getValue("app.start", Date.class);

        // then
        Assertions.assertEquals(
                new Date(1662367513551L),
                date,
                "The date should be converted correctly based on the given value");
    }

    @Test
    public void testMinConstrainAnnotation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("network.port", "-1");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Check for @Min annotation in NetworkConfig should end in violation");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals("network.port", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("-1", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be >= 1", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testConstrainAnnotation() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("network.port", "8080");
        dummyConfigSource.setProperty("network.server", "invalid");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Check for @Constraint annotation in NetworkConfig should end in"
                                + " violation");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "network.server", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("invalid", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "server must not be invalid", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testMultipleConstrainAnnotationsFail() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("network.port", "-1");
        dummyConfigSource.setProperty("network.server", "invalid");
        ConfigurationProvider.addSource(dummyConfigSource);
        ConfigurationProvider.addConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception =
                Assertions.assertThrows(
                        ConfigViolationException.class,
                        () -> ConfigurationProvider.init(),
                        "Check for @Constraint annotation in NetworkConfig should end in"
                                + " violation");

        // then
        Assertions.assertEquals(2, exception.getViolations().size());
    }

    @Test
    public void testConfigPropertyConstraintMetadata() {
        // given
        ConfigurationProvider.dispose();
        dummyConfigSource.setProperty("test", "-1");
        ConfigurationProvider.addSources(dummyConfigSource);
        final AtomicBoolean check = new AtomicBoolean(false);
        ConfigurationProvider.addConstraint(
                "test",
                Integer.TYPE,
                new ConfigPropertyConstraint<Integer>() {
                    @Override
                    public ConfigViolation check(final PropertyMetadata<Integer> metadata) {
                        Assertions.assertNotNull(metadata);
                        Assertions.assertEquals("test", metadata.getName());
                        Assertions.assertEquals("-1", metadata.getRawValue());
                        Assertions.assertNotNull(metadata.getConverter());
                        Assertions.assertEquals(
                                -1, metadata.getConverter().convert(metadata.getRawValue()));
                        Assertions.assertEquals(Integer.TYPE, metadata.getValueType());
                        Assertions.assertTrue(metadata.exists());
                        check.set(true);
                        return null;
                    }
                });

        // when
        ConfigurationProvider.init();

        // then
        Assertions.assertTrue(check.get());
    }
}
