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
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializedObjectProvider;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Objects;

import static java.util.Map.Entry;

public class FCMLeaf<K extends FCMKey, V extends FCMValue>
		extends AbstractFCMNode<K, V> {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0xc1bd3ae28094acdeL;

	private static final String DELETE_MESSAGE = "FCMLeaf has been deleted";

	private static class ChildIndices {
		public static final int KEY = 0;
		public static final int VALUE = 1;

		public static final int CHILD_COUNT = 2;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren() {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaximumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	public K getKey() {
		return getChild(ChildIndices.KEY);
	}

	private void setKey(K key) {
		setChild(ChildIndices.KEY, key);
	}

	public V getValue() {
		return getChild(ChildIndices.VALUE);
	}

	private void setValue(V value) {
		setChild(ChildIndices.VALUE, value);
	}

	public FCMLeaf(final K key, final V value) {
		super();
		setKey(key);
		setValue(value);
	}

	public FCMLeaf() {
		super();
	}

	@SuppressWarnings("unchecked")
	private FCMLeaf(final FCMLeaf<K, V> otherLeaf) {
		super(otherLeaf.getParent());
		setKey((K) otherLeaf.getKey().copy());
		setValue((V) otherLeaf.getValue().copy());
		setImmutable(false);
		otherLeaf.setImmutable(true);
	}

	/**
	 *
	 * @return
	 */
	protected V getMutableValue() {
		return (V) this.getValue().copy();
	}

	public V getValueForModify() {
		if (this.isPathUnique()) {
			return this.getValue();
		}

		return this.getMutableValue();
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
	public void copyFrom(final SerializableDataInputStream inStream) {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFromExtra(final SerializableDataInputStream inStream) {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@SuppressWarnings("unchecked")
	public void deserializeThroughProviders(final SerializedObjectProvider keyProvider,
			final SerializedObjectProvider valueProvider,
			final SerializableDataInputStream inStream) throws IOException {
		this.setKey((K) keyProvider.deserialize(inStream));
		this.setValue((V) valueProvider.deserialize(inStream));
	}

	@SuppressWarnings("unchecked")
	public static <K extends FCMKey, V extends FCMValue> FCMLeaf<K, V> deserialize(
			final SerializedObjectProvider keyProvider,
			final SerializedObjectProvider valueProvider,
			final SerializableDataInputStream inStream) throws IOException {
		final K key = (K) keyProvider.deserialize(inStream);
		final V value = (V) valueProvider.deserialize(inStream);
		return new FCMLeaf<>(key, value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}
