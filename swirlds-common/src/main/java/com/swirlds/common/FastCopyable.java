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
package com.swirlds.common;

import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SerializableDataInputStream;

import java.io.IOException;

/**
 * An interface for classes that can be copied and serialized in a way specific to the Swirlds platform. If
 * a class implements the FastCopyable interface, then it should use a copy-on-write strategy so that calls
 * to {@link #copy} make virtual copies almost instantaneously. See the documentation for these methods for
 * the details on what they should do, and how they differ from the usual Java <code>copy</code> and
 * <code>serialize</code> methods.
 */
public interface FastCopyable<T extends FastCopyable<T>> extends Releasable {

	/**
	 * If a class ExampleClass implements the FastCopyable interface, and an object x is of class
	 * ExampleClass, then x.copy() instantiates and returns a new ExampleClass object containing the same
	 * data as x. This should be a deep clone, not shallow. So if x contains references to other objects, it
	 * should do something like copy() on them, too, rather than just copying the reference.
	 * <p>
	 * Furthermore, the copy operation should be fast, at the possible cost of making reads and writes
	 * slower (such as by using some kind of copy-on-write mechanism).
	 * <strong>This method causes the object to become immutable and returns a mutable copy.</strong>
	 * If the object is already immutable:
	 * <ol>
	 *     <li>it can throw an IllegalStateException</li>
	 *     <li>or it can implement a slow, deep copy that returns a mutable object </li>
	 * </ol>
	 *
	 * Either behavior is fine, but each implementation should document which behavior it has chosen.
	 * By the default, the first implementation is assumed.
	 *
	 * @return the new copy that was made
	 */
	 T copy();

	/**
	 * Make this object be an exact copy of the object that was serialized to the given stream. It should
	 * overwrite all the existing state within this object, and replace it.
	 * <p>
	 * It can be assumed that the stream was originally written by the copyTo method. There may be bytes in
	 * the stream after the ones to read here, so it is important to know when this has finished reading its
	 * state (e.g., by making copyTo write the length of an array before writing all its elements).
	 *
	 * @param inStream
	 * 		the stream to read from
	 * @throws IOException
	 * 		can be thrown by the DataInputStream while reading from it
	 * @deprecated Implement {@link SerializableHashable} instead
	 */
	@Deprecated
	default void copyFrom(SerializableDataInputStream inStream) throws IOException {
	}

	/**
	 * Reads all the data written in the (removed) copyToExtra method.
	 *
	 * @param inStream
	 * 		the stream to read from
	 * @throws IOException
	 * 		can be thrown by the DataInputStream while reading from it
	 */
	@Deprecated
	default void copyFromExtra(SerializableDataInputStream inStream) throws IOException {
	}

	/**
	 * Determines if an object/copy is immutable or not.
	 * Only the most recent copy must be mutable
	 *
	 * @return Whether is immutable or not
	 */
	default boolean isImmutable() {
		return true;
	}

	/**
	 * Throws an exception if {@link #isImmutable()}} returns {@code true}
	 */
	default void throwIfImmutable() {
		if (this.isImmutable()) {
			throw new IllegalStateException("This operation is not permitted on an immutable object.");
		}
	}
}
