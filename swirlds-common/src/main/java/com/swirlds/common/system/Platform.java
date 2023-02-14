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
package com.swirlds.common.system;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.stream.Signer;

/** An interface for Swirlds Platform. */
public interface Platform extends PlatformIdentity, StateAccessor, Signer, TransactionSubmitter {

    /**
     * Gets the {@link Cryptography} instance attached to this platform. The provided instance is
     * already configured with the settings defined for this {@link Platform}. The returned
     * reference may be a single disposal instance or may be a singleton.
     *
     * @return a preconfigured cryptography instance
     * @deprecated use the platform context instead
     */
    @Deprecated(forRemoval = true)
    Cryptography getCryptography();

    /**
     * Get a reference to the metrics-system of the current node
     *
     * @return the reference to the metrics-system
     * @deprecated use the platform context instead
     */
    @Deprecated(forRemoval = true)
    Metrics getMetrics();

    /**
     * Get the notification engine running on this node.
     *
     * @return a notification engine
     */
    NotificationEngine getNotificationEngine();

    /**
     * Get the transactionMaxBytes in Settings
     *
     * @return integer representing the maximum number of bytes allowed in a transaction
     * @deprecated access "transactionMaxBytes" configuration directly instead of using this method
     */
    @Deprecated(forRemoval = true)
    static int getTransactionMaxBytes() {
        return SettingsCommon.transactionMaxBytes;
    }
}
