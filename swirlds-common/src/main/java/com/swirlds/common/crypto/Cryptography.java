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

package com.swirlds.common.crypto;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.hash.FutureMerkleHash;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public interface Cryptography {
	/** The default value for the setHash argument */
	boolean DEFAULT_SET_HASH = true;
	/** The default digest type */
	DigestType DEFAULT_DIGEST_TYPE = DigestType.SHA_384;

	/**
	 * Computes a cryptographic hash (message digest) for the given message. The resulting hash value will be returned
	 * by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()}) has been
	 * completed.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param message
	 * 		the message to be hashed
	 */
	void digestAsync(final Message message);

	/**
	 * Computes a cryptographic hash (message digest) for the given list of messages. The resulting hash value will be
	 * returned by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()})
	 * has been completed.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param messages
	 * 		a list of messages to be hashed
	 */
	void digestAsync(final List<Message> messages);

	/**
	 * Computes a cryptographic hash (message digest) for the given message. Convenience method that defaults to {@link
	 * DigestType#SHA_384} message digests.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param message
	 * 		the message contents to be hashed
	 * @return a {@link Future} containing the cryptographic hash for the given message contents when resolved
	 */
	default Future<Hash> digestAsync(final byte[] message) {
		return digestAsync(message, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * Computes a cryptographic hash (message digest) for the given message.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param message
	 * 		the message contents to be hashed
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @return a {@link Future} containing the cryptographic hash for the given message contents when resolved
	 */
	Future<Hash> digestAsync(final byte[] message, final DigestType digestType);

	/**
	 * Computes a cryptographic hash (message digest) for the given message. The resulting hash value will be
	 * returned by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()})
	 * has been completed.
	 *
	 * @param message
	 * 		the message to be hashed
	 * @return the cryptographic hash for the given message
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	Hash digestSync(final Message message);

	/**
	 * Computes a cryptographic hash (message digest) for the given list of messages. The resulting hash value will be
	 * returned by the {@link Message#getHash()} method once the future (accessible from {@link Message#getFuture()})
	 * has been completed.
	 *
	 * @param messages
	 * 		a list of messages to be hashed
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	void digestSync(final List<Message> messages);

	/**
	 * Computes a cryptographic hash (message digest) for the given message. Convenience method that defaults to {@link
	 * DigestType#SHA_384} message digests.
	 *
	 * @param message
	 * 		the message contents to be hashed
	 * @return the cryptographic hash for the given message contents
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	default Hash digestSync(final byte[] message) {
		return digestSync(message, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * Computes a cryptographic hash (message digest) for the given message.
	 *
	 * @param message
	 * 		the message contents to be hashed
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @return the cryptographic hash for the given message contents
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	Hash digestSync(final byte[] message, final DigestType digestType);

	/**
	 * Same as {@link #digestSync(SelfSerializable, DigestType)} with DigestType set to SHA_384
	 *
	 * @param serializable
	 * 		the object to be hashed
	 * @return the cryptographic hash for the {@link SelfSerializable} object
	 */
	default Hash digestSync(final SelfSerializable serializable) {
		return digestSync(serializable, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * Computes a cryptographic hash for the {@link SelfSerializable} instance by serializing it and hashing the
	 * bytes. The hash is then returned by this method
	 *
	 * @param serializable
	 * 		the object to be hashed
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @return the cryptographic hash for the {@link SelfSerializable} object
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	Hash digestSync(final SelfSerializable serializable, final DigestType digestType);

	/**
	 * Same as {@link #digestSync(SerializableHashable, DigestType)} with DigestType set to SHA_384
	 *
	 * @param serializableHashable
	 * 		the object to be hashed
	 */
	default Hash digestSync(final SerializableHashable serializableHashable) {
		return digestSync(serializableHashable, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * Same as {@link #digestSync(SerializableHashable, DigestType, boolean)} with setHash set to true
	 */
	default Hash digestSync(final SerializableHashable serializableHashable, final DigestType digestType) {
		return digestSync(serializableHashable, digestType, DEFAULT_SET_HASH);
	}

	/**
	 * Computes a cryptographic hash for the {@link SerializableHashable} instance by serializing it and hashing the
	 * bytes. The hash is then passed to the object by calling {@link Hashable#setHash(Hash)} if setHash is true.
	 *
	 * @param serializableHashable
	 * 		the object to be hashed
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @param setHash
	 * 		should be set to true if the calculated should be assigned to the serializableHashable object
	 * @return the cryptographic hash for the {@link SelfSerializable} object
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	Hash digestSync(final SerializableHashable serializableHashable, final DigestType digestType, boolean setHash);

	/**
	 * Computes a cryptographic hash for the {@link MerkleInternal} instance. The hash is passed to the object
	 * by calling {@link Hashable#setHash(Hash)}. Convenience method that defaults to {@link DigestType#SHA_384} message
	 * digests.
	 *
	 * @param node
	 * 		the MerkleInternal to hash
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	default Hash digestSync(final MerkleInternal node) {
		return digestSync(node, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * Same as {@link #digestSync(SerializableHashable, DigestType, boolean)} with setHash set to true
	 */
	default Hash digestSync(final MerkleInternal node, final DigestType digestType) {
		return digestSync(node, digestType, DEFAULT_SET_HASH);
	}

	/**
	 * Computes a cryptographic hash for the {@link MerkleInternal} instance. The hash is passed to the object
	 * by calling {@link Hashable#setHash(Hash)} if setHash is true.
	 *
	 * @param node
	 * 		the MerkleInternal to hash
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @param setHash
	 * 		should be set to true if the calculated should be assigned to the node
	 * @return the cryptographic hash for the {@link MerkleInternal} object
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	default Hash digestSync(final MerkleInternal node, final DigestType digestType, boolean setHash) {
		List<Hash> childHashes = new ArrayList<>(node.getNumberOfChildren());
		for (int childIndex = 0; childIndex < node.getNumberOfChildren(); childIndex++) {
			MerkleNode child = node.getChild(childIndex);
			if (child == null) {
				childHashes.add(getNullHash(digestType));
			} else {
				childHashes.add(child.getHash());
			}
		}
		return digestSync(node, childHashes, setHash);
	}

	/**
	 * Same as {@link #digestSync(SerializableHashable, DigestType, boolean)} with setHash set to true
	 */
	default Hash digestSync(final MerkleInternal node, final List<Hash> childHashes) {
		return digestSync(node, childHashes, DEFAULT_SET_HASH);
	}

	/**
	 * Computes a cryptographic hash for the {@link MerkleInternal} instance. Requires a list of child hashes,
	 * as it is possible that the MerkleInternal has not yet been given its children. The hash is passed to the object
	 * by calling {@link Hashable#setHash(Hash)} if setHash is true.
	 *
	 * @param node
	 * 		the MerkleInternal to hash
	 * @param childHashes
	 * 		a list of the hashes of this node's children
	 * @param setHash
	 * 		should be set to true if the calculated should be assigned to the node
	 * @return the cryptographic hash for the {@link MerkleInternal} object
	 */
	Hash digestSync(final MerkleInternal node, final List<Hash> childHashes, boolean setHash);

	/**
	 * Computes a cryptographic hash for the {@link MerkleLeaf} instance. The hash is passed to the object
	 * by calling {@link Hashable#setHash(Hash)}.
	 *
	 * @param leaf
	 * 		the {@link MerkleLeaf} to hash
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	default Hash digestSync(MerkleLeaf leaf, DigestType digestType) {
		return digestSync((SerializableHashable) leaf, digestType);
	}

	/**
	 * Computes a cryptographic hash for the {@link MerkleNode} instance. The hash is passed to the object
	 * by calling {@link Hashable#setHash(Hash)}.
	 *
	 * @param node
	 * 		the {@link MerkleNode} to hash
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	default Hash digestSync(MerkleNode node, DigestType digestType) {
		if (node.isLeaf()) {
			return digestSync(node.asLeaf(), digestType);
		}

		return digestSync(node.asInternal(), digestType);
	}

	/**
	 * Compute the hash of the merkle tree synchronously on the caller's thread.
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @return The hash of the tree.
	 */
	Hash digestTreeSync(final MerkleNode root, final DigestType digestType);

	/**
	 * Same as {@link #digestTreeSync(MerkleNode, DigestType)}  with DigestType set to SHA_384
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @return the cryptographic hash for the {@link MerkleNode} object
	 */
	default Hash digestTreeSync(final MerkleNode root) {
		return digestTreeSync(root, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * Compute the hash of the merkle tree on multiple worker threads.
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @return the {@link FutureMerkleHash} for the {@link MerkleNode} object
	 */
	Future<Hash> digestTreeAsync(final MerkleNode root, final DigestType digestType);

	/**
	 * Same as {@link #digestTreeAsync(MerkleNode, DigestType)}  with DigestType set to SHA_384
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @return the {@link FutureMerkleHash} for the {@link MerkleNode} object
	 */
	default Future<Hash> digestTreeAsync(final MerkleNode root) {
		return digestTreeAsync(root, DEFAULT_DIGEST_TYPE);
	}

	/**
	 * @return the hash for a null value. Uses SHA_384.
	 */
	default Hash getNullHash() {
		return getNullHash(DEFAULT_DIGEST_TYPE);
	}

	/**
	 * @param digestType
	 * 		the type of digest used to compute the hash
	 * @return the hash for a null value.
	 */
	Hash getNullHash(final DigestType digestType);

	/**
	 * Verifies the given digital signature for authenticity. The result of the verification will be returned by the
	 * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
	 * TransactionSignature#getFuture()}) has been completed.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param signature
	 * 		the signature to be verified
	 */
	void verifyAsync(final TransactionSignature signature);

	/**
	 * Verifies the given digital signatures for authenticity. The result of the verification will be returned by the
	 * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
	 * TransactionSignature#getFuture()}) has
	 * been completed.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param signatures
	 * 		a list of signatures to be verified
	 */
	void verifyAsync(final List<TransactionSignature> signatures);

	/**
	 * Verifies the given digital signature for authenticity. Convenience method that defaults to {@link
	 * SignatureType#ED25519} signatures.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param data
	 * 		the original contents that the signature should be verified against
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key required to validate the signature
	 * @return a {@link Future} that will contain the true if the signature is valid; otherwise false when
	 * 		resolved
	 */
	default Future<Boolean> verifyAsync(final byte[] data, final byte[] signature, final byte[] publicKey) {
		return verifyAsync(data, signature, publicKey, SignatureType.ED25519);
	}

	/**
	 * Verifies the given digital signature for authenticity.
	 *
	 * Note: This implementation is non-blocking and returns almost immediately.
	 *
	 * @param data
	 * 		the original contents that the signature should be verified against
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key required to validate the signature
	 * @param signatureType
	 * 		the type of signature to be verified
	 * @return a {@link Future} that will contain the true if the signature is valid; otherwise false when
	 * 		resolved
	 */
	Future<Boolean> verifyAsync(final byte[] data, final byte[] signature, final byte[] publicKey,
			final SignatureType signatureType);

	/**
	 * Verifies the given digital signature for authenticity. The result of the verification will be returned by the
	 * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
	 * TransactionSignature#getFuture()}) has
	 * been completed.
	 *
	 * @param signature
	 * 		the signature to be verified
	 * @return true if the signature is valid; otherwise false
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	boolean verifySync(final TransactionSignature signature);

	/**
	 * Verifies the given digital signatures for authenticity. The result of the verification will be returned by the
	 * {@link TransactionSignature#getSignatureStatus()} method once the future (available via {@link
	 * TransactionSignature#getFuture()}) has
	 * been completed.
	 *
	 * @param signatures
	 * 		a list of signatures to be verified
	 * @return true if all the signatures are valid; otherwise false
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	boolean verifySync(final List<TransactionSignature> signatures);

	/**
	 * Verifies the given digital signature for authenticity. Convenience method that defaults to {@link
	 * SignatureType#ED25519} signatures.
	 *
	 * @param data
	 * 		the original contents that the signature should be verified against
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key required to validate the signature
	 * @return true if the signature is valid; otherwise false
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	default boolean verifySync(final byte[] data, final byte[] signature, final byte[] publicKey) {
		return verifySync(data, signature, publicKey, SignatureType.ED25519);
	}

	/**
	 * Verifies the given digital signature for authenticity.
	 *
	 * @param data
	 * 		the original contents that the signature should be verified against
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key required to validate the signature
	 * @param signatureType
	 * 		the type of signature to be verified
	 * @return true if the signature is valid; otherwise false
	 * @throws CryptographyException
	 * 		if an unrecoverable error occurs while computing the digest
	 */
	boolean verifySync(final byte[] data, final byte[] signature, final byte[] publicKey,
			final SignatureType signatureType);

	/**
	 * Computes a cryptographic hash for the concatenation of current running Hash and
	 * the given newHashToAdd.
	 * return the calculated running Hash
	 *
	 * @param runningHash
	 * 		the calculated running {@code Hash}
	 * @param newHashToAdd
	 * 		a Hash for updating the runningHash
	 * @param digestType
	 * 		the digest type used to compute runningHash
	 * @return calculated running Hash
	 */
	Hash calcRunningHash(final Hash runningHash, final Hash newHashToAdd, final DigestType digestType);
}
