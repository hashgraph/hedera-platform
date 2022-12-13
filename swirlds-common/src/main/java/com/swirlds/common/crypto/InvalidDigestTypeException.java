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
package com.swirlds.common.crypto;

import com.swirlds.logging.LogMarker;

/** Exception caused when Invalid algorithm name was provided */
public class InvalidDigestTypeException extends CryptographyException {

    private static final String MESSAGE_TEMPLATE = "Invalid algorithm name was provided (%s)";

    public InvalidDigestTypeException(final String algorithmName) {
        super(String.format(MESSAGE_TEMPLATE, algorithmName), LogMarker.TESTING_EXCEPTIONS);
    }

    public InvalidDigestTypeException(
            final String algorithmName, final Throwable cause, final LogMarker logMarker) {
        super(String.format(MESSAGE_TEMPLATE, algorithmName), cause, LogMarker.TESTING_EXCEPTIONS);
    }
}
