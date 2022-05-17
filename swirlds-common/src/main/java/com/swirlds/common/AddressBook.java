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
package com.swirlds.common;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Address of every known member of the swirld. The getters are public and the setters aren't, so it is
 * read-only for apps.
 * When enableEventStreaming is set to be true, the memo field is required and should be unique.
 */
public class AddressBook extends AbstractMerkleLeaf implements Iterable<Address> {
	public static final long CLASS_ID = 0x4ee5498ef623fbe0L;

	private static class ClassVersion {
		/**
		 * In this version, the version was written as a long.
		 */
		public static final int ORIGINAL = 0;
		public static final int UNDOCUMENTED = 1;
		/**
		 * In this version, ad-hoc code was used to read and write the list of addresses.
		 */
		public static final int AD_HOC_SERIALIZATION = 2;
		/**
		 * In this version, AddressBook uses the serialization utilities to read & write the list of addresses.
		 */
		public static final int UTILITY_SERIALIZATION = 3;
	}

	private static final int MAX_ADDRESSES = 1000;


	// Ignore ThreadSafe, because it thinks that synchronizedList is thread safe
	// but UnmodifiableList is not. And it makes the same mistake for maps.
	// ThreadSafe: OFF
	private List<Address> addresses;
	private Map<String, Long> publicKeyToId;
	// ThreadSafe: ON
	/** the total stake of all members, or 0 if it hasn't been calculated yet */
	private long totalStake = 0;
	/** the stake of each member, where stakes[i] is the stake of the member with memberID i (null if not found yet) */
	private long[] stakes = null;

	/**
	 * return an exact copy of this AddressBook, except that it is immutable
	 *
	 * @return an immutable copy of the address book
	 */
	public AddressBook immutableCopy() {
		AddressBook ab = new AddressBook();
		// create an immutable copy (technically, an immutable view of a copy that isn't accessible to
		// anyone else) of both addresses and publicKeyToId
		ab.addresses = Collections
				.unmodifiableList(new LinkedList<Address>(addresses));
		ab.publicKeyToId = Collections
				.unmodifiableMap(new HashMap<String, Long>(publicKeyToId));
		ab.setImmutable(true);
		ab.setHash(this.getHash());
		return ab;
	}

	/**
	 * Create an empty address book. Only classes in the com.swirlds.platform package can add to it.
	 */
	public AddressBook() {
		this(new ArrayList<>());
	}

	/**
	 * Copy constructor.
	 */
	private AddressBook(final AddressBook that) {
		super(that);
		// shallow clone ok, because Address is immutable
		this.addresses = Collections
				.synchronizedList(new ArrayList<>(that.addresses));
		addressesToHashMap();
	}

	/**
	 * Create an address book initialized with the given list of addresses. Any class can instantiate this
	 * and set up the initial list of addresses. Only classes in the com.swirlds.platform package can add to
	 * it.
	 *
	 * @param addresses
	 * 		the addresses to start with
	 */
	public AddressBook(List<Address> addresses) {
		// shallow clone ok, because Address is immutable
		this.addresses = Collections
				.synchronizedList(new ArrayList<>(addresses));
		addressesToHashMap();
	}

