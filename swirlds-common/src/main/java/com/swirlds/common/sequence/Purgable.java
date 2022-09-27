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

package com.swirlds.common.sequence;

/**
 * An instance that holds data which has generation associated with it and needs to get rid of old data regularly
 */
public interface Purgable {
	/**
	 * Purge all data with a generation older (lower number) than the specified generation
	 *
	 * @param olderThan
	 * 		all data associated with a generation strictly less than this value should be erased from memory
	 */
	void purge(long olderThan);
}
