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
package com.swirlds.platform.test;

import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.state.StateSettings;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TestSettings implements SettingsProvider {
    public final AtomicBoolean enableBetaMirrorNode = new AtomicBoolean(false);
    public final AtomicInteger rescueChildlessInverseProbability = new AtomicInteger(0);
    public final AtomicInteger randomEventProbability = new AtomicInteger(0);
    public final AtomicReference<Double> throttle7Threshold = new AtomicReference<>(0.0);
    public final AtomicReference<Double> throttle7Extra = new AtomicReference<>(0.0);
    public final AtomicInteger throttle7MaxBytes = new AtomicInteger(0);
    public final AtomicBoolean throttle7Enabled = new AtomicBoolean(false);
    public final AtomicInteger maxEventQueueForCons = new AtomicInteger(0);
    public final AtomicInteger transactionMaxBytes = new AtomicInteger(0);
    public final AtomicInteger signedStateFreq = new AtomicInteger(0);
    public final AtomicInteger throttleTransactionQueueSize = new AtomicInteger(100_000);
    public final AtomicLong delayShuffle = new AtomicLong(0);

    @Override
    public boolean isEnableBetaMirror() {
        return enableBetaMirrorNode.get();
    }

    @Override
    public int getRescueChildlessInverseProbability() {
        return rescueChildlessInverseProbability.get();
    }

    @Override
    public int getRandomEventProbability() {
        return randomEventProbability.get();
    }

    @Override
    public double getThrottle7Threshold() {
        return throttle7Threshold.get();
    }

    @Override
    public double getThrottle7Extra() {
        return throttle7Extra.get();
    }

    @Override
    public int getThrottle7MaxBytes() {
        return throttle7MaxBytes.get();
    }

    @Override
    public boolean isThrottle7Enabled() {
        return throttle7Enabled.get();
    }

    @Override
    public int getMaxEventQueueForCons() {
        return maxEventQueueForCons.get();
    }

    @Override
    public int getTransactionMaxBytes() {
        return transactionMaxBytes.get();
    }

    @Override
    public int getSignedStateFreq() {
        return signedStateFreq.get();
    }

    @Override
    public long getDelayShuffle() {
        return delayShuffle.get();
    }

    @Override
    public int getSocketIpTos() {
        return 5_000;
    }

    @Override
    public int getTimeoutSyncClientSocket() {
        return 5_000;
    }

    @Override
    public int getTimeoutSyncClientConnect() {
        return 5_000;
    }

    @Override
    public int getTimeoutServerAcceptConnect() {
        return 0;
    }

    @Override
    public boolean isTcpNoDelay() {
        return true;
    }

    @Override
    public String getKeystorePassword() {
        return "password";
    }

    @Override
    public boolean isEnableStateRecovery() {
        return false;
    }

    @Override
    public boolean useLoopbackIp() {
        return false;
    }

    public StateSettings getStateSettings() {
        return null;
    }

    @Override
    public int getThrottleTransactionQueueSize() {
        return throttleTransactionQueueSize.get();
    }

    @Override
    public int getMaxTransactionBytesPerEvent() {
        return 245_760;
    }

    @Override
    public int connectionStreamBufferSize() {
        return 0;
    }

    @Override
    public int sleepHeartbeatMillis() {
        return 0;
    }

    @Override
    public String getPlaybackStreamFileDirectory() {
        return null;
    }

    @Override
    public String getPlaybackEndTimeStamp() {
        return null;
    }
}
