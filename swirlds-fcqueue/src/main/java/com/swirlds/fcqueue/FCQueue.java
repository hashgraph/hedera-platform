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

package com.swirlds.fcqueue;

import com.swirlds.common.FCMValue;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.list.ListDigestException;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;

import static com.swirlds.common.ByteUtils.toBytes;
import static com.swirlds.common.ByteUtils.toLong;
import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.io.DataStreamUtils.readValidInt;
import static com.swirlds.common.io.DataStreamUtils.readValidLong;

/**
 * A threadsafe fast-copyable queue, each of whose elements is fast-copyable. Elements must always be inserted at the
 * tail and removed from the head. It is not allowed to insert nulls. This is fast copyable. A fast copy of a queue can
 * be either immutable or mutable. A mutable fast copy can only be created from a mutable queue, which would then become
 * immutable after creating this mutable fast copy.
 *
 * Element insertion/deletion and fast copy creation/deletion all take constant time. Except that if a queue has n
 * elements that are not in any other queue in its queue group, then deleting it takes O(n) time.
 *
 * The FCQueue objects can be thought of as being organized into "queue groups". A fast copy of a queue creates another
 * queue in the same queue group. But instantiating a queue with "new" and the constructor creates a new queue group.
 *
 * All write operations are synchronized within a queue group. So it is possible to write to two different queue
 * groups at the same time, but writing to different queues in the same queue group will be done serially. Reads via
 * getters are also serialized within a thread group, but it is ok for multiple iterators to be running in multiple
 * threads at the same time within that thread group. An iterator for a queue will throw an exception if it is used after
 * a write to that queue, but it is unaffected by writes to other queues in that queue group.
 */
public class FCQueue<E extends FCQueueElement<E>> extends AbstractMerkleLeaf implements Queue<E>, FCMValue {
	/**
	 * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
	 * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
	 * specially by the platform.
	 */
	private static final int VERSION_ORIGINAL = 1;
	/**
	 * FCQ implements MerkleLeaf, element implements FCQueueElement
	 */
	private static final int VERSION_MIGRATE_TO_SERIALIZABLE = 2;
	/** current version of this class. Increment whenever copyTo / copyToExtra changes format, or hashAlg changes */
	private static final int VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;

	/** Object identifier of this class (random int). Do NOT change when the class changes its code/name/version. */
	public static final long CLASS_ID = 139236190103L;

	/** Maximum number of elements FCQueue supports */
	public static final int MAX_ELEMENTS = 100_000_000;

	/** Calculate hash as: sum hash, rolling hash, Merkle hash.
	 *  rolling hash is recommended for now (unless Merkle is tried and found fast enough)
	 */
	private static final HashAlgorithm HASH_ALGORITHM = HashAlgorithm.ROLLING_HASH;

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

	/** serialized at the start of an element, for detecting bugs */
	private static final int BEGIN_ELEMENT_MARKER = 182113441;

	/** serialized at the end of an element, for detecting bugs */
	private static final int END_ELEMENT_MARKER = 182124951;

	/** the multiplicative inverse of 3 modulo 2 to the 64, in hex, is 15 "a" digits then a "b" digit */
	private static final long INVERSE_3 = 0xaaaaaaaaaaaaaaabL;


	/** the number of elements in this queue */
	private int size;

	/** 3 to the power of size, modulo 2 to the 64. Used for the rolling hash */
	private long threeToSize;

	/** the head of this queue */
	private FCQueueNode<E> head;

	/** the tail of this queue */
	private FCQueueNode<E> tail;

	/** the original FCQueue created with "new". Shared by the whole queue group, so every method synchronizes on it */
	private final FCQueue<E> original;

	/** the hash of set of elements in the queue. */
	private final byte[] hash = getNullHash();

	/** the hash read in by copyFrom when deserializing. It should equal this.hash after copyfromExtra is done. */
	private byte[] recoveredHash;

