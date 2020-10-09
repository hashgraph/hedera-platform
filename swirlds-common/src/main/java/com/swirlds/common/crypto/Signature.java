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

package com.swirlds.common.crypto;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;

import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.crypto.SignatureType.RSA;

/**
 * Encapsulates a cryptographic signature along with its SignatureType.
 */
public class Signature implements SelfSerializable {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private static final long CLASS_ID = 0x13dc4b399b245c69L;
	private static final int CLASS_VERSION = 1;

	/** The type of cryptographic algorithm used to create the signature */
	private SignatureType signatureType;
	/** signature byte array */
	private byte[] sigBytes;

	/**
	 * For RuntimeConstructable
	 */
	public Signature() {
	}

	public Signature(SignatureType signatureType, byte[] sigBytes) {
		this.signatureType = signatureType;
		this.sigBytes = sigBytes;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		this.signatureType = SignatureType.from(in.readInt(), RSA);
		this.sigBytes = in.readByteArray(this.signatureType.getSignatureLength(), true);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(signatureType.ordinal());
		out.writeByteArray(sigBytes, true);
	}

	/**
	 * check whether this signature is signed by given publicKey on given data
	 *
	 * @param data
	 * 		the data that was signed
	 * @param publicKey
	 * 		publicKey
	 * @return true if the signature is valid
	 */
	public boolean verifySignature(byte[] data, PublicKey publicKey) {
		if (publicKey == null) {
			log.info(LOGM_EXCEPTION, "verifySignature :: missing PublicKey");
			return false;
		}

		final String signingAlgorithm = signatureType.signingAlgorithm();
		final String sigProvider = signatureType.provider();
		try {
			java.security.Signature sig = java.security.Signature.getInstance(signingAlgorithm, sigProvider);
			sig.initVerify(publicKey);
			sig.update(data);
			return sig.verify(sigBytes);
		} catch (NoSuchAlgorithmException | NoSuchProviderException
				| InvalidKeyException | SignatureException e) {
			log.error(LOGM_EXCEPTION, " verifySignature :: Fail to verify Signature: {}, PublicKey: {}",
					this,
					hex(publicKey.getEncoded()), e);
		}
		return false;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Signature)) {
			return false;
		}

		Signature signature = (Signature) obj;
		return Arrays.equals(sigBytes, signature.sigBytes)
				&& signatureType == signature.signatureType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(signatureType, sigBytes);
	}

	@Override
	public String toString() {
		return String.format("Signature{signatureType: %s, sigBytes: %s",
				signatureType, hex(sigBytes));
	}
}
