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

import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.spi.AbstractConfigurationResolver;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigValidator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class provides the implementation of the config SPI. The class will automatically be loaded
 * at runtime by using service loader in {@link AbstractConfigurationResolver} and is the entry
 * point to the internal implementation of the config API.
 */
public class ConfigurationProviderResolverImpl extends AbstractConfigurationResolver
        implements ConfigLifecycle {

    private static final Logger LOG = LogManager.getLogger(ConfigurationProviderResolverImpl.class);

    private final AutoClosableLock initializationLock = Locks.createAutoLock();

    private final ConfigurationImpl configuration;

    private final ConfigSourceService configSourceService;

    private final ConverterService converterService;

    private final ConfigValidationService validationService;

    private final ConfigPropertiesService propertiesService;

    private AtomicBoolean initialized = new AtomicBoolean();

    /** Default constructor for creation by SPI */
    public ConfigurationProviderResolverImpl() {
        configSourceService = new ConfigSourceService();
        converterService = new ConverterService();
        propertiesService = new ConfigPropertiesService(configSourceService);
        validationService = new ConfigValidationService(converterService);
        configuration =
                new ConfigurationImpl(propertiesService, converterService, validationService);
    }

    /** {@inheritDoc} */
    @Override
    public Configuration getConfig() {
        return configuration;
    }

    /** {@inheritDoc} */
    @Override
    public void init() {
        try (final Locked ignored = initializationLock.lock()) {
            if (isInitialized()) {
                throw new IllegalStateException("Configuration already initialized");
            }
            try {
                configSourceService.init();
                converterService.init();
                propertiesService.init();
                validationService.init();
                configuration.init();
                initialized.set(true);
            } catch (final Exception initException) {
                try {
                    dispose();
                } catch (final Exception disposeException) {
                    LOG.error(
                            "error in dispose while trying to recover failed init",
                            disposeException);
                } finally {
                    throw initException;
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInitialized() {
        return initialized.get();
    }

    /** {@inheritDoc} */
    @Override
    public void dispose() {
        try (final Locked ignored = initializationLock.lock()) {
            initialized.set(false);
            configuration.dispose();
            validationService.dispose();
            propertiesService.dispose();
            converterService.dispose();
            configSourceService.dispose();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addConfigSource(final ConfigSource configSource) {
        CommonUtils.throwArgNull(configSource, "configSource");
        if (configuration.isInitialized()) {
            throw new IllegalStateException("ConfigSource can not be added to initialized config");
        }
        configSourceService.addConfigSource(configSource);
    }

    /** {@inheritDoc} */
    @Override
    public <T> void addConverter(final ConfigConverter<T> converter) {
        CommonUtils.throwArgNull(converter, "converter");
        if (configuration.isInitialized()) {
            throw new IllegalStateException("Converters can not be added to initialized config");
        }
        converterService.addConverter(converter);
    }

    /** {@inheritDoc} */
    @Override
    public void addValidator(final ConfigValidator validator) {
        CommonUtils.throwArgNull(validator, "validator");
        if (configuration.isInitialized()) {
            throw new IllegalStateException(
                    "ConfigValidator can not be added to initialized config");
        }
        validationService.addValidator(validator);
    }

    /** {@inheritDoc} */
    @Override
    public <T> void addConstraint(
            final String propertyName,
            final Class<T> valueType,
            final ConfigPropertyConstraint<T> validator) {
        CommonUtils.throwArgBlank(propertyName, propertyName);
        CommonUtils.throwArgNull(valueType, "valueType");
        CommonUtils.throwArgNull(validator, "validator");
        if (configuration.isInitialized()) {
            throw new IllegalStateException(
                    "ConfigPropertyConstraint can not be added to initialized config");
        }
        validationService.addConstraint(propertyName, valueType, validator);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Record> void addConfigDataType(final Class<T> type) {
        configuration.addConfigDataType(type);
    }
}
