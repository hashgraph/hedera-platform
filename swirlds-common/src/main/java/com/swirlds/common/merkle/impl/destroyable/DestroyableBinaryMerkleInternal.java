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

package com.swirlds.common.merkle.impl.destroyable;

import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.internal.AbstractMerkleNode;
import com.swirlds.common.utility.CommonUtils;

/**
 * A variant of {@link PartialBinaryMerkleInternal} that stores a callback that is invoked when
 * {@link AbstractMerkleNode#destroyNode() destroyNode()} is called. This class does not provide
 * any exception handling when destroyNode() is called.
 */
public class DestroyableBinaryMerkleInternal extends PartialBinaryMerkleInternal {

	private final Runnable onDestroy;

	/**
	 * Create a new abstract node
	 *
	 * @param onDestroy
	 * 		called when this node is destroyed
	 */
	public DestroyableBinaryMerkleInternal(final Runnable onDestroy) {
		this.onDestroy = CommonUtils.throwArgNull(onDestroy, "onDestroy");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void destroyNode() {
		onDestroy.run();
	}
}
