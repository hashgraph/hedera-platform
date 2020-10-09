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
package com.swirlds.platform;

import com.swirlds.common.Transaction;
import com.swirlds.common.internal.HashUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * A cryptographic hash. A new one can be instantiated and then initialized by repeatedly hashing bytes
 * until done. Once it is done, this object is immutable, at least as far as the public methods will show.
 * It implements Java's hash function, so it can be used as a key in a HashMap. It uses Platform.hashMapSeed
 * to make it difficult for an attacker to force lots of collisions in a HashMap that uses this as a key.
 */
@Deprecated
public class Hash extends HashUtils implements Comparable<Hash> {
	private final int hashMapSeed;
	final byte[] hash;

	/**
	 * construct an immutable object containing the hash of (selfHash, otherHash, time, trans). If
	 * selfHash==null, then hash creatorId, instead. And similarly for otherHash.
	 *
	 * @param hashMapSeed
	 * 		used to calculate Java hashes for HashMap keys that are cryptographic hashes
	 * @param creatorId
	 * 		the ID of the member who created the event being hashed
	 * @param selfHash
	 * 		the hash of the self-parent of the event being hashed
	 * @param otherHash
	 * 		the hash of the other-parent of the event being hashed
	 * @param selfParentGen
	 * 		the claimed generation for the self parent
	 * @param otherParentGen
	 * 		the claimed generation for the other parent
	 * @param time
	 * 		the claimed time at which the event was created
	 * @param transactions
	 * 		the transactions in the event (or null, if none)
	 */
	public Hash(int hashMapSeed, long creatorId, byte[] selfHash, byte[] otherHash, long selfParentGen,
			long otherParentGen, Instant time, Transaction[] transactions) {
		this.hashMapSeed = hashMapSeed;
		MessageDigest md = null;
		md = Crypto.getMessageDigest();
		if (selfHash != null) {
			md.update(selfHash);
		} else {
			update(md, creatorId); // if no parent, hash own id, so initial events all different
		}
		if (otherHash != null) {
			md.update(otherHash);
		} else {
			update(md, creatorId); // if no parent, hash own id
		}
		update(md, selfParentGen);
		update(md, otherParentGen);
		update(md, time == null ? 0 : time.getEpochSecond());
		update(md, time == null ? 0 : time.getNano());
		update(md, transactions);
		hash = md.digest();
	}

	/**
	 * Constructs an immutable object from a hash that has already been calculated
	 *
	 * @param hash
	 * 		the hash that has been calculated beforehand
	 * @param hashMapSeed
	 * 		the seed
	 */
	private Hash(byte[] hash, int hashMapSeed) {
		this.hashMapSeed = hashMapSeed;
		this.hash = hash;
	}

	void writeHash(DataOutputStream dos) throws IOException {
		dos.writeInt(hashMapSeed);
		Utilities.writeByteArray(dos, hash);
	}

	public static Hash readHash(DataInputStream dis) throws IOException {
		int hashMapSeed = dis.readInt();
		byte hash[] = Utilities.readByteArray(dis);
		return new Hash(hash, hashMapSeed);
	}

	@Override
	public boolean equals(Object object) {
		if (!(object instanceof Hash)) {
			return false;
		}
		return compareTo((Hash) object) == 0;
	}

	@Override
	public int hashCode() {
		int result = hashMapSeed;
		for (int i = 0; i < hash.length; i++) {
			result = 31 * result + hash[i];
		}
		return result;
	}

	@Override
	public int compareTo(Hash other) {
		if (other == null) {
			return 1; // something is greater than nothing
		}
		for (int i = 0; i < hash.length; i++) {
			if (hash[i] > other.hash[i]) {
				return 1;
			} else if (hash[i] < other.hash[i]) {
				return -1;
			}
		}
		return 0;
	}

	public byte[] getValue(){
		return hash;
	}
}
