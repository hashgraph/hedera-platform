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
package com.swirlds.config.impl;

import com.swirlds.config.impl.sources.AbstractConfigSource;
import java.util.Properties;

public class DummyConfigSource extends AbstractConfigSource {

    private final Properties internalProperties;

    public DummyConfigSource() {
        this.internalProperties = new Properties();
    }

    public void clear() {
        internalProperties.clear();
    }

    public void setProperty(final String propertyName, final String value) {
        internalProperties.setProperty(propertyName, value);
    }

    @Override
    protected Properties getInternalProperties() {
        return internalProperties;
    }

    @Override
    public String getName() {
        return "DUMMY";
    }

    @Override
    public int getOrdinal() {
        return Integer.MAX_VALUE;
    }
}