	@Override
	public int getVersion() {
		return ClassVersion.UTILITY_SERIALIZATION;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	// create the hashMap and add all the current addresses
	private void addressesToHashMap() {
		publicKeyToId = Collections
				.synchronizedMap(new HashMap<>());
		for (int i = 0; i < addresses.size(); i++) {
			publicKeyToId.put(addresses.get(i).getNickname(), (long) i);
		}
	}

	/**
	 * Get the number of addresses currently in the address book.
	 *
	 * @return the number of addresses
	 */
	public int getSize() {
		return addresses.size();
	}

	/**
	 * Get the number of addresses currently in the address book that have a stake greater than zero.
	 *
	 * @return the number of addresses with a stake greater than zero
	 */
	public int getNumberWithStake() {
		return (int) addresses.stream().filter(v -> v.getStake() > 0).count();
	}

	/**
	 * Get the total stake of all members added together, where each member has nonnegative stake. This is zero if
	 * there are no members.
	 *
	 * @return the total stake
	 */
	public long getTotalStake() {
		if (totalStake == 0 && addresses != null) {
			long tmpTotalStake = 0;
			for (Address addr : addresses) {
				tmpTotalStake += addr.getStake();
			}
			totalStake = tmpTotalStake;
		}
		return totalStake;
	}

	/**
	 * Get an array of the stake of each member, indexed by the memberID.
	 *
	 * @return the array of member stakes
	 */
	public long[] getStakes() {
		if (stakes == null && addresses != null) {
			long[] tmpStakes = new long[addresses.size()];
			for (int id = 0; id < addresses.size(); id++) {
				tmpStakes[id] = getStake(id);
			}
			stakes = tmpStakes;
		}
		return stakes;
	}

	/**
	 * Get the number of addresses currently in the address book that are running on this computer. When the
	 * browser is run with a config.txt file, it can launch multiple copies of the app simultaneously, each
	 * with its own TCP/IP port. This method returns how many there are.
	 *
	 * @return the number of local addresses
	 */
	public int getOwnHostCount() {
		int count = 0;
		for (int i = 0; i < addresses.size(); i++) {
			if (addresses.get(i).isOwnHost()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Find the ID for the member whose address has the given public key. for now, this uses the nickname
	 * instead of the public key. Returns -1 if it does not exist.
	 *
	 * @param publicKey
	 * 		the public key to look up
	 * @return the ID of the member with that key, or -1 if it was not found
	 */
	public long getId(String publicKey) {
		if (publicKeyToId == null) {
			return -1;
		}
		Long ans = publicKeyToId.get(publicKey);
		if (ans == null) {
			return -1;
		}
		return ans;
	}

	/**
	 * Get the address for the member with the given ID
	 *
	 * @param id
	 * 		the member ID of the address to get
	 * @return the address, or null if it doesn't exist
	 */
	public Address getAddress(long id) {
		if (id < 0 || id >= addresses.size()) {
			return null;
		}
		return addresses.get((int) id);
	}

	/**
	 * Get the stake for the member with the given ID
	 *
	 * @param id
	 * 		the member ID of the member
	 * @return the nonnegative stake, or 0 if no such member exists
	 */
	public long getStake(long id) {
		Address addr = getAddress((int) id);
		return addr == null ? 0 : addr.getStake();
	}

	/**
	 * Indicates whether or not the stake configured in this {@link AddressBook} for this node is set to zero.
	 *
	 * @param nodeId
	 * 		the node identifier
	 * @return true if this node has zero stake assigned; otherwise, false if stake is greater than zero
	 * @throws InvalidNodeIdException
	 * 		if the specified {@code nodeId} is invalid
	 */
	public boolean isZeroStakeNode(final long nodeId) {
		final Address nodeAddress = getAddress(nodeId);

		if (nodeAddress == null) {
			throw new InvalidNodeIdException("NodeId may not be null");
		}

		return nodeAddress.getStake() == 0;
	}

	/**
	 * Set the address for the member with the given ID
	 *
	 * @param id
	 * 		the ID of a member
	 * @param address
	 * 		the address for that member
	 */
	public void setAddress(long id, Address address) {
		publicKeyToId.put(address.getNickname(), id);
		addresses.set((int) id, address);
		// this is the only method that changes the AddressBook, so we reset the hash here
		this.invalidateHash();
	}

	@Override
	public AddressBook copy() {
		AddressBook ab = new AddressBook(this);
		ab.setImmutable(false); // copy isn't immutable, even if the original was immutable
		return ab;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializableList(addresses, false, true);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		// AddressBook version used to be written as a long, so if the int read is 0,
		// then we read another int to get the version number
		if (version == ClassVersion.ORIGINAL) {
			version = in.readInt();
		}
		switch (version) {
			case ClassVersion.UNDOCUMENTED:
			case ClassVersion.AD_HOC_SERIALIZATION:
				this.addresses = Collections.synchronizedList(new ArrayList<>());
				int size = in.readInt();
				for (int i = 0; i < size; i++) {
					Address a = new Address();
					a.deserialize(in, version);
					addresses.add(a);
				}
				break;
			default:
				this.addresses = Collections.synchronizedList(
						in.readSerializableList(MAX_ADDRESSES, false, Address::new)
				);
		}


		addressesToHashMap();
		totalStake = 0; //force recalculation on the next getTotalStake() call
		stakes = null; //force recalculation on the next getStakes() call
	}

	@Override
	public int getMinimumSupportedVersion() {
		return ClassVersion.AD_HOC_SERIALIZATION;
	}

	@Override
	public Iterator<Address> iterator() {
		return addresses.iterator();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		AddressBook that = (AddressBook) o;
		return Objects.equals(addresses, that.addresses);
	}

	@Override
	public String toString() {
		return "AddressBook{" +
				"addresses=" + addresses +
				'}';
	}
}
