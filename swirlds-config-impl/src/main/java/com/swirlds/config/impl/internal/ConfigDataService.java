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

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This service provides instances of config data objects (see {@link
 * com.swirlds.config.api.ConfigData} for a detailed description).
 */
class ConfigDataService implements ConfigLifecycle {

    /** The factory that create data object instrances */
    private final ConfigDataFactory configDataFactory;

    /** A set of all regisstered data object types */
    private Queue<Class<? extends Record>> registeredTypes;

    /** A map that contains all created data objects */
    private Map<Class<? extends Record>, Record> configDataCache;

    /** Defines if the service is initialized */
    private boolean initialized = false;

    ConfigDataService(final Configuration configuration, final ConverterService converterService) {
        this.configDataFactory = new ConfigDataFactory(configuration, converterService);
        configDataCache = new HashMap<>();
        registeredTypes = new ConcurrentLinkedQueue<>();
    }

    /**
     * Adds the given type as a config data object type. This is only possible if the service is not
     * initialized
     *
     * @param type the type that should be added as a supported config data object
     * @param <T> generic type of the config data object
     */
    <T extends Record> void addConfigDataType(final Class<T> type) {
        CommonUtils.throwArgNull(type, "type");
        throwIfInitialized();
        registeredTypes.add(type);
    }

    @Override
    public void init() {
        throwIfInitialized();
        registeredTypes.stream()
                .forEach(
                        type -> {
                            try {
                                final Record dataInstance =
                                        configDataFactory.createConfigInstance(type);
                                configDataCache.put(type, dataInstance);
                            } catch (final Exception e) {
                                throw new IllegalStateException(
                                        "Can not create config data for record type '" + type + "'",
                                        e);
                            }
                        });
        initialized = true;
    }

    @Override
    public void dispose() {
        registeredTypes.clear();
        configDataCache.clear();
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the instance of the given config data type. If the given type is not registered (see
     * {@link #addConfigDataType(Class)}) the method throws an {@link IllegalArgumentException}
     *
     * @param type the config data type
     * @param <T> the config data type
     * @return the instance of the given config data type
     */
    <T extends Record> T getConfigData(final Class<T> type) {
        CommonUtils.throwArgNull(type, "type");
        throwIfNotInitialized();
        if (!configDataCache.containsKey(type)) {
            throw new IllegalArgumentException(
                    "No config data record available of type '" + type + "'");
        }
        return (T) configDataCache.get(type);
    }

    /**
     * Returns all config data types that are registered
     *
     * @return all config data types that are registered
     */
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        return registeredTypes;
    }
}
