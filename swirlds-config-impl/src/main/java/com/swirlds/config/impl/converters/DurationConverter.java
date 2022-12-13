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
package com.swirlds.config.impl.converters;

import com.swirlds.common.settings.ParsingUtils;
import com.swirlds.config.api.converter.ConfigConverter;
import java.time.Duration;

/**
 * Concrete {@link ConfigConverter} implementation that provides the support for {@link Duration}
 * values in the configuration
 */
public final class DurationConverter implements ConfigConverter<Duration> {

    /** {@inheritDoc} */
    @Override
    public Duration convert(final String value) throws IllegalArgumentException {
        return ParsingUtils.parseDuration(value);
    }
}