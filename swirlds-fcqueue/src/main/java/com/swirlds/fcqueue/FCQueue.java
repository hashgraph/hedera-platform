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

package com.swirlds.fcqueue;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.list.ListDigestException;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.fcqueue.internal.FCQHashAlgorithm;
import com.swirlds.fcqueue.internal.FCQueueNode;
import com.swirlds.fcqueue.internal.FCQueueNodeBackwardIterator;
import com.swirlds.fcqueue.internal.FCQueueNodeIterator;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.io.DataStreamUtils.readValidInt;

/**
 * A threadsafe fast-copyable queue, each of whose elements is fast-copyable. Elements must always be inserted at the
 * tail and removed from the head. It is not allowed to insert nulls. This is fast copyable. A fast copy of a queue is
 * mutable and the original queue becomes immutable. A mutable fast copy can only be created from a mutable queue,
 * which would then become immutable after creating this mutable fast copy, or by using the "new" operator.
 *
 * Element insertion/deletion and fast copy creation/deletion all take constant time. Except that if a queue has n
 * elements that are not in any other queue in its queue group, then deleting it takes O(n) time.
 *
 * The FCQueue objects can be thought of as being organized into "queue groups". A fast copy of a queue creates another
 * queue in the same queue group. But instantiating a queue with "new" and the constructor creates a new queue group.
 *
 * All write operations are synchronized with the current instance. So it is possible to write to two different queue
 * groups at the same time. It is ok for multiple iterators to be running in multiple threads at the same time within
 * any thread group. An iterator for a queue will throw an exception if it is used after a write to that queue,
 * but it is unaffected by writes to other queues in that queue group.
 */
public class FCQueue<E extends FCQueueElement> extends AbstractMerkleLeaf implements Queue<E> {

	private static class ClassVersion {
		/**
		 * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
		 * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
		 * specially by the platform.
		 */
		public static final int ORIGINAL = 1;
		/**
		 * FCQ implements MerkleLeaf, element implements FCQueueElement
		 */
		public static final int MIGRATE_TO_SERIALIZABLE = 2;
	}

	/** Object identifier of this class (random int). Do NOT change when the class changes its code/name/version. */
	public static final long CLASS_ID = 139236190103L;

	/** Maximum number of elements FCQueue supports */
	public static final int MAX_ELEMENTS = 100_000_000;

	/**
	 * Calculate hash as: sum hash, rolling hash, Merkle hash.
	 * rolling hash is recommended for now (unless Merkle is tried and found fast enough)
	 */
	protected static final FCQHashAlgorithm HASH_ALGORITHM = FCQHashAlgorithm.ROLLING_HASH;

	/** The digest type used by FCQ */
	private static final DigestType digestType = DigestType.SHA_384;

	/** The default null hash, all zeros */
	public static final byte[] NULL_HASH = new byte[digestType.digestLength()];

	/**
	 * When deserializing, a hash is read and another hash is calculated. If set to true, it will throw an exception
	 * if these two do not match.
	 */
	private static final boolean THROW_ON_HASH_MISMATCH = false;

	/** log all problems here, not to the console or elsewhere */
	private static final Logger log = LogManager.getLogger(FCQueue.class);

	/** should markers be sent during serialization to aid debugging? */
	private static final boolean USE_MARKERS = false;

	/** serialized at the start of this queue, for detecting bugs */
	private static final int BEGIN_QUEUE_MARKER = 175624369;

	/** serialized at the end of this queue, for detecting bugs */
	private static final int END_QUEUE_MARKER = 175654143;

	/** the multiplicative inverse of 3 modulo 2 to the 64, in hex, is 15 "a" digits then a "b" digit */
	protected static final long INVERSE_3 = 0xaaaaaaaaaaaaaaabL;

	/** number of elements hashed **/
	private int runningHashSize;

	/** the number of elements in this queue */
	protected int size;

	/** the head of this queue */
	protected FCQueueNode<E> head;

	/** the tail of this queue */
	protected FCQueueNode<E> tail;

	/** the hash of set of elements in the queue. */
	protected final byte[] hash = new byte[digestType.digestLength()];