	/**
	 * The provider that can deserialize from a stream to create an element for this queue.
	 */
	private SerializedObjectProvider<E> elementProvider;

	/**
	 * The number of times this queue has changed so far, such as by add/remove/clear. This could be made volatile to
	 * catch more bugs, but then operations would be slower.
	 */
	private int numChanges;

	/**
	 * copyFrom defaults to version 1, but if copyFrom is used to read a later version, this variable will ensure
	 * that it works
	 */
	private int copyFromVersion = 1;

	/**
	 * Instantiates a new empty queue which doesn't require deserialization
	 */
	public FCQueue() {
		size = 0;
		// 3^^0 mod 2^^64 == 1
		threeToSize = 1;
		head = null;
		tail = null;
		original = this;
		//the first in a queue group is mutable until copy(true) is called on it
		setImmutable(false);
	}

	/**
	 * Instantiate a new empty queue that is in a new queue group by itself
	 *
	 * This constructor should only be use for migration of an old state
	 *
	 * @param elementProvider
	 * 		the object whose deserialize method can deserialize and instantiate an element of the queue
	 */
	@Deprecated
	public FCQueue(final SerializedObjectProvider<E> elementProvider) {
		this();
		this.elementProvider = elementProvider;
	}

	/** Instantiate a queue with all the given parameters. This is just a helper function, not visible to users. */
	private FCQueue(final FCQueue<E> fcQueue) {
		this.size = fcQueue.size;
		this.threeToSize = fcQueue.threeToSize;
		System.arraycopy(fcQueue.hash, 0, this.hash, 0, this.hash.length);
		this.head = fcQueue.head;
		this.tail = fcQueue.tail;
		this.original = fcQueue.original;
		this.elementProvider = fcQueue.original.elementProvider;
		this.setImmutable(false);
	}

