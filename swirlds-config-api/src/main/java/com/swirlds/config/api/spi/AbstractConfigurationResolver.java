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
package com.swirlds.config.api.spi;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationProvider;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigValidator;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * SPI entrypoint of the config API to access a concrete implementation of the config API. The API
 * uses SPI (see {@link ServiceLoader} for more details) to find a load an implementation of the
 * config API at runtime. This class must be implemented by an implementation of the config API and
 * provided for the {@link ServiceLoader}. Only one implementation is supported at runtime. If more
 * than one implementation is found on the classpath the initialization will fail.
 *
 * <p>The configuration API provides a minimal lifecycle that must be suported by any
 * implementation. The lifecycle is defined by the {@link AbstractConfigurationResolver#init()} and
 * {@link AbstractConfigurationResolver#dispose()} methods.
 */
public abstract class AbstractConfigurationResolver {

    private static AbstractConfigurationResolver instance;

    /**
     * Returns the concrete {@link AbstractConfigurationResolver} instance of the config API
     * implementation.
     *
     * @return the concrete {@link AbstractConfigurationResolver} instance of the config API
     *     implementation
     */
    public static AbstractConfigurationResolver getInstance() {
        if (instance == null) {
            synchronized (AbstractConfigurationResolver.class) {
                if (instance != null) {
                    return instance;
                }
                instance = loadImplementation(AbstractConfigurationResolver.class.getClassLoader());
            }
        }
        return instance;
    }

    private static AbstractConfigurationResolver loadImplementation(final ClassLoader classloader) {
        final ServiceLoader<AbstractConfigurationResolver> serviceLoader =
                ServiceLoader.load(AbstractConfigurationResolver.class, classloader);
        final Iterator<AbstractConfigurationResolver> iterator = serviceLoader.iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        throw new IllegalStateException("No ConfigurationProviderResolver implementation found!");
    }

    /**
     * This method provides the {@link Configuration} instance.
     *
     * @return the configuration instance
     */
    public abstract Configuration getConfig();

    /** This method initialize the configuration */
    public abstract void init();

    /**
     * This method returns true if the configuration is initialized
     *
     * @return true if the configuration is initialized, false otherwise
     */
    public abstract boolean isInitialized();

    /**
     * This method disposes the configuration. Once this method is called the configuration is not
     * initialized anymore.
     */
    public abstract void dispose();

    /**
     * Adds the given config source to the configuration
     *
     * @param configSource the config source
     */
    public abstract void addConfigSource(final ConfigSource configSource);

    /**
     * Adds the given converter to the configuration
     *
     * @param converter the converter
     * @param <T> value type that is supported by the converter
     */
    public abstract <T> void addConverter(final ConfigConverter<T> converter);

    /**
     * Adds the given validator to the configuration
     *
     * @param validator the validator
     */
    public abstract void addValidator(final ConfigValidator validator);

    /**
     * Adds the given constraint for a specific property to the configuration
     *
     * @param propertyName name of the property that should be checked by the constraint
     * @param valueType value type of the property that should be checked by the constraint
     * @param constraint the constraint
     * @param <T> value type of the property that should be checked by the constraint
     */
    public abstract <T> void addConstraint(
            final String propertyName,
            final Class<T> valueType,
            final ConfigPropertyConstraint<T> constraint);

    /**
     * Adds a config data type. {@link ConfigData} provides more information about config data
     * objects.
     *
     * @param type the record of the data config to add
     * @param <T> the generic record type of the data config to add
     * @throws IllegalStateException if the config is already initialized (see {@link
     *     ConfigurationProvider#isInitialized()})
     */
    public abstract <T extends Record> void addConfigDataType(Class<T> type)
            throws IllegalStateException;
}
