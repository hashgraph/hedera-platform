/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.common.merkle.io;

import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDet;

/**
 * Different algorithms that can be used for the serialization of various {@link SerializableDet} objects.
 */
public enum SerializationStrategy {
	/**
	 * The node is responsible for serializing itself. Must implement {@link SelfSerializable}.
	 */
	SELF_SERIALIZATION,
	/**
	 * The node is is responsible for serializing itself, but some of the data may be written to
	 * a location external to the primary data stream. Must implement {@link ExternalSelfSerializable}.
	 */
	EXTERNAL_SELF_SERIALIZATION,
	/**
	 * Only valid for {@link com.swirlds.common.merkle.MerkleInternal MerkleInternal} objects. If
	 * this strategy is supported then serialization is automatically handled by merkle utilities.
	 */
	DEFAULT_MERKLE_INTERNAL
}
