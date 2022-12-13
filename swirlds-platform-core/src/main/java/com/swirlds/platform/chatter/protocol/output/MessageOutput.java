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
package com.swirlds.platform.chatter.protocol.output;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;

/**
 * Manages messages that need to be sent to multiple peers
 *
 * @param <T> the type of message managed
 */
public interface MessageOutput<T extends SelfSerializable> {
    /**
     * Creates an instance responsible for sending messages to one particular peer
     *
     * @param communicationState the state of communication with this chatter peer
     * @param sendCheck invoked before a message is about to be sent, to determine if it should be
     *     sent or not
     * @return a message provider for a peer
     */
    MessageProvider createPeerInstance(
            final CommunicationState communicationState, final SendCheck<T> sendCheck);

    /**
     * Send a message to all peers
     *
     * @param message the message to send
     */
    void send(T message);
}
