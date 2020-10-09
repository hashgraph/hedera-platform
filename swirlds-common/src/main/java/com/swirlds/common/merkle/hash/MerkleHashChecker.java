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

package com.swirlds.common.merkle.hash;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleBreadthFirstIterator;

import java.util.Iterator;
import java.util.function.Consumer;

import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;

public class MerkleHashChecker {
	private Cryptography cryptography;

	/**
	 * Construct an object which calculates the hash of a merkle tree.
	 *
	 * @param cryptography
	 * 		the {@link Cryptography} implementation to use
	 */
	public MerkleHashChecker(Cryptography cryptography) {
		this.cryptography = cryptography;
	}

	public void checkSync(MerkleNode root, Consumer<MerkleNode> mismatchCallback) {
		checkSync(cryptography, root, mismatchCallback);
	}

	/**
	 * Traverses the merkle tree and checks if there are any hashes that are not valid. It will recalculate all hashes
	 * that have been calculated externally and check them against the getHash value.
	 *
	 * @param cryptography
	 * 		used to calculate hashes
	 * @param root
	 * 		the root of the merkle tree
	 * @param mismatchCallback
	 * 		the method to call if a mismatch is found
	 */
	public static void checkSync(Cryptography cryptography, MerkleNode root, Consumer<MerkleNode> mismatchCallback) {
		if (root == null) {
			return;
		}
		Iterator<MerkleNode> it = new MerkleBreadthFirstIterator<>(root);
		while (it.hasNext()) {
			MerkleNode node = it.next();
			if (node == null) {
				continue;
			}
			Hash old = node.getHash();
			if (old == null) {
				mismatchCallback.accept(node);
				continue;
			}
			// some nodes calculate their own hash, we can't check these
			try {
				node.setHash(null);
			} catch (Exception e) {
				continue;
			}
			if (node.getHash() != null) {
				continue;
			}

			cryptography.digestSync(node, MERKLE_DIGEST_TYPE);
			if (!old.equals(node.getHash())) {
				mismatchCallback.accept(node);
			}
		}
	}
}
