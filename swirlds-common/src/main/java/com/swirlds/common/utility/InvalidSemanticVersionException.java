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
package com.swirlds.common.utility;

/** Thrown by {@link SemanticVersion#parse(String)} when a version string cannot be parsed. */
public class InvalidSemanticVersionException extends RuntimeException {

    /** {@inheritDoc} */
    public InvalidSemanticVersionException() {}

    /** {@inheritDoc} */
    public InvalidSemanticVersionException(final String message) {
        super(message);
    }

    /** {@inheritDoc} */
    public InvalidSemanticVersionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /** {@inheritDoc} */
    public InvalidSemanticVersionException(final Throwable cause) {
        super(cause);
    }
}
