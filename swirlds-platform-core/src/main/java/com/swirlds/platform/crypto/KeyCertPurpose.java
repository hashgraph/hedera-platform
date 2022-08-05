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

package com.swirlds.platform.crypto;

/**
 * Denotes which of the three purposes a key or certificate serves
 */
public enum KeyCertPurpose {
	SIGNING("s"),
	AGREEMENT("a"),
	ENCRYPTION("e");

	/** the prefix used for certificate names */
	private final String prefix;

	KeyCertPurpose(final String prefix) {
		this.prefix = prefix;
	}

	/**
	 * @param memberName
	 * 		the name of the required member
	 * @return the name of the key or certificate used in a KeyStore for this member and key type
	 */
	public String storeName(final String memberName) {
		return prefix + "-" + memberName;
	}
}
