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

package com.swirlds.common.merkle.iterators;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterate over a merkle tree.
 *
 * @param <T> The type of node over which this iterator walks. Usually MerkleNode is the correct choice for this
 *           unless there is an implementation specific method required by one of the overridable methods in this
 *           iterator.
 * @param <R> The type of the node returned by this iterator. Nodes of type T are cast into type R before being
 *           returned by next().
 */
public abstract class MerkleIterator<T extends MerkleNode, R extends T> implements Iterator<R> {

	/**
	 * The next node to be returned by this iterator
	 */
	protected T next;

	/**
	 * True if the value contained should be returned by the iterator
	 */
	protected boolean hasNext;

	public MerkleIterator(final T root) {
		init();
		if (root != null) {
			pushNode(root);
		}
	}

	/**
	 * Initialize any data structures that are required. This is called in MerkleIterator's constructor.
	 */
	protected abstract void init();

	/**
	 * Push a node into the stack/queue.
	 */
	protected abstract void push(T node);

	/**
	 * Remove and return the next item in the stack/queue.
	 */
	protected abstract T pop();

	/**
	 * Return the next item in the stack/queue but do not remove it.
	 */
	protected abstract T peek();

	/**
	 * Get the number of elements in the stack/queue.
	 */
	protected abstract int size();

	/**
	 * When adding children, should the order the children are pushed be reversed?
	 * Should be true for depth first searches (since stacks are first in last out)
	 * and false for breadth first searches (since queues are first in first out).
	 */
	protected abstract boolean reverseChildren();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {
		findNext();
		return hasNext;
	}

	/**
	 * An overridable filter that is applied to each internal node. If false then neither this node nor its descendants
	 * will be visited by this iterator.
	 */
	protected boolean shouldNodeBeVisited(@SuppressWarnings("unused") final T node) {
		return true;
	}

	/**
	 * An overridable filter that is applied to each node. If false then this node will not be returned by the iterator.
	 * Does not stop the iterator from visiting and possibly returning descendant nodes.
	 */
	protected boolean shouldNodeBeReturned(final T node) {
		return true;
	}

	/**
	 * Called when a parent is fetching its children. If false is returned then the
	 * parent will act as if that child does not exist. This will cause the child to
	 * not be iterated over and not returned.
	 *
	 * Useful if T is a subtype of MerkleNode but there are parts of the tree that do not implement T. This
	 * allows for the iterator to ignore the parts of the tree that it should not (or can not) handle.
	 *
	 * @param parent Parent node
	 * @param child Child node
	 * @return Whether the child node should be considered or not
	 */
	protected boolean shouldChildBeConsidered(final T parent, final MerkleNode child) {
		return true;
	}

	/**
	 * Check if a node is ready to be visited by the iterator. A node that is ready to be visited is ready to
	 * be pushed onto the stack/queue.
	 */
	protected boolean isNodeReadyToBeVisited(final T node) {
		if (node == null || node.isLeaf()) {
			return shouldNodeBeReturned(node);
		} else {
			return shouldNodeBeVisited(node);
		}
	}

	/**
	 * Check if this node has children that need to be added to the stack/queue.
	 */
	protected boolean hasChildrenToHandle(MerkleNode node) {
		if (node != null && !node.isLeaf()) {
			// Node is an InternalNode and may have children
			// If a node has children that need to be handled it will have been added to the stack twice in a row
			return size() > 0 && peek() == node;
		}
		return false;
	}

	/**
	 * Add all of a node's children to the stack that need to be added.
	 */
	@SuppressWarnings("unchecked")
	protected void addChildren(final T node) {
		final MerkleInternal internalNode = (MerkleInternal) node;
		boolean reverseChildren = reverseChildren();

		// If needed, iterate through the children from last to first
		int startIndex = reverseChildren ? internalNode.getNumberOfChildren() - 1 : 0;
		int endIndex = reverseChildren ? -1 : internalNode.getNumberOfChildren();
		int delta = reverseChildren ? -1 : 1;

		for (int childIndex = startIndex; childIndex != endIndex; childIndex += delta) {
			final MerkleNode child = internalNode.getChild(childIndex);
			if (shouldChildBeConsidered(node, child)) {
				pushNode((T) child);
			}
		}
	}

	/**
	 * Push a node onto the stack.
	 */
	protected void pushNode(final T node) {
		if (isNodeReadyToBeVisited(node)) {
			push(node);
			if (node != null && !node.isLeaf()) {
				// Internal nodes are pushed twice
				// This sends a signal to handle that node's children when the time comes
				push(node);
			}
		}
	}

	/**
	 * Iterate over the merkle tree until the next MerkleNode to be returned is found.
	 */
	protected void findNext() {
		if (hasNext) {
			return;
		}

		while (size() > 0) {
			final T target = pop();

			if (hasChildrenToHandle(target)) {
				addChildren(target);
			} else if (shouldNodeBeReturned(target)) {
				next = target;
				hasNext = true;
				return;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public R next() {
		findNext();
		if (!hasNext) {
			throw new NoSuchElementException();
		}

		hasNext = false;
		return (R) next;
	}

}
