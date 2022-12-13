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
package com.swirlds.logging.payloads;

/** A message that gets logged when a node receives a signature for a state that is invalid. */
public class IssPayload extends AbstractLogPayload {
    private long round;
    private String selfHash;
    private String consensusHash;
    private boolean catastrophic;

    public IssPayload() {}

    /**
     * Create a new payload for an ISS.
     *
     * @param message the human-readable message
     * @param round the round for which the ISS was received
     * @param selfHash a string representation of the hash computed by this node
     * @param consensusHash a string representation of the hash computed by the network, or an empty
     *     string if there was no consensus hash
     * @param catastrophic if this was a catastrophic ISS
     */
    public IssPayload(
            final String message,
            final long round,
            final String selfHash,
            final String consensusHash,
            final boolean catastrophic) {
        super(message);
        this.round = round;
        this.selfHash = selfHash;
        this.consensusHash = consensusHash;
        this.catastrophic = catastrophic;
    }

    /** Get the round when the ISS was observed. */
    public long getRound() {
        return round;
    }

    /** Set the round when the ISS was observed. */
    public void setRound(long round) {
        this.round = round;
    }

    /** Get the hash computed by this node. */
    public String getSelfHash() {
        return selfHash;
    }

    /** Set the hash computed by this node. */
    public void setSelfHash(final String selfHash) {
        this.selfHash = selfHash;
    }

    /**
     * Get the consensus hash, or an empty string if there is no consensus hash (i.e. catastrophic
     * ISS).
     */
    public String getConsensusHash() {
        return consensusHash;
    }

    /**
     * Set the consensus hash, or an empty string if there is no consensus hash (i.e. catastrophic
     * ISS).
     */
    public void setConsensusHash(final String consensusHash) {
        this.consensusHash = consensusHash;
    }

    /** Check if this is a catastrophic ISS. */
    public boolean isCatastrophic() {
        return catastrophic;
    }

    /** Set if this is a catastrophic ISS. */
    public void setCatastrophic(final boolean catastrophic) {
        this.catastrophic = catastrophic;
    }
}
