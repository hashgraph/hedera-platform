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
package com.swirlds.platform;

import com.swirlds.platform.state.StateSettings;

/** A temporary class to bridge circumvent the fact that the Settings class is package private */
public final class StaticSettingsProvider implements SettingsProvider {

    private final Settings settings = Settings.getInstance();

    private static final StaticSettingsProvider SINGLETON = new StaticSettingsProvider();

    public static StaticSettingsProvider getSingleton() {
        return SINGLETON;
    }

    private StaticSettingsProvider() {}

    /** {@inheritDoc} */
    @Override
    public boolean isEnableBetaMirror() {
        return settings.isEnableBetaMirror();
    }

    /** {@inheritDoc} */
    @Override
    public int getRescueChildlessInverseProbability() {
        return settings.getRescueChildlessInverseProbability();
    }

    /** {@inheritDoc} */
    @Override
    public int getRandomEventProbability() {
        return settings.getRandomEventProbability();
    }

    /** {@inheritDoc} */
    @Override
    public double getThrottle7Threshold() {
        return settings.getThrottle7threshold();
    }

    /** {@inheritDoc} */
    @Override
    public double getThrottle7Extra() {
        return settings.getThrottle7extra();
    }

    /** {@inheritDoc} */
    @Override
    public int getThrottle7MaxBytes() {
        return settings.getThrottle7maxBytes();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isThrottle7Enabled() {
        return settings.isThrottle7();
    }

    @Override
    public int getMaxEventQueueForCons() {
        return settings.getMaxEventQueueForCons();
    }

    @Override
    public int getTransactionMaxBytes() {
        return settings.getTransactionMaxBytes();
    }

    @Override
    public int getSignedStateFreq() {
        return settings.getSignedStateFreq();
    }

    @Override
    public long getDelayShuffle() {
        return settings.getDelayShuffle();
    }

    @Override
    public int getSocketIpTos() {
        return settings.getSocketIpTos();
    }

    @Override
    public int getTimeoutSyncClientSocket() {
        return settings.getTimeoutSyncClientSocket();
    }

    @Override
    public int getTimeoutSyncClientConnect() {
        return settings.getTimeoutSyncClientConnect();
    }

    @Override
    public int getTimeoutServerAcceptConnect() {
        return settings.getTimeoutServerAcceptConnect();
    }

    @Override
    public boolean isTcpNoDelay() {
        return settings.isTcpNoDelay();
    }

    @Override
    public String getKeystorePassword() {
        return settings.getCrypto().getKeystorePassword();
    }

    @Override
    public boolean isEnableStateRecovery() {
        return settings.isEnableStateRecovery();
    }

    @Override
    public StateSettings getStateSettings() {
        return settings.getState();
    }

    /**
     * @see Settings#getThrottleTransactionQueueSize()
     */
    @Override
    public int getThrottleTransactionQueueSize() {
        return settings.getThrottleTransactionQueueSize();
    }

    @Override
    public int getMaxTransactionBytesPerEvent() {
        return settings.getMaxTransactionBytesPerEvent();
    }

    @Override
    public boolean useLoopbackIp() {
        return settings.isUseLoopbackIp();
    }

    @Override
    public int connectionStreamBufferSize() {
        return settings.getBufferSize();
    }

    @Override
    public int sleepHeartbeatMillis() {
        return settings.getSleepHeartbeat();
    }

    @Override
    public String getPlaybackStreamFileDirectory() {
        return settings.getPlaybackStreamFileDirectory();
    }

    @Override
    public String getPlaybackEndTimeStamp() {
        return settings.getPlaybackEndTimeStamp();
    }
}
