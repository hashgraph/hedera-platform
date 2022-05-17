/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.merkle.map;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.Keyed;

import java.util.Objects;

/**
 * The merkle node that the old FCMap used to use to store a key and a value.
 *
 * @param <L>
 * 		a merkle node key
 * @param <R>
 * 		a merkle node value
 * @deprecated this class is very memory inefficient to store as a value in a {@link MerkleMap}.
 * 		This class will be removed in a future release.
 */
@Deprecated(forRemoval = true)
public class MerklePair<L extends MerkleNode, R extends MerkleNode>
		extends AbstractBinaryMerkleInternal
		implements Keyed<L> {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0xc1bd3ae28094acdeL;

	/**
	 * Create a new merkle pair.
	 *
	 * @param key
	 * 		the key
	 * @param value
	 * 		the value
	 */
	public MerklePair(final L key, final R value) {
		super();
		setLeft(key);
		setRight(value);
	}

	/**
	 * Create a new merkle pair.
	 */
	public MerklePair() {
		super();
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the node to copy
	 */
	protected MerklePair(final MerklePair<L, R> that) {
		super(that);
		that.setImmutable(true);

		if (that.getLeft() != null) {
			setLeft(that.getLeft().copy().cast());
		}
		if (that.getRight() != null) {
			setRight(that.getRight().copy().cast());
		}
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
		return Objects.equals(getLeft(), otherLeaf.getLeft()) && Objects.equals(getRight(), otherLeaf.getRight());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(getLeft(), getRight());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerklePair<L, R> copy() {
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
		return MerklePair.ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public L getKey() {
		return getLeft();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setKey(final L key) {
		setLeft(key);
	}


}