	/** @return the number of times this queue has changed since it was instantiated by {@code new} or {@code copy} */
	int getNumChanges() {
		return numChanges;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash getHash() {
		return new ImmutableHash(hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setHash(final Hash hash) {
		throw new UnsupportedOperationException("FCQueue computes its own hash");
	}

	/**
	 * This method is intentionally a no-op.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void invalidateHash() {

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
	public boolean add(final E o) {
		synchronized (original) {

			if (isImmutable()) {
				throw new IllegalStateException("tried to modify an immutable FCQueue");
			}

			if (o == null) {
				throw new NullPointerException("tried to add a null element into an FCQueue");
			}

			if (this.size() >= MAX_ELEMENTS) {
				throw new IllegalStateException(
						String.format("tried to add an element to an FCQueue whose size has reached MAX_ELEMENTS: %d",
								MAX_ELEMENTS));
			}

			final FCQueueNode<E> node;

			if (tail == null) { //current list is empty
				node = new FCQueueNode<>(o);
				head = node;
			} else { //current list is nonempty, so add to the tail
				node = this.tail.insertAtTail(o);
				node.towardHead = tail;
				node.towardTail = null;
				tail.towardTail = node;
				tail.decRefCount();
			}

			tail = node;

			size++;
			threeToSize *= 3;
			numChanges++;

			final byte[] elementHash;

			elementHash = getHash(o);

			if (HASH_ALGORITHM == HashAlgorithm.SUM_HASH) {
				// This is treated as a "set", not "list", so changing the order does not change the hash. The hash is
				// the sum of the hashes of the elements, modulo 2^384.
				//
				// Note, for applications like Hedera, for the queue of receipts or queue of records, each element of
				// the queue will have a unique  timestamp, and they will always be sorted by those timestamps. So the
				// hash of the set is equivalent to the hash of a list. But if it is ever required to have a hash of a
				// list, then the rolling hash is better (HASH_ALGORITHM 1).

				// perform hash = (hash + elementHash) mod 2^^384
				int carry = 0;
				for (int i = 0; i < hash.length; i++) {
					carry += (hash[i] & 0xff) + (elementHash[i] & 0xff);
					hash[i] = (byte) carry;
					carry >>= 8;
				}
			} else if (HASH_ALGORITHM == HashAlgorithm.ROLLING_HASH) {
				// This is a rolling hash, so it takes into account the order.
				// if the queue contains {a,b,c,d}, where "a" is the head and "d" is the tail, then define:
				//
				//    hash64({a,b,c,d}) = a * 3^^3 + b * 3^^2 + c * 3^^1 + d * 3^^0 mod 2^^64
				//    hash64({a,b,c})   = a * 3^^2 + b * 3^^1 + c * 3^^0            mod 2^^64
				//    hash64({b,c,d})   = b * 3^^2 + c * 3^^1 + d * 3^^0            mod 2^^64
				//
				//    Which implies these:
				//
				//    hash64({a,b,c,d}) = hash64({a,b,c}) * 3 + d                   mod 2^^64     //add(d)
				//    hash64({b,c,d})   = hash64({a,b,c,d}) - a * 3^^3              mod 2^^64     //remove() deletes a
				//
				// so we add an element by multiplying by 3 and adding the new element's hash,
				// and we remove an element by subtracting that element times 3 to the power of the resulting size.
				//
				// This is all easy to do for a 64-bit hash by keeping track of 3^^size modulo 2^^64, and multiplying
				// it by 3 every time the size increments, and multiplying by the inverse of 3 each time it decrements.
				// The multiplicative inverse of 3 modulo 2^^64 is 0xaaaaaaaaaaaaaaab (that's 15 a digits then a b).
				//
				// It would be much slower to use modulo 2^^384, but we don't have to do that. We can treat the
				// 48-byte hash as a sequence of 6 numbers, each of which is an unsigned 64 bit integer.  We do this
				// rolling hash on each of the 6 numbers independently. Then it ends up being simple and fast

				for (int i = 0; i < 48; i += 8) { //process 8 bytes at a time
					long old = toLong(hash, i);
					long elm = toLong(elementHash, i);
					toBytes(old * 3 + elm, hash, i);
				}
			} else if (HASH_ALGORITHM == HashAlgorithm.MERKLE_HASH) {
				throw new UnsupportedOperationException("Hash algorithm " + HASH_ALGORITHM + " is not supported");
			} else { //invalid hashAlg choice
				throw new UnsupportedOperationException("Hash algorithm " + HASH_ALGORITHM + " is not supported");
			}

			return true;
		}
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
	public E remove() {
		synchronized (original) {

			final FCQueueNode<E> oldHead = head;

			if (isImmutable()) {
				throw new IllegalArgumentException("tried to remove from an immutable FCQueue");
			}

			if (size == 0 || head == null) {
				throw new NoSuchElementException("tried to remove from an empty FCQueue");
			}

			final E element = head.element;
			head = head.towardTail;

			if (head == null) { //if just removed the last one, then tail should be null, too
				tail.decRefCount();
				tail = null;
			} else {
				head.incRefCount();
			}

			oldHead.decRefCount(); //this will garbage collect the old head, if no copies point to it
			size--;
			threeToSize *= INVERSE_3;
			numChanges++;

			final byte[] elementHash;

			elementHash = getHash(element);

			if (HASH_ALGORITHM == HashAlgorithm.SUM_HASH) {
				// do hash = (hash - elementHash) mod 2^^384

				int carry = 0;
				for (int i = 0; i < hash.length; i++) {
					carry += (hash[i] & 0xff) - (elementHash[i] & 0xff);
					hash[i] = (byte) carry;
					carry >>= 8;
				}
			} else if (HASH_ALGORITHM == HashAlgorithm.ROLLING_HASH) {
				//see comments in add() about the rolling hash

				for (int i = 0; i < 48; i += 8) {//process 8 bytes at a time
					long old = toLong(hash, i);
					long elm = toLong(elementHash, i);
					toBytes(old - elm * threeToSize, hash, i);
				}
			} else if (HASH_ALGORITHM == HashAlgorithm.MERKLE_HASH) {
				throw new UnsupportedOperationException("Hash algorithm " + HASH_ALGORITHM + " is not supported");
			} else { //invalid hashAlg choice
				throw new UnsupportedOperationException("Hash algorithm " + HASH_ALGORITHM + " is not supported");
			}

			return element;
		}
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
	public boolean offer(final E o) {
		return add(o);
	}

	/**
	 * Retrieves and removes the head of this queue,
	 * or returns {@code null} if this queue is empty.
	 *
	 * @return the head of this queue, or {@code null} if this queue is empty
	 */
	@Override
	public E poll() {
		synchronized (original) {

			if (this.head == null) {
				return null;
			}

			return remove();
		}
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
	public E element() {
		synchronized (original) {

			if (this.head == null) {
				throw new NoSuchElementException("tried to get the head of an empty FCQueue");
			}

			return head.element;
		}
	}

	/**
	 * Retrieves, but does not remove, the head of this queue,
	 * or returns {@code null} if this queue is empty.
	 *
	 * @return the head of this queue, or {@code null} if this queue is empty
	 */
	@Override
	public E peek() {
		synchronized (original) {

			if (this.head == null) {
				return null;
			}

			return head.element;
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following implement FastCopyable
	//////////////////////////////////////////////////////////////////////////////////////////////////

	/** {@inheritDoc} */
	@Override
	public FCQueue<E> copy() {
		synchronized (original) {
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
	}

	/** {@inheritDoc} */
	@Override
	public void copyFrom(final SerializableDataInputStream inStream) throws IOException {
		synchronized (original) {
			clear();
			numChanges++;

			//readValidLong(inStream, "VERSION", 1);
			copyFromVersion = (int) inStream.readLong();
			readValidLong(inStream, "CLASS_ID", CLASS_ID);

			recoveredHash = new byte[hash.length];
			inStream.readFully(recoveredHash);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void copyFromExtra(final SerializableDataInputStream inStream) throws IOException {
		synchronized (original) {
			numChanges++;
			deserialize(inStream);
		}
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
	public int size() {
		synchronized (original) {
			return size;
		}
	}

	/**
	 * Returns {@code true} if this collection contains no elements.
	 *
	 * @return {@code true} if this collection contains no elements
	 */
	@Override
	public boolean isEmpty() {
		synchronized (original) {
			return size == 0;
		}
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
	public boolean contains(final Object o) {
		synchronized (original) {
			for (final E e : this) {

				if (Objects.equals(o, e)) {
					return true;
				}

			}

			return false;
		}
	}

	/**
	 * Returns an iterator over the elements in this queue, in insertion order (head first, tail last).
	 *
	 * @return an {@code Iterator} over the elements in this collection
	 */
	@Override
	public Iterator<E> iterator() {
		synchronized (original) {
			return new FCQueueIterator<>(this, head, tail);
		}
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
	public Object[] toArray() {
		synchronized (original) {

			final int size = size();
			final Object[] result = new Object[size];
			int i = 0;

			for (final E e : this) {
				result[i++] = e;
			}

			return result;
		}
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
	public <T> T[] toArray(T[] a) {
		synchronized (original) {

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
	public boolean containsAll(final Collection<?> c) {
		synchronized (original) {

			//This could be made faster by sorting both lists (if c is larger than log of size()).
			//But we'll do brute force for now (which is better for small c).
			for (final Object e : c) {
				if (!contains(e)) {
					return false;
				}
			}

			return true;
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean addAll(final Collection<? extends E> c) {
		synchronized (original) {

			for (final E e : c) {
				add(e);
			}

			return false;
		}
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
		synchronized (original) {
			throw new UnsupportedOperationException("FCQueue can only remove from the head");
		}
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
		synchronized (original) {
			throw new UnsupportedOperationException("FCQueue can only remove from the head");
		}
	}

	/**
	 * Removes all of the elements from this queue.
	 * The queue will be empty and the hash reset to the null value after this method returns.
	 * This does not delete the FCQueue object. It just empties the queue.
	 */
	@Override
	public void clear() {
		synchronized (original) {
			numChanges++;

			if (head != null) {
				head.decRefCount();
				head = null;
			}

			if (tail != null) {
				tail.decRefCount();
				tail = null;
			}

			size = 0;
			threeToSize = 1;  // 3^^0 mod 2^^64 == 1
			resetHash();
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following FastCopyable methods have default implementations, but are overridden here
	//////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
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

	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following Java.util.Collection methods have default implementations, but could be overridden
	//////////////////////////////////////////////////////////////////////////////////////////////////


	//////////////////////////////////////////////////////////////////////////////////////////////////
	//the following Java.util.Collection methods have default implementations, but could be overridden
	//////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Serializes the current object to an array of bytes in a deterministic manner.
	 *
	 * @param dos
	 * 		the {@link java.io.DataOutputStream} to which the object's binary form should be written
	 * @throws IOException
	 * 		if there are problems during serialization
	 */
	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
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

	/**
	 * Recovers the object state from the given {@link DataInputStream} that was previously written by {@link
	 * #serialize(SerializableDataOutputStream)}.
	 *
	 * @param dis
	 * 		the {@link DataInputStream} from which the object's binary form should be read
	 * @throws IOException
	 * 		if there are problems during serialization
	 */
	private void deserialize(final SerializableDataInputStream dis) throws IOException {
		deserialize(dis, copyFromVersion);
	}

	@Override
	public void deserialize(SerializableDataInputStream dis, int version) throws IOException {
		synchronized (original) {
			numChanges++;
			if (USE_MARKERS) {
				readValidInt(dis, "BEGIN_QUEUE_MARKER", BEGIN_QUEUE_MARKER);
			}
			if (version == VERSION_ORIGINAL) {
				readValidLong(dis, "VERSION", VERSION_ORIGINAL);
				readValidLong(dis, "CLASS_ID", CLASS_ID);
			} else {
				recoveredHash = new byte[hash.length];
			}

			final int listSize = dis.readInt();
			dis.readFully(recoveredHash);

			if (version == VERSION_ORIGINAL) {
				for (int i = 0; i < listSize; i++) {
					if (USE_MARKERS) {
						readValidInt(dis, "BEGIN_ELEMENT_MARKER", BEGIN_ELEMENT_MARKER);
					}
					final E element = elementProvider.deserialize(dis);
					add(element);
					if (USE_MARKERS) {
						readValidInt(dis, "END_ELEMENT_MARKER", END_ELEMENT_MARKER);
					}
				}
			} else {
				dis.readSerializableIterableWithSize(MAX_ELEMENTS, this::add);
			}

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
	}

	/**
	 * Find the hash of a FastCopyable object.
	 *
	 * @param element
	 * 		an element contained by this collection that is being added, deleted, or replaced
	 * @return the 48-byte hash of the element (getNullHash() if element is null)
	 */
	private byte[] getHash(final E element) {
		// Handle cases where list methods return null if the list is empty
		if (element == null) {
			return getNullHash();
		}
		Cryptography crypto = CryptoFactory.getInstance();
		//return a hash of a hash, in order to make state proofs smaller in the future
		crypto.digestSync(element);
		return crypto.digestSync(element.getHash()).getValue();
	}

	private static byte[] getNullHash() {
		return Arrays.copyOf(NULL_HASH, NULL_HASH.length);
	}

	private void resetHash() {
		System.arraycopy(NULL_HASH, 0, this.hash, 0, this.hash.length);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return VERSION;
	}

	@Override
	public int getMinimumSupportedVersion() {
		return VERSION_MIGRATE_TO_SERIALIZABLE;
	}

	private enum HashAlgorithm {
		SUM_HASH,
		ROLLING_HASH,
		MERKLE_HASH
	}
}
