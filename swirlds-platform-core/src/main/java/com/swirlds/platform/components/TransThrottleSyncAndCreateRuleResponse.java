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

package com.swirlds.platform.components;

/**
 * Types of response of {@link TransThrottleSyncAndCreateRule}
 */
public enum TransThrottleSyncAndCreateRuleResponse {
	/**
	 * should not initiate a sync and create an event, and don't check subsequent rules
	 */
	DONT_SYNC_OR_CREATE,
	/**
	 * should initiate a sync and create an event, and don't check subsequent rules
	 */
	SYNC_AND_CREATE,
	/**
	 * continue with checking subsequent rules
	 */
	PASS
}
