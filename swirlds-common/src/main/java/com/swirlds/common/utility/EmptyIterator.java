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

package com.swirlds.common.utility;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator that doesn't return anything. A convenient utility object.
 *
 * @param <T>
 * 		the type "returned" by the iterator
 */
public class EmptyIterator<T> implements Iterator<T> {

	public EmptyIterator() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T next() {
		throw new NoSuchElementException();
	}
}
