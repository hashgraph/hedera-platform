/*
 * (c) 2016-2021 Swirlds, Inc.
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

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;

import java.util.AbstractMap;
import java.util.Objects;

import static java.util.Map.Entry;

public class FCMLeaf<K extends MerkleNode, V extends MerkleNode>
		extends AbstractBinaryMerkleInternal
		implements FCMNode<K, V> {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0xc1bd3ae28094acdeL;

	private static class ChildIndices {
		public static final int KEY = 0;
		public static final int VALUE = 1;

		public static final int CHILD_COUNT = 2;
	}

	public FCMLeaf(final K key, final V value) {
		super();
		setKey(key);
		setValue(value);
	}

	public FCMLeaf() {
		super();
	}

	private FCMLeaf(final FCMLeaf<K, V> otherLeaf) {
		super(otherLeaf);
		setImmutable(false);
		otherLeaf.setImmutable(true);
	}

	public K getKey() {
		return getChild(ChildIndices.KEY);
	}

	public void setKey(K key) {
		setChild(ChildIndices.KEY, key);
	}

	public V getValue() {
		return getChild(ChildIndices.VALUE);
	}

	public void setValue(V value) {
		setChild(ChildIndices.VALUE, value);
	}

	/**
	 * A special setter for key and value that recycles existing routes. Much more efficient of the routes for
	 * the key and value exist and are known a priori.
	 *
	 * @param key
	 * 		the key to be set
	 * @param keyRoute
	 * 		the route that describes the location where the key is going
	 * @param value
	 * 		the value to be set
	 * @param valueRoute
	 * 		the route that describes the location where the value is going
	 */
	public void emplaceChildren(final K key, final MerkleRoute keyRoute, final V value, final MerkleRoute valueRoute) {
		setChild(ChildIndices.KEY, key, keyRoute);
		setChild(ChildIndices.VALUE, value, valueRoute);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object object) {
		if (!(object instanceof FCMLeaf)) {
			return false;
		}

		final FCMLeaf<?, ?> otherLeaf = (FCMLeaf<?, ?>) object;
		return Objects.equals(this.getKey(), otherLeaf.getKey());
	}

	public Entry<K, V> getEntry() {
		return new AbstractMap.SimpleEntry<>(this.getKey(), this.getValue());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFCMLeaf() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public BalanceInfo getBalanceInfo() {
		return new BalanceInfo(true, 0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMNode<K, V> getLeftChild() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMNode<K, V> getRightChild() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMLeaf<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		return new FCMLeaf<>(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}
