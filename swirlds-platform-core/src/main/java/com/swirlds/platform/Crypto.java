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
package com.swirlds.platform;

import com.swirlds.common.internal.CryptoUtils;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * @deprecated Since 0.7.0
 */
@Deprecated
public class Crypto extends CryptoUtils {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	private final KeysAndCerts keysAndCerts;
	/** a pool of threads used to verify signatures and generate keys, in parallel */
	private final ExecutorService cryptoThreadPool;

	/**
	 * @param keysAndCerts
	 * 		keys and certificates
	 * @param cryptoThreadPool
	 * 		the thread pool that will be used for all operations that can be done in parallel, like signing and
	 * 		verifying
	 */
	public Crypto(final KeysAndCerts keysAndCerts, final ExecutorService cryptoThreadPool) {
		this.keysAndCerts = keysAndCerts;
		this.cryptoThreadPool = cryptoThreadPool;
	}


	/**
	 * Digitally sign the data with the private key. Return null if anything goes wrong (e.g., bad private
	 * key).
	 * <p>
	 * The returned signature will be at most SIG_SIZE_BYTES bytes, which is 104 for the CNSA suite
	 * parameters.
	 *
	 * @param data
	 * 		the data to sign
	 * @return the signature (or null if any errors)
	 */
	public byte[] sign(final byte[] data) {
		try {
			final PlatformSigner signer = new PlatformSigner(keysAndCerts);
			return signer.sign(data);
		} catch (NoSuchAlgorithmException | NoSuchProviderException
				| InvalidKeyException | RuntimeException e) {
			log.error(EXCEPTION.getMarker(), "ERROR in sig 3", e);
		}
		return null;
	}

	/**
	 * Verify the given signature for the given data. This is submitted to the thread pool so that it will
	 * be done in parallel with other signature verifications and key generation operations. This method
	 * returns a Future immediately. If the signature is valid, then a get() method on that Future will
	 * eventually return a Boolean which is true if the signature was valid. After the thread does the
	 * validation, and before it returns, it will run doLast(true) if the signature was valid, or
	 * doLast(false) if it wasn't.
	 *
	 * This is flexible. It is OK to ignore the returned Future, and only have doLast handle the result. It
	 * is also OK to pass in (Boolean b) for doLast, and handle the result of doing a .get() on the
	 * Future. Or both mechanisms can be used.
	 *
	 * @param data
	 * 		the data that was signed
	 * @param signature
	 * 		the claimed signature of that data
	 * @param publicKey
	 * 		the claimed public key used to generate that signature
	 * @param doLast
	 * 		a function that will be run after verification, and will be passed true if the signature
	 * 		is valid. To do nothing, pass in (Boolean b)
	 * @return validObject if the signature is valid, else returns null
	 */
	public Future<Boolean> verifySignatureParallel(
			final byte[] data,
			final byte[] signature,
			final PublicKey publicKey,
			final Consumer<Boolean> doLast) {
		return cryptoThreadPool.submit(() -> {
			boolean result = CryptoStatic.verifySignature(data, signature, publicKey);
			doLast.accept(result);
			return result;
		});
	}

	public KeysAndCerts getKeysAndCerts() {
		return keysAndCerts;
	}
}
