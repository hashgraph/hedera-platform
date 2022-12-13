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
package com.swirlds.platform.network.communication.states;

import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorBytes;
import com.swirlds.platform.network.protocol.Protocol;
import java.io.IOException;
import java.io.InputStream;

/**
 * A protocol initiate was sent, this state waits for and handles the byte sent by the peer in
 * parallel
 */
public class SentInitiate implements NegotiationState {
    private final NegotiationProtocols protocols;
    private final InputStream byteInput;

    private final ProtocolNegotiated negotiated;
    private final ReceivedInitiate receivedInitiate;
    private final WaitForAcceptReject waitForAcceptReject;
    private final NegotiationState sleep;

    private int protocolInitiated = NegotiatorBytes.UNINITIALIZED;

    /**
     * @param protocols protocols being negotiated
     * @param byteInput the stream to read from
     * @param negotiated the state to transition to if a protocol gets negotiated
     * @param receivedInitiate the state to transition to if we need to reply to the peer's initiate
     * @param waitForAcceptReject the state to transition to if the peer needs to reply to our
     *     initiate
     * @param sleep the sleep state to transition to if the negotiation fails
     */
    public SentInitiate(
            final NegotiationProtocols protocols,
            final InputStream byteInput,
            final ProtocolNegotiated negotiated,
            final ReceivedInitiate receivedInitiate,
            final WaitForAcceptReject waitForAcceptReject,
            final NegotiationState sleep) {
        this.protocols = protocols;
        this.byteInput = byteInput;
        this.negotiated = negotiated;
        this.receivedInitiate = receivedInitiate;
        this.waitForAcceptReject = waitForAcceptReject;
        this.sleep = sleep;
    }

    /**
     * Set the protocol ID that was initiated by us
     *
     * @param protocolId the ID of the protocol initiated
     * @return this state
     */
    public NegotiationState initiatedProtocol(final byte protocolId) {
        protocolInitiated = protocolId;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public NegotiationState transition()
            throws NegotiationException, NetworkProtocolException, InterruptedException,
                    IOException {
        final int b = byteInput.read();
        NegotiatorBytes.checkByte(b);
        final NegotiationState next = transition(b);
        protocolInitiated = NegotiatorBytes.UNINITIALIZED;
        return next;
    }

    private NegotiationState transition(final int b) {
        if (b == NegotiatorBytes.KEEPALIVE) {
            // we wait for ACCEPT or reject
            return waitForAcceptReject;
        }
        if (b == protocolInitiated) { // both initiated the same protocol at the same time
            final Protocol protocol = protocols.getInitiatedProtocol();
            if (protocol.acceptOnSimultaneousInitiate()) {
                return negotiated.runProtocol(protocols.initiateAccepted());
            } else {
                protocols.initiateFailed();
                return sleep;
            }
        }
        // peer initiated a different protocol
        if (b < protocolInitiated) { // lower index means higher priority
            // the one we initiated failed
            protocols.initiateFailed();
            // THEIR protocol is higher priority, so we should ACCEPT or REJECT
            return receivedInitiate.receivedInitiate(b);
        } else {
            // OUR protocol is higher priority, so they should ACCEPT or REJECT
            return waitForAcceptReject;
        }
    }
}
