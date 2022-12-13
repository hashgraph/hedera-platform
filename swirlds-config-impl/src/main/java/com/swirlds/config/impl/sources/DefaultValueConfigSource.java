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
package com.swirlds.config.impl.sources;

import com.swirlds.common.utility.CommonUtils;
import java.util.Properties;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} implementation that can be used to provide
 * default values of properties programmatically by calling {@link #setDefaultValue(String,
 * String)}. The class is defined as a singleton.
 */
public final class DefaultValueConfigSource extends AbstractConfigSource {

    private static DefaultValueConfigSource instance;

    private final Properties internalProperties;

    private DefaultValueConfigSource() {
        this.internalProperties = new Properties();
    }

    /**
     * Returns the singleton
     *
     * @return the singleton
     */
    public static DefaultValueConfigSource getInstance() {
        if (instance == null) {
            synchronized (DefaultValueConfigSource.class) {
                if (instance != null) {
                    return instance;
                }
                instance = new DefaultValueConfigSource();
            }
        }
        return instance;
    }

    /**
     * Adds a default value to this source
     *
     * @param propertyName name of the peoprty
     * @param value default value
     */
    public static void setDefaultValue(final String propertyName, final String value) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        getInstance().setDefaultValueInternal(propertyName, value);
    }

    private void setDefaultValueInternal(final String propertyName, final String value) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        internalProperties.setProperty(propertyName, value);
    }

    /** {@inheritDoc} */
    @Override
    protected Properties getInternalProperties() {
        return internalProperties;
    }

    /** {@inheritDoc} */
    @Override
    public int getOrdinal() {
        return ConfigSourceOrdinalConstants.DEFAULT_VALUES_ORDINAL;
    }
}
