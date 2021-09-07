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

package com.swirlds.merkletree;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

public class MerklePair<K extends MerkleNode, V extends MerkleNode>
		extends AbstractBinaryMerkleInternal {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0xc1bd3ae28094acdeL;

	public static class ChildIndices {
		public static final int KEY = 0;
		public static final int VALUE = 1;

		public static final int CHILD_COUNT = 2;
	}

	public MerklePair(final K key, final V value) {
		super();
		setKey(key);
		setValue(value);
	}

	public MerklePair() {
		super();
	}

	private MerklePair(final MerklePair<K, V> otherLeaf) {
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
	public void emplaceChildren(final K key,
			final MerkleRoute keyRoute,
			final V value,
			final MerkleRoute valueRoute) {
		setChild(ChildIndices.KEY, key, keyRoute);
		setChild(ChildIndices.VALUE, value, valueRoute);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof MerklePair)) {
			return false;
		}

		final MerklePair<?, ?> otherLeaf = (MerklePair<?, ?>) object;
		return Objects.equals(this.getKey(), otherLeaf.getKey());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(this.getKey());
	}

	public Map.Entry<K, V> getEntry() {
		return new AbstractMap.SimpleEntry<>(this.getKey(), this.getValue());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerklePair<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		return new MerklePair<>(this);
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

	/**
	 * Condition to find a MerklePair with the same key
	 *
	 * @param pair MerklePair
	 * @return Whether or not the pair has the same key
	 */
	public boolean isMatchedKey(final MerklePair<K, V> pair) {
		if (this == pair) {
			return true;
		}

		if (pair == null) {
			return false;
		}

		return this.getKey().equals(pair.getKey());
	}
}
