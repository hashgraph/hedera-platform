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
package com.swirlds.config.api;

import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.spi.AbstractConfigurationResolver;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolationException;
import java.util.Arrays;

/**
 * Global entry point for the configuration API. This class should be used to configure the
 * configuration (by adding {@link ConfigSource}, {@link ConfigValidator} and {@link
 * ConfigConverter} instances). This class should be used to get the {@link Configuration} instance.
 *
 * <p>The configuration has a simple lifecycle:
 *
 * <p>Whenever the setup of the configuration has changed the {@link #init()} method must be called.
 * To reset the configuration the {@link #dispose()} method must be called.
 *
 * <p>Example:
 *
 * <pre>
 * // only needed if config has already been used at runtime
 * ConfigurationProvider.dispose();
 *
 * // example how custom ConfigSource, ConfigValidator, and Converter can be added
 * ConfigurationProvider.addSource(new ConfigSourceImpl());
 * ConfigurationProvider.addConverter(new ConfigConverterImpl());
 * ConfigurationProvider.addValidator(new ConfigValidatorImpl());
 *
 * // config will be initialized
 * ConfigurationProvider.init();
 *
 * //now we can use the config
 * Configuration config = ConfigurationProvider.getConfig();
 * String value = config.getValue("app.name");
 *
 * </pre>
 */
public final class ConfigurationProvider {

    private ConfigurationProvider() {}

    /**
     * Returns the configuration instance;
     *
     * @return the configuration instance
     */
    public static Configuration getConfig() {
        return AbstractConfigurationResolver.getInstance().getConfig();
    }

    /**
     * Initialize the configuration
     *
     * @throws ConfigViolationException if any violations are happened in the initialization of the
     *     configuration (see {@link com.swirlds.config.api.validation})
     */
    public static void init() throws ConfigViolationException {
        AbstractConfigurationResolver.getInstance().init();
    }

    /**
     * Returns true if the configuration is initialized
     *
     * @return true if the configuration is initialized
     */
    public static boolean isInitialized() {
        return AbstractConfigurationResolver.getInstance().isInitialized();
    }

    /** Dispose the configuration */
    public static void dispose() {
        AbstractConfigurationResolver.getInstance().dispose();
    }

    /**
     * Adds a config source to the configuration. If this method is called after the config has been
     * initialized a {@link IllegalStateException} will be thrown.
     *
     * @param configSource the config source that should be added
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static void addSource(final ConfigSource configSource) throws IllegalStateException {
        AbstractConfigurationResolver.getInstance().addConfigSource(configSource);
    }

    /**
     * Adds a converter to the configuration. If this method is called after the config has been
     * initialized a {@link IllegalStateException} will be thrown.
     *
     * @param converter the converter
     * @param <T> type of the converter
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static <T> void addConverter(final ConfigConverter<T> converter)
            throws IllegalStateException {
        AbstractConfigurationResolver.getInstance().addConverter(converter);
    }

    /**
     * Adds a validator to the config. If this method is called after the config has been
     * initialized a {@link IllegalStateException} will be thrown.
     *
     * @param validator the validator.
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static void addValidator(final ConfigValidator validator) throws IllegalStateException {
        AbstractConfigurationResolver.getInstance().addValidator(validator);
    }

    /**
     * Adds a contraint for a specific property to the config. If this method is called after the
     * config has been initialized a {@link IllegalStateException} will be thrown.
     *
     * @param propertyName name of the property
     * @param valueType type of the property value
     * @param constraint the constraint
     * @param <T> type of the property value
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static <T> void addConstraint(
            final String propertyName,
            final Class<T> valueType,
            final ConfigPropertyConstraint<T> constraint)
            throws IllegalStateException {
        AbstractConfigurationResolver.getInstance()
                .addConstraint(propertyName, valueType, constraint);
    }

    /**
     * Method that can be used to add multiple validators. See {@link
     * ConfigurationProvider#addValidator(ConfigValidator)} for more information.
     *
     * @param validators the validtors
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static void addValidators(final ConfigValidator... validators)
            throws IllegalStateException {
        Arrays.stream(validators).forEach(ConfigurationProvider::addValidator);
    }

    /**
     * Method that can be used to add multiple constraints. See {@link
     * ConfigurationProvider#addConstraint(String, Class, ConfigPropertyConstraint)} for more
     * information.
     *
     * @param propertyName name of the property
     * @param valueType type of the property value
     * @param constraints the contraints
     * @param <T> type of the property value
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static <T> void addConstraint(
            final String propertyName,
            final Class<T> valueType,
            final ConfigPropertyConstraint<T>... constraints)
            throws IllegalStateException {
        Arrays.stream(constraints).forEach(v -> addConstraint(propertyName, valueType, v));
    }

    /**
     * Method that can be used to add multiple converters. See {@link
     * ConfigurationProvider#addConverter(ConfigConverter)} for more information.
     *
     * @param converters the converters
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static void addConverters(final ConfigConverter<?>... converters)
            throws IllegalStateException {
        Arrays.stream(converters).forEach(ConfigurationProvider::addConverter);
    }

    /**
     * Method that can be used to add multiple config sources. See {@link
     * ConfigurationProvider#addSource(ConfigSource)} for more information.
     *
     * @param configSources the sources
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static void addSources(final ConfigSource... configSources)
            throws IllegalStateException {
        Arrays.stream(configSources).forEach(ConfigurationProvider::addSource);
    }

    /**
     * Adds a config data type. {@link ConfigData} provides more information about config data
     * objects.
     *
     * @param type the record of the data config to add
     * @param <T> the generic record type of the data config to add
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public static <T extends Record> void addConfigDataType(Class<T> type)
            throws IllegalStateException {
        AbstractConfigurationResolver.getInstance().addConfigDataType(type);
    }
}
