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

package com.swirlds.common.test.benchmark;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.utility.AutoCloseableWrapper;

/**
 * Get a state to be used in an operation.
 *
 * @param <S>
 * 		the type of the state
 */
public interface StateManager<S extends MerkleNode> {

	AutoCloseableWrapper<S> getState();

}
