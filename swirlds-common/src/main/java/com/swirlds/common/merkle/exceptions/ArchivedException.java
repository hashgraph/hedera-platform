/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.merkle.exceptions;

/**
 * This exception is thrown if there is an error due to the archival state of a merkle node.
 */
public class ArchivedException extends RuntimeException {

	public ArchivedException() {
	}

	public ArchivedException(final String message) {
		super(message);
	}

	public ArchivedException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ArchivedException(final Throwable cause) {
		super(cause);
	}

	public ArchivedException(final String message, final Throwable cause, final boolean enableSuppression,
			final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
