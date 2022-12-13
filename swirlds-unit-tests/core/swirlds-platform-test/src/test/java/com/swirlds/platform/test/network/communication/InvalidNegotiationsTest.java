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
package com.swirlds.platform.test.network.communication;

import static com.swirlds.platform.network.communication.NegotiatorBytes.ACCEPT;
import static com.swirlds.platform.network.communication.NegotiatorBytes.KEEPALIVE;
import static com.swirlds.platform.test.network.communication.NegotiatorTestSuite.PROTOCOL_2;

import com.swirlds.platform.network.communication.NegotiationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvalidNegotiationsTest {
    @DisplayName("Test an ACCEPT byte sent in the initial phase of negotiation")
    @Test
    void invalidAccept() {
        final NegotiatorTestSuite suite = new NegotiatorTestSuite();
        suite.input(ACCEPT);
        suite.expectedOutput(KEEPALIVE);
        Assertions.assertThrows(
                NegotiationException.class,
                suite::execute,
                "an ACCEPT sent at the beginning of the negotiation is not valid and should throw");
        suite.assertProtocolRuns(0, 0, 0);
    }

    @DisplayName("Test an invalid byte sent in the initial phase of negotiation")
    @Test
    void invalidByte() {
        final NegotiatorTestSuite suite = new NegotiatorTestSuite();
        suite.input(-1);
        suite.expectedOutput(KEEPALIVE);
        Assertions.assertThrows(
                NegotiationException.class,
                suite::execute,
                "an negative byte sent at any point in the negotiation is not valid and should"
                        + " throw");
        suite.assertProtocolRuns(0, 0, 0);
    }

    @DisplayName("Test an invalid protocol being initiated")
    @Test
    void invalidProtocolInitiated() {
        final NegotiatorTestSuite suite = new NegotiatorTestSuite();
        suite.input(NegotiatorTestSuite.INVALID_PROTOCOL);
        suite.expectedOutput(KEEPALIVE);
        Assertions.assertThrows(
                NegotiationException.class,
                suite::execute,
                "if a non-existent protocol ID is sent it should throw");
        suite.assertProtocolRuns(0, 0, 0);
    }

    @DisplayName("Test a valid protocol ID sent when an ACCEPT or REJECT should be sent")
    @Test
    void invalidAcceptReject() {
        final NegotiatorTestSuite suite = new NegotiatorTestSuite();
        suite.getProtocol(PROTOCOL_2).setShouldInitiate(true);
        suite.input(KEEPALIVE, PROTOCOL_2);
        suite.expectedOutput(PROTOCOL_2);
        Assertions.assertThrows(
                NegotiationException.class,
                suite::execute,
                "if the negotiator initiated a protocol, we are expected to reply ACCEPT or REJECT."
                        + " if we send anything else it should throw");
        suite.assertProtocolRuns(0, 0, 0);
    }
}
