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

package com.swirlds.fcmap.internal;

import com.swirlds.common.FCMKey;
import com.swirlds.common.FCMValue;
import com.swirlds.fcmap.FCMap;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @deprecated Use Merkle utilities to serialize Merkle Nodes
 * @param <K>
 * @param <V>
 */
@Deprecated
public class FCSerializer<K extends FCMKey, V extends FCMValue> {

	private static final Marker SERIALIZATION = MarkerManager.getMarker("FC_SERIALIZATION");

	/**
	 * Start delimiter for leaf keys
	 */
	private static final int KEY_S = 1_801_812_339; // 'k', 'e', 'y', 's'

	/**
	 * End delimiter for leaf keys
	 */
	private static final int KEY_E = 1_801_812_325; // 'k', 'e', 'y', 's'

	/**
	 * Start delimiter for leaf values
	 */
	private static final int VALUE_S = 1_986_096_243; // 'v', 'a', 'l', 's'

	/**
	 * End delimiter for leaf values
	 */
	private static final int VALUE_E = 1_986_096_229; // 'v', 'a', 'l', 's'

	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger LOG = LogManager.getLogger(FCMap.class);

	public List<FCMLeaf<K, V>> deserialize(final SerializedObjectProvider keyProvider,
			final SerializedObjectProvider valueProvider,
			final SerializableDataInputStream inputStream) throws IOException {
		return deserializeBody(keyProvider, valueProvider, inputStream);
	}

	@SuppressWarnings("unchecked")
	private List<FCMLeaf<K, V>> deserializeBody(final SerializedObjectProvider keyProvider,
			final SerializedObjectProvider valueProvider,
			final SerializableDataInputStream inputStream) throws IOException {
		final long numberOfLeaves = inputStream.readLong();
		final List<FCMLeaf<K, V>> leaves = new ArrayList<>((int) numberOfLeaves);
		LOG.trace(SERIALIZATION, "Deserializing {} leaves", ()->numberOfLeaves);
		for (int leafIndex = 0; leafIndex < numberOfLeaves; leafIndex++) {
			final int keyS = inputStream.readInt();
			if (keyS != KEY_S) {
				throw new IOException(
						String.format("Key %d/%d does not start at the correct position", leafIndex, numberOfLeaves));
			}

			final K key = (K) keyProvider.deserialize(inputStream);
			final int keyE = inputStream.readInt();
			if (keyE != KEY_E) {
				throw new IOException(
						String.format("Key %d/%d didn't consume the input stream correctly", leafIndex,
								numberOfLeaves));
			}

			final int valueS = inputStream.readInt();
			if (valueS != VALUE_S) {
				throw new IOException(
						String.format("Value %d/%d does not start at the correct position", leafIndex, numberOfLeaves));
			}

			final V value = (V) valueProvider.deserialize(inputStream);
			final int valueE = inputStream.readInt();
			if (valueE != VALUE_E) {
				throw new IOException(String.format("Value %d/%d didn't consume the input stream correctly", leafIndex,
						numberOfLeaves));
			}

			leaves.add(new FCMLeaf<>(key, value));
		}

		return leaves;
	}
}
