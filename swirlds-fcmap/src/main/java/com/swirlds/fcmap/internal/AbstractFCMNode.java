/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.fcmap.internal;

import com.swirlds.common.FCMKey;
import com.swirlds.common.FCMValue;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;

public abstract class AbstractFCMNode<K extends FCMKey, V extends FCMValue> extends AbstractMerkleInternal
		implements FCMNode<K, V> {

	private FCMInternalNode<K, V> parent;

	public AbstractFCMNode() {
		super();
	}

	public AbstractFCMNode(final FCMInternalNode<K, V> parent) {
		this();
		this.setParent(parent);
	}

	@Override
	public void setParent(final FCMInternalNode<K, V> parent) {
		this.parent = parent;
	}

	@Override
	public FCMInternalNode<K, V> getParent() {
		return this.parent;
	}

	@Override
	public boolean isPathUnique() {
		AbstractFCMNode<K, V> next = this;
		while (next != null) {
			if (next.getReferenceCount() > 1) {
				return false;
			}
			next = next.parent;
		}
		return true;
	}

	@Override
	public void nullifyHashPath() {
		AbstractFCMNode<K, V> next = this;
		while (next != null) {
			if (next.getHash() == null) {
				break;
			}
			next.invalidateHash();
			next = next.parent;
		}
	}

	@Override
	public String toString() {
		return String.format("%s has parent %s. Reference count: %d. Deleted: %b",
				super.toString(),
				this.getParent() == null ? null : this.getParent().getMemoryReference(),
				this.getReferenceCount(),
				this.isReleased());
	}

	@Override
	public String getMemoryReference() {
		return super.toString();
	}
}