	/**
	 * The number of times this queue has changed so far, such as by add/remove/clear. This could be made volatile to
	 * catch more bugs, but then operations would be slower.
	 */
	protected int numChanges;

	/**
	 * Instantiates a new empty queue which doesn't require deserialization
	 */
	public FCQueue() {
		size = 0;
		head = null;
		tail = null;
		//the first in a queue group is mutable until copy(true) is called on it
		setImmutable(false);
	}

	/** Instantiate a queue with all the given parameters. This is just a helper function, not visible to users. */
	protected FCQueue(final FCQueue<E> fcQueue) {
		super(fcQueue);
		this.size = fcQueue.size;
		System.arraycopy(fcQueue.hash, 0, this.hash, 0, this.hash.length);
		this.head = fcQueue.head;
		this.tail = fcQueue.tail;
		this.runningHashSize = fcQueue.runningHashSize;
		this.setImmutable(false);
	}

	/** @return the number of times this queue has changed since it was instantiated by {@code new} or {@code copy} */
	public int getNumChanges() {
		return numChanges;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash getHash() {
		final StopWatch watch = new StopWatch();
		watch.start();


		final byte[] localHash;
		final Iterator<FCQueueNode<E>> it;
		final int currentSize;
		final int currentRunningHashSize;

		synchronized (this) {
			if (head == null) {
				return new ImmutableHash(getNullHash());
			}

			if (size == runningHashSize) {
				return new ImmutableHash(hash);
			}

			currentSize = size;
			it = nodeBackwardIterator();
			localHash = Arrays.copyOf(hash, hash.length);
			currentRunningHashSize = runningHashSize;
		}

		final int limit = currentSize - currentRunningHashSize;
		FCQHashAlgorithm.increaseRollingBase(limit, localHash);
		int index = 0;
		while (index < limit) {
			final FCQueueNode<E> node = it.next();
			final byte[] elementHash;

			if (node.getElementHashOfHash() == null) {
				elementHash = getHash(node.getElement());
				node.setElementHashOfHash(elementHash);
			} else {
				elementHash = node.getElementHashOfHash();
			}

			HASH_ALGORITHM.computeHash(localHash, elementHash, index);
			index++;
		}

		synchronized (this) {
			runningHashSize = currentSize;
			System.arraycopy(localHash, 0, hash, 0, hash.length);
		}

		watch.stop();
		synchronized (this) {
			// we need to guard this line with lock
			// because FCQueue#getHash might be called by multiple threads, and StatsBuffer is not thread-safe
			FCQueueStatistics.fcqHashExecutionMicros.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
		}

		return new ImmutableHash(hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setHash(final Hash hash) {
		throw new UnsupportedOperationException("FCQueue computes its own hash");
	}

	@Override
	public boolean isSelfHashing() {
		return true;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following implement Queue<E>
	//////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Inserts the specified element into this queue if it is possible to do so
	 * immediately without violating capacity restrictions, returning
	 * {@code true} upon success and throwing an {@code IllegalStateException}
	 * if no space is currently available.
	 *
	 * @param o
	 * 		the element to add
	 * @return {@code true} (as specified by {@link Collection#add})
	 * @throws IllegalStateException
	 * 		if the element cannot be added at this
	 * 		time due to capacity restrictions (this cannot happen)
	 * @throws ClassCastException
	 * 		if the class of the specified element
	 * 		prevents it from being added to this queue
	 * @throws NullPointerException
	 * 		if the specified element is null and
	 * 		this queue does not permit null elements
	 * @throws IllegalArgumentException
	 * 		if some property of this element prevents it from being added to this queue.
	 * 		This will happen if the fast-copyable object o
	 * 		has an IOException while serializing to create its hash.
	 */
	@Override
	public synchronized boolean add(final E o) {
		StopWatch watch = null;

		if (FCQueueStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		if (isImmutable()) {
			throw new IllegalStateException("tried to modify an immutable FCQueue");
		}

		if (o == null) {
			throw new NullPointerException("tried to add a null element into an FCQueue");
		}

		if (this.size() >= MAX_ELEMENTS) {
			throw new IllegalStateException(
					String.format(
							"tried to add an element to an FCQueue whose size has reached MAX_ELEMENTS: %d",
							MAX_ELEMENTS));
		}

		final FCQueueNode<E> node;

		if (tail == null) { //current list is empty
			node = new FCQueueNode<>(o);
			head = node;
		} else { //current list is nonempty, so add to the tail
			node = this.tail.insertAtTail(o);
			node.setTowardHead(tail);
			node.setTowardTail(null);
			tail.setTowardTail(node);
			tail.decRefCount();
		}
		tail = node;

		size++;
		numChanges++;

		if (watch != null) {
			watch.stop();
			FCQueueStatistics.fcqAddExecutionMicros.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
		}

		return true;
	}

	/**
	 * Retrieves and removes the head of this queue.  This method differs
	 * from {@link #poll() poll()} only in that it throws an exception if
	 * this queue is empty.
	 *
	 * @return the head of this queue
	 * @throws NoSuchElementException
	 * 		if this queue is empty
	 */
	@Override
	public synchronized E remove() {
		StopWatch watch = null;

		if (FCQueueStatistics.isRegistered()) {
			watch = new StopWatch();
			watch.start();
		}

		final E element;
		final byte[] elementHash;
		final FCQueueNode<E> oldHead;

		oldHead = head;

		if (isImmutable()) {
			throw new IllegalArgumentException("tried to remove from an immutable FCQueue");
		}

		if (size == 0 || head == null) {
			throw new NoSuchElementException("tried to remove from an empty FCQueue");
		}

		// Retrieve the element and change the head pointer
		elementHash = head.getElementHashOfHash();

		element = head.getElement();
		head = head.getTowardTail();

		if (head == null) { //if just removed the last one, then tail should be null, too
			tail.decRefCount();
			tail = null;
		} else {
			head.incRefCount();
		}

		oldHead.decRefCount(); //this will garbage collect the old head, if no copies point to it
		size--;
		numChanges++;

		if (elementHash != null && runningHashSize > 0) {
			runningHashSize--;
			HASH_ALGORITHM.computeRemoveHash(hash, elementHash, runningHashSize);
		}

		if (watch != null) {
			watch.stop();
			FCQueueStatistics.fcqRemoveExecutionMicros.recordValue(watch.getTime(TimeUnit.MICROSECONDS));
		}

		return element;
	}

	/**
	 * Inserts the specified element into this queue. This is equivalent to {@code add(o)}.
	 *
	 * @param o
	 * 		the element to add
	 * @return {@code true} if the element was added to this queue, else
	 *        {@code false}
	 * @throws ClassCastException
	 * 		if the class of the specified element
	 * 		prevents it from being added to this queue
	 * @throws NullPointerException
	 * 		if the specified element is null and
	 * 		this queue does not permit null elements
	 * @throws IllegalArgumentException
	 * 		if some property of this element
	 * 		prevents it from being added to this queue
	 */
	@Override
	public synchronized boolean offer(final E o) {
		return add(o);
	}

	/**
	 * Retrieves and removes the head of this queue,
	 * or returns {@code null} if this queue is empty.
	 *
	 * @return the head of this queue, or {@code null} if this queue is empty
	 */
	@Override
	public synchronized E poll() {
		if (this.head == null) {
			return null;
		}

		return remove();
	}

	/**
	 * Retrieves, but does not remove, the head of this queue.  This method
	 * differs from {@link #peek peek} only in that it throws an exception
	 * if this queue is empty.
	 *
	 * @return the head of this queue
	 * @throws NoSuchElementException
	 * 		if this queue is empty
	 */
	@Override
	public synchronized E element() {
		if (this.head == null) {
			throw new NoSuchElementException("tried to get the head of an empty FCQueue");
		}

		return head.getElement();
	}

	/**
	 * Retrieves, but does not remove, the head of this queue,
	 * or returns {@code null} if this queue is empty.
	 *
	 * @return the head of this queue, or {@code null} if this queue is empty
	 */
	@Override
	public synchronized E peek() {
		if (this.head == null) {
			return null;
		}

		return head.getElement();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following implement FastCopyable
	//////////////////////////////////////////////////////////////////////////////////////////////////

	/** {@inheritDoc} */
	@Override
	public synchronized FCQueue<E> copy() {
		if (isImmutable()) {
			throw new IllegalStateException("Tried to make a copy of an immutable FCQueue");
		}

		final FCQueue<E> queue = new FCQueue<>(this);

		//there can be only one mutable per queue group. If the copy is, then this isn't.
		setImmutable(true);

		if (head != null) {
			head.incRefCount();
		}

		if (tail != null) {
			tail.incRefCount();
		}

		return queue;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following implement AbstractMerkleNode
	//////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	protected synchronized void onRelease() {
		clearInternal();
		super.onRelease();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following implement Java.util.Collection
	//////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Returns the number of elements in this collection.
	 *
	 * @return the number of elements in this collection
	 */
	@Override
	public synchronized int size() {
		return size;
	}

	/**
	 * Returns {@code true} if this collection contains no elements.
	 *
	 * @return {@code true} if this collection contains no elements
	 */
	@Override
	public synchronized boolean isEmpty() {
		return size == 0;
	}

	/**
	 * Returns {@code true} if this collection contains the specified element.
	 * More formally, returns {@code true} if and only if this collection
	 * contains at least one element {@code e} such that
	 * {@code Objects.equals(o, e)}.
	 *
	 * @param o
	 * 		element whose presence in this collection is to be tested
	 * @return {@code true} if this collection contains the specified
	 * 		element
	 * @throws ClassCastException
	 * 		if the type of the specified element
	 * 		is incompatible with this collection
	 * 		(<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
	 * @throws NullPointerException
	 * 		if the specified element is null and this
	 * 		collection does not permit null elements
	 * 		(<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
	 */
	@Override
	public synchronized boolean contains(final Object o) {
		for (final E e : this) {

			if (Objects.equals(o, e)) {
				return true;
			}

		}

		return false;
	}

	/**
	 * Returns an iterator over the elements in this queue, in insertion order (head first, tail last).
	 *
	 * @return an {@code Iterator} over the elements in this collection
	 */
	@Override
	public synchronized Iterator<E> iterator() {
		return new FCQueueIterator<>(this, head, tail);
	}

	/**
	 * Returns an iterator over the internal nodes in this queue, in insertion order (head first, tail last).
	 *
	 * @return an {@code Iterator} over the elements in this collection
	 */
	protected synchronized Iterator<FCQueueNode<E>> nodeIterator() {
		return new FCQueueNodeIterator<>(this, head, tail);
	}

	/**
	 * Returns an iterator over the internal nodes in this queue, in reverse-insertion order (tail first, head last).
	 *
	 * @return an {@code Iterator} over the elements in this collection
	 */
	protected synchronized Iterator<FCQueueNode<E>> nodeBackwardIterator() {
		return new FCQueueNodeBackwardIterator<>(this, head, tail);
	}

	/**
	 * Returns an array containing all of the elements in this collection.
	 * If this collection makes any guarantees as to what order its elements
	 * are returned by its iterator, this method must return the elements in
	 * the same order. The returned array's {@linkplain Class#getComponentType
	 * runtime component type} is {@code Object}.
	 *
	 * <p>The returned array will be "safe" in that no references to it are
	 * maintained by this collection.  (In other words, this method must
	 * allocate a new array even if this collection is backed by an array).
	 * The caller is thus free to modify the returned array.
	 *
	 * <p>This method acts as bridge between array-based and collection-based
	 * APIs.
	 *
	 * @return an array, whose {@linkplain Class#getComponentType runtime component
	 * 		type} is {@code Object}, containing all of the elements in this collection
	 */
	@Override
	public synchronized Object[] toArray() {
		final int size = size();
		final Object[] result = new Object[size];
		int i = 0;

		for (final E e : this) {
			result[i++] = e;
		}

		return result;
	}

	/**
	 * Returns an array containing all of the elements in this collection;
	 * the runtime type of the returned array is that of the specified array.
	 * If the collection fits in the specified array, it is returned therein.
	 * Otherwise, a new array is allocated with the runtime type of the
	 * specified array and the size of this collection.
	 *
	 * <p>If this collection fits in the specified array with room to spare
	 * (i.e., the array has more elements than this collection), the element
	 * in the array immediately following the end of the collection is set to
	 * {@code null}.  (This is useful in determining the length of this
	 * collection <i>only</i> if the caller knows that this collection does
	 * not contain any {@code null} elements.)
	 *
	 * <p>If this collection makes any guarantees as to what order its elements
	 * are returned by its iterator, this method must return the elements in
	 * the same order.
	 *
	 * <p>Like the {@link #toArray()} method, this method acts as bridge between
	 * array-based and collection-based APIs.  Further, this method allows
	 * precise control over the runtime type of the output array, and may,
	 * under certain circumstances, be used to save allocation costs.
	 *
	 * <p>Suppose {@code x} is a collection known to contain only strings.
	 * The following code can be used to dump the collection into a newly
	 * allocated array of {@code String}:
	 *
	 * <pre>
	 *     String[] y = x.toArray(new String[0]);</pre>
	 *
	 * Note that {@code toArray(new Object[0])} is identical in function to
	 * {@code toArray()}.
	 *
	 * @param a
	 * 		the array into which the elements of this collection are to be
	 * 		stored, if it is big enough; otherwise, a new array of the same
	 * 		runtime type is allocated for this purpose.
	 * @return an array containing all of the elements in this collection
	 * @throws ArrayStoreException
	 * 		if the runtime type of any element in this
	 * 		collection is not assignable to the {@linkplain Class#getComponentType
	 * 		runtime component type} of the specified array
	 * @throws NullPointerException
	 * 		if the specified array is null
	 */
	@Override
	@SuppressWarnings("unchecked")
	public synchronized <T> T[] toArray(T[] a) {
		int size = size();
		int i = 0;

		if (a.length < size) {
			// If array is too small, allocate the new one with the same component type
			a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
		} else if (a.length > size) {
			// If array is too large, set the first unassigned element to null
			a[size] = null;
		}

		for (final E e : this) {
			// No need for checked cast - ArrayStoreException will be thrown
			// if types are incompatible, just as required
			a[i++] = (T) e;
		}

		return a;
	}

	/**
	 * This operation is not supported, and will throw an exception. The FCQueue is fast to copy because it ensures that
	 * no element will ever be removed from the middle of the queue.
	 *
	 * @throws UnsupportedOperationException
	 * 		always thrown because the {@code remove} operation
	 * 		is not supported by this collection
	 */
	@Override
	public boolean remove(final Object o) {
		throw new UnsupportedOperationException("FCQueue allows removal only from the head, not arbitrary removals");
	}

	/**
	 * Returns {@code true} if this collection contains all of the elements
	 * in the specified collection.
	 *
	 * @param c
	 * 		collection to be checked for containment in this collection
	 * @return {@code true} if this collection contains all of the elements
	 * 		in the specified collection
	 * @throws ClassCastException
	 * 		if the types of one or more elements
	 * 		in the specified collection are incompatible with this
	 * 		collection
	 * 		(<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
	 * @throws NullPointerException
	 * 		if the specified collection contains one
	 * 		or more null elements and this collection does not permit null
	 * 		elements
	 * 		(<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>),
	 * 		or if the specified collection is null.
	 * @see #contains(Object)
	 */
	@Override
	public synchronized boolean containsAll(final Collection<?> c) {
		//This could be made faster by sorting both lists (if c is larger than log of size()).
		//But we'll do brute force for now (which is better for small c).
		for (final Object e : c) {
			if (!contains(e)) {
				return false;
			}
		}

		return true;
	}

	/** {@inheritDoc} */
	@Override
	public synchronized boolean addAll(final Collection<? extends E> c) {
		for (final E e : c) {
			add(e);
		}

		return false;
	}


	/**
	 * This operation is not supported, and will throw an exception. The FCQueue is fast to copy because it ensures that
	 * no element will ever be removed from the middle of the queue.
	 *
	 * @param c
	 * 		collection containing elements to be removed from this collection
	 * @throws UnsupportedOperationException
	 * 		always thrown because the {@code removeAll} operation
	 * 		is not supported by this collection
	 */
	@Override
	public boolean removeAll(final Collection<?> c) {
		throw new UnsupportedOperationException("FCQueue can only remove from the head");
	}


	/**
	 * This operation is not supported, and will throw an exception. The FCQueue is fast to copy because it ensures that
	 * no element will ever be removed from the middle of the queue.
	 *
	 * @param c
	 * 		collection containing elements to be retained in this collection
	 * @throws UnsupportedOperationException
	 * 		always thrown because the {@code retainAll} operation
	 * 		is not supported by this collection
	 */
	@Override
	public boolean retainAll(final Collection<?> c) {
		throw new UnsupportedOperationException("FCQueue can only remove from the head");
	}

	/**
	 * Removes all of the elements from this queue.
	 * The queue will be empty and the hash reset to the null value after this method returns.
	 * This does not delete the FCQueue object. It just empties the queue.
	 */
	@Override
	public synchronized void clear() {
		throwIfImmutable();
		numChanges++;

		clearInternal();
	}

	private void clearInternal() {
		if (head != null) {
			head.decRefCount();
			head = null;
		}

		if (tail != null) {
			tail.decRefCount();
			tail = null;
		}

		size = 0;
		runningHashSize = 0;
		resetHash();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following FastCopyable methods have default implementations, but are overridden here
	//////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final FCQueue<?> fcQueue = (FCQueue<?>) o;

		return size == fcQueue.size &&
				Arrays.equals(hash, fcQueue.hash);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(size);
		result = 31 * result + Arrays.hashCode(hash);
		return result;
	}

	/**
	 * Serializes the current object to an array of bytes in a deterministic manner.
	 *
	 * @param dos
	 * 		the {@link java.io.DataOutputStream} to which the object's binary form should be written
	 * @throws IOException
	 * 		if there are problems during serialization
	 */
	@Override
	public synchronized void serialize(final SerializableDataOutputStream dos) throws IOException {
		if (USE_MARKERS) {
			dos.writeInt(BEGIN_QUEUE_MARKER);
		}
		dos.writeInt(this.size());
		dos.write(this.hash);
		dos.writeSerializableIterableWithSize(this.iterator(), this.size(), true, false);

		if (USE_MARKERS) {
			dos.writeInt(END_QUEUE_MARKER);
		}
	}

	@Override
	public synchronized void deserialize(SerializableDataInputStream dis, int version) throws IOException {
		numChanges++;
		if (USE_MARKERS) {
			readValidInt(dis, "BEGIN_QUEUE_MARKER", BEGIN_QUEUE_MARKER);
		}
		final byte[] recoveredHash = new byte[hash.length];


		final int listSize = dis.readInt();
		dis.readFully(recoveredHash);

		dis.readSerializableIterableWithSize(MAX_ELEMENTS, this::add);

		if (USE_MARKERS) {
			readValidInt(dis, "END_QUEUE_MARKER", END_QUEUE_MARKER);
		}

		if (THROW_ON_HASH_MISMATCH &&
				recoveredHash != null && recoveredHash.length > 0) {
			if (!Arrays.equals(hash, recoveredHash)) {
				throw new ListDigestException(String.format(
						"FCQueue: Invalid list signature detected during deserialization (Actual: %s, Expected: " +
								"%s for list of size %d)",
						hex(hash), hex(recoveredHash), listSize));
			}
		}
	}

	/**
	 * Find the hash of a FastCopyable object.
	 *
	 * @param element
	 * 		an element contained by this collection that is being added, deleted, or replaced
	 * @return the 48-byte hash of the element (getNullHash() if element is null)
	 */
	protected byte[] getHash(final E element) {
		// Handle cases where list methods return null if the list is empty
		if (element == null) {
			return getNullHash();
		}
		Cryptography crypto = CryptoFactory.getInstance();
		//return a hash of a hash, in order to make state proofs smaller in the future
		crypto.digestSync(element);
		return crypto.digestSync(element.getHash()).getValue();
	}

	protected static byte[] getNullHash() {
		return Arrays.copyOf(NULL_HASH, NULL_HASH.length);
	}

	private synchronized void resetHash() {
		System.arraycopy(NULL_HASH, 0, this.hash, 0, this.hash.length);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return ClassVersion.MIGRATE_TO_SERIALIZABLE;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return ClassVersion.MIGRATE_TO_SERIALIZABLE;
	}
}
