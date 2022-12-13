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
package com.swirlds.platform.chatter.protocol.heartbeat;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import java.util.concurrent.atomic.AtomicLong;

/** Responds to heartbeat requests. Only holds the last request received. */
public class HeartbeatResponder implements MessageProvider, MessageHandler<HeartbeatMessage> {
    private static final long NO_ID = Long.MIN_VALUE;
    private final AtomicLong idReceived = new AtomicLong();

    public HeartbeatResponder() {
        clear();
    }

    @Override
    public void clear() {
        idReceived.set(NO_ID);
    }

    @Override
    public void handleMessage(final HeartbeatMessage message) {
        idReceived.set(message.getHeartbeatId());
    }

    @Override
    public SelfSerializable getMessage() {
        final long id = idReceived.getAndSet(NO_ID);
        if (id != NO_ID) {
            return HeartbeatMessage.response(id);
        }
        return null;
    }
}
