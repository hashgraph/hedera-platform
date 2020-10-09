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

package com.swirlds.common.crypto;

import com.swirlds.common.constructable.ConstructableIgnored;

import java.util.Arrays;

@ConstructableIgnored
public class ImmutableHash extends Hash {

	/**
	 * {@inheritDoc}
	 */
	public ImmutableHash() {
	}

	/**
	 * {@inheritDoc}
	 */
	public ImmutableHash(final byte[] value) {
		super(value, DigestType.SHA_384, true, true);
	}

	/**
	 * {@inheritDoc}
	 */
	public ImmutableHash(final byte[] value, final DigestType digestType) {
		super(value, digestType, true, true);
	}

	/**
	 * {@inheritDoc}
	 */
	public ImmutableHash(final Hash mutable) {
		super(mutable);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] getValue() {
		final byte[] value = super.getValue();
		return Arrays.copyOf(value, value.length);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setValue(final byte[] value) {
		throw new UnsupportedOperationException();
	}
}
