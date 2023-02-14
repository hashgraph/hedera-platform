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
package com.swirlds.fchashmap;

import java.time.Duration;

/**
 * This object is used to configure general FCHashMap settings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near
 *     future. If you need to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public final class FCHashMapSettingsFactory {

    private static FCHashMapSettings settings;

    private FCHashMapSettingsFactory() {}

    /** Specify the settings that should be used for the FCHashMap. */
    public static void configure(FCHashMapSettings settings) {
        FCHashMapSettingsFactory.settings = settings;
    }

    /** Get the settings for FCHashMap. */
    public static FCHashMapSettings get() {
        if (settings == null) {
            settings = getDefaultSettings();
        }
        return settings;
    }

    /** Get default FCHashMap settings. Useful for testing. */
    private static FCHashMapSettings getDefaultSettings() {
        return new FCHashMapSettings() {
            @Override
            public int getMaximumGCQueueSize() {
                return 200;
            }

            @Override
            public Duration getGCQueueThresholdPeriod() {
                return Duration.ofMinutes(1);
            }

            @Override
            public boolean isArchiveEnabled() {
                return true;
            }

            @Override
            public int getRebuildSplitFactor() {
                return 7;
            }

            @Override
            public int getRebuildThreadCount() {
                return Runtime.getRuntime().availableProcessors();
            }
        };
    }
}
