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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A list embedded within a merkle leaf.
 *
 * @param <T>
 * 		The type contained by the list.
 */
public abstract class AbstractListLeaf<T extends FastCopyable<T> & SelfSerializable> extends AbstractMerkleLeaf
		implements MerkleLeaf, List<T> {

	/**
	 * Version information for AbstractEmbeddedListLeaf.
	 */
	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * Create a new AbstractEmbeddedListLeaf.
	 */
	public AbstractListLeaf() {
		elements = new ArrayList<>();
	}

	/**
	 * Initialize this list from another list. Deep copies the given list.
	 *
	 * @param elements
	 * 		The list of elements to copy.
	 */
	public AbstractListLeaf(List<T> elements) {
		if (elements.size() > getMaxSize()) {
			throw new IllegalArgumentException("Provided list exceeds maximum size");
		}
		this.elements = new ArrayList<>(elements.size());
		for (int index = 0; index < elements.size(); index++) {
			this.elements.add(elements.get(index).copy());
		}
	}

	/**
	 * Create a new AbstractEmbeddedListLeaf with an initial size.
	 *
	 * @param initialSize
	 * 		The initial size of the list.
	 */
	public AbstractListLeaf(int initialSize) {
		elements = new ArrayList<>(initialSize);
	}

	/**
	 * The maximum size that the list is allowed to be (for deserialization safety).
	 */
	protected int getMaxSize() {
		return Integer.MAX_VALUE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializableList(elements, true, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		elements = in.readSerializableList(getMaxSize());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	protected List<T> elements;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return elements.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean contains(Object o) {
		return elements.contains(o);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<T> iterator() {
		return elements.iterator();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] toArray() {
		return elements.toArray();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T1> T1[] toArray(T1[] a) {
		return elements.toArray(a);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean add(T t) {
		return elements.add(t);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove(Object o) {
		return elements.remove(o);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean containsAll(Collection<?> c) {
		return elements.containsAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addAll(Collection<? extends T> c) {
		return elements.addAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addAll(int index, Collection<? extends T> c) {
		return elements.addAll(index, c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean removeAll(Collection<?> c) {
		return elements.removeAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean retainAll(Collection<?> c) {
		return elements.retainAll(c);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		elements.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T get(int index) {
		return elements.get(index);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T set(int index, T element) {
		return elements.set(index, element);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void add(int index, T element) {
		elements.add(index, element);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T remove(int index) {
		return elements.remove(index);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int indexOf(Object o) {
		return elements.indexOf(o);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int lastIndexOf(Object o) {
		return elements.lastIndexOf(o);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ListIterator<T> listIterator() {
		return elements.listIterator();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ListIterator<T> listIterator(int index) {
		return elements.listIterator(index);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<T> subList(int fromIndex, int toIndex) {
		return elements.subList(fromIndex, toIndex);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("List size: ").append(elements.size());
		sb.append(" [");
		for (int index = 0; index < elements.size(); index++) {
			T element = elements.get(index);
			sb.append((element == null) ? "null" : element.toString());
			if (index + 1 < elements.size()) {
				sb.append(", ");
			}
		}
		sb.append("]");

		return sb.toString();
	}
}
