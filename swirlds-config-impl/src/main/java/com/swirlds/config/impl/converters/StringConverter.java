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
package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;

/**
 * Concrete {@link ConfigConverter} implementation that provides the support for {@link String}
 * values in the configuration
 */
public class StringConverter implements ConfigConverter<String> {

    /** {@inheritDoc} */
    @Override
    public String convert(final String value) throws IllegalArgumentException {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        return value;
    }
}
