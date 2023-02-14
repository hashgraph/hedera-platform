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
package com.swirlds.platform.recovery.internal;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.NoSuchElementException;

/** An iterator that walks over events in a single event stream file. */
public class EventStreamSingleFileIterator implements IOIterator<DetailedConsensusEvent> {

    private final IOIterator<SelfSerializable> iterator;

    private final Hash startHash;
    private Hash endHash;
    private final boolean toleratePartialFile;

    /**
     * Create an iterator that walks over an event stream file.
     *
     * @param objectStreamFile the file
     * @param toleratePartialFile if true then allow the event stream file to end abruptly (possibly
     *     mid-event), and return all events that are complete within the stream. If false then
     *     throw if the file is incomplete.
     * @throws IOException
     */
    public EventStreamSingleFileIterator(
            final Path objectStreamFile, final boolean toleratePartialFile) throws IOException {
        this(
                new BufferedInputStream(new FileInputStream(objectStreamFile.toFile())),
                toleratePartialFile);
    }

    /**
     * Create an iterator that walks over an event stream file.
     *
     * @param in the input stream
     * @param toleratePartialFile if true then allow the event stream file to end abruptly (possibly
     *     mid-event), and return all events that are complete within the stream. If false then
     *     throw if the file is incomplete.
     * @throws IOException
     */
    public EventStreamSingleFileIterator(final InputStream in, final boolean toleratePartialFile)
            throws IOException {

        iterator = new ObjectStreamIterator<>(in, toleratePartialFile);
        this.toleratePartialFile = toleratePartialFile;

        if (!iterator.hasNext()) {
            startHash = null;
            throw new IOException("event stream file has not objects");
        }

        // First thing in the stream is a hash
        if (!iterator.hasNext()) {
            throw new IOException("iterator contains no objects");
        }
        final SelfSerializable firstObject = iterator.next();
        if (firstObject != null && firstObject.getClassId() != Hash.CLASS_ID) {
            throw new IOException(
                    "Illegal object in event stream file at position 0, expected a Hash: "
                            + firstObject.getClass());
        }
        startHash = (Hash) firstObject;

        // An event stream is required to have at least 1 event to be considered valid
        if (!hasNext()) {
            throw new IOException("event stream does not contain any events");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        iterator.close();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() throws IOException {

        if (!iterator.hasNext()) {
            if (endHash == null && !toleratePartialFile) {
                throw new IOException("file terminates early");
            }
            return false;
        }

        if (endHash != null || !iterator.hasNext()) {
            return false;
        }

        final SelfSerializable next = iterator.peek();
        if (next == null) {
            throw new IOException("null object in the event stream");
        }

        if (next.getClassId() == Hash.CLASS_ID) {
            endHash = (Hash) next;
            return false;
        }

        if (next.getClassId() != DetailedConsensusEvent.CLASS_ID) {
            throw new IOException("Invalid object type found in event stream: " + next.getClass());
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public DetailedConsensusEvent peek() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return (DetailedConsensusEvent) iterator.peek();
    }

    /** {@inheritDoc} */
    @Override
    public DetailedConsensusEvent next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return (DetailedConsensusEvent) iterator.next();
    }

    /** Get the hash at the start of the event stream. */
    public Hash getStartHash() {
        return startHash;
    }

    /** Get the hash at the end of the event stream. */
    public Hash getEndHash() {
        return endHash;
    }
}
