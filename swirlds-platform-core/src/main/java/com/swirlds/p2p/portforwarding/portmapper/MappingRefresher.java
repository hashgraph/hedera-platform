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
package com.swirlds.p2p.portforwarding.portmapper;

import com.swirlds.p2p.portforwarding.PortForwarder;

public class MappingRefresher implements Runnable {
    private PortForwarder forwarder;
    private long sleep;

    public MappingRefresher(PortForwarder forwarder, long sleep) {
        super();
        this.forwarder = forwarder;
        this.sleep = sleep;
    }

    public void run() {
        // Refresh mapping half-way through the lifetime of the mapping (for example,
        // if the mapping is available for 40 seconds, refresh it every 20 seconds)
        while (true) {
            try {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }

                forwarder.refreshMappings();
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
