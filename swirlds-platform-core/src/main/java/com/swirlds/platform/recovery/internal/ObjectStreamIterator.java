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

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;

/** Iterates over objects in an object stream. */
public class ObjectStreamIterator<T extends SelfSerializable> implements IOIterator<T> {

    /** The base input stream. */
    private final SerializableDataInputStream in;

    /**
     * Once we encounter the first IO exception, we want to report this exception any time somebody
     * attempts to do something that is not possible due to the exception.
     */
    private Exception exception;

    /**
     * The next object that will be returned by {@link #next()}, or null if no object is ready to be
     * returned.
     */
    private T next;

    /**
     * If true, then allow for the object file to be abbruptly terminated, and return the
     * non-currpted data at the begining of the file.
     */
    private final boolean toleratePartialFile;

    private static final int EXPECTED_OBJECT_STREAM_VERSION = 1;
    private static final int EXPECTED_RECORD_STREAM_VERSION = 5;

    /**
     * Create an iterator that reads from a file.
     *
     * @param objectStreamFile the file to read from
     * @param toleratePartialFile if true then allow the stream file to end abruptly (possibly
     *     mid-object), and return all objects that are complete within the stream. If false then
     *     throw if the file is incomplete.
     * @throws FileNotFoundException if the file does not exist or can't be read
     */
    public ObjectStreamIterator(final Path objectStreamFile, final boolean toleratePartialFile)
            throws IOException {
        this(
                new BufferedInputStream(new FileInputStream(objectStreamFile.toFile())),
                toleratePartialFile);
    }

    /**
     * Create an iterator from an input stream.
     *
     * @param in the input stream
     * @param toleratePartialFile if true then allow the stream file to end abruptly (possibly
     *     mid-object), and return all objects that are complete within the stream. If false then
     *     throw if the file is incomplete.
     */
    public ObjectStreamIterator(final InputStream in, final boolean toleratePartialFile)
            throws IOException {
        this.in = new SerializableDataInputStream(in);
        this.toleratePartialFile = toleratePartialFile;

        try {
            final int eventStreamVersion = this.in.readInt();
            if (eventStreamVersion != EXPECTED_RECORD_STREAM_VERSION) {
                throw new IOException("unexpected event stream version " + eventStreamVersion);
            }

            final int objectStreamVersion = this.in.readInt();
            if (objectStreamVersion != EXPECTED_OBJECT_STREAM_VERSION) {
                throw new IOException("unexpected object stream version " + objectStreamVersion);
            }

        } catch (final IOException ioException) {
            if (toleratePartialFile) {
                exception = ioException;
            } else {
                throw ioException;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() throws IOException {
        if (exception != null) {
            if (toleratePartialFile) {
                return false;
            } else {
                throw new IOException(exception);
            }
        }

        if (next == null && in.available() > 0) {
            try {
                next = in.readSerializable();
            } catch (final IOException e) {
                exception = e;
                if (toleratePartialFile) {
                    return false;
                } else {
                    throw e;
                }
            }
        }

        return next != null;
    }

    /** {@inheritDoc} */
    @Override
    public T next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        try {
            return next;
        } finally {
            next = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public T peek() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return next;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        try {
            in.close();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
