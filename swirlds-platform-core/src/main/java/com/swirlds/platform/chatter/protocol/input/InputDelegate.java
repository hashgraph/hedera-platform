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
package com.swirlds.platform.chatter.protocol.input;

import com.swirlds.common.constructable.ClassIdFormatter;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.PeerMessageException;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;
import com.swirlds.platform.stats.PerSecondStat;
import java.util.List;

/**
 * Determines the type of message received from a peer and passes it on to an appropriate handler
 */
public class InputDelegate implements PeerMessageHandler {
    private final List<MessageTypeHandler<? extends SelfSerializable>> handlers;
    private final PerSecondStat msgPerSecond;

    public InputDelegate(
            final List<MessageTypeHandler<? extends SelfSerializable>> handlers,
            final PerSecondStat msgPerSecond) {
        this.handlers = handlers;
        this.msgPerSecond = msgPerSecond;
    }

    /** {@inheritDoc} */
    @Override
    public void handleMessage(final SelfSerializable message) throws PeerMessageException {
        msgPerSecond.increment();
        for (final MessageTypeHandler<?> caster : handlers) {
            if (caster.castHandleMessage(message)) {
                return;
            }
        }
        throw new PeerMessageException(
                "Unrecognized message type: " + ClassIdFormatter.classIdString(message));
    }
}
