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

package com.swirlds.platform.state.address;

/**
 * Provides static methods for managing address book settings.
 */
public final class AddressBookSettingsFactory {

	private static AddressBookSettings addressBookSettings;

	private AddressBookSettingsFactory() {

	}

	/**
	 * The default settings for address books. These defaults will only end up being used during unit tests.
	 */
	private static AddressBookSettings getDefaultSettings() {
		return new AddressBookSettings() {
			@Override
			public boolean isUpdateAddressBookOnlyAtUpgrade() {
				return true;
			}
		};
	}

	/**
	 * Hook that {@code Browser#populateSettingsCommon()} will use to attach the instance of
	 * {@link AddressBookSettings} obtained by parsing <i>settings.txt</i>.
	 */
	public static void configure(final AddressBookSettings addressBookSettings) {
		AddressBookSettingsFactory.addressBookSettings = addressBookSettings;
	}

	/**
	 * Get the configured settings for address books.
	 */
	public static AddressBookSettings get() {
		if (addressBookSettings == null) {
			addressBookSettings = getDefaultSettings();
		}
		return addressBookSettings;
	}

}
