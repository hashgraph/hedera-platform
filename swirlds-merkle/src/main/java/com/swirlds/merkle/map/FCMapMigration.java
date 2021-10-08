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

package com.swirlds.merkle.map;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;

import java.util.function.Function;

/**
 * This class provides utility methods for migration from {@link MerkleMap} to {@link MerkleMap MerkleMap}.
 */
public final class FCMapMigration {

	private FCMapMigration() {

	}

	/**
	 * This utility function can be used to do an in-place conversion between the old FCMap data format and
	 * the new MerkleMap format. Converts from the old type {@code FCMap<OLD_KEY, OLD_VALUE>}
	 * to the new type {@code MerkleMap<NON_MERKLE_KEY, NEW_VALUE>}.
	 *
	 * @param parent
	 * 		the parent of the FCMap to convert
	 * @param mapIndex
	 * 		the index of the FCMap within its parent
	 * @param keyConverter
	 * 		a function that converts from the old key type to the new key type.
	 * 		If the returned key type is merkle then an exception is thrown. Returned
	 * 		key type must not be a merkle type.
	 * @param valueConverter
	 * 		a function that converts from the old value type to the new value type.
	 * 		If no conversion is required, it is ok to simply return the old value.
	 * @param <OLD_KEY>
	 * 		the original type of the key in the previous version
	 * @param <OLD_VALUE>
	 * 		the original type of the value in the previous version
	 * @param <NON_MERKLE_KEY>
	 * 		the new type of the key. This type should not be a merkle type!
	 * @param <NEW_VALUE>
	 * 		the new type of the value. Can be the same as the old value type.
	 */
	public static <
			OLD_KEY extends MerkleNode,
			OLD_VALUE extends MerkleNode,
			NON_MERKLE_KEY,
			NEW_VALUE extends MerkleNode & Keyed<NON_MERKLE_KEY>>
	void FCMapToMerkleMap(
			final MerkleInternal parent,
			final int mapIndex,
			final Function<OLD_KEY, NON_MERKLE_KEY> keyConverter,
			final Function<OLD_VALUE, NEW_VALUE> valueConverter) {

		final MerkleNode originalMap = parent.getChild(mapIndex);

		// Prevent premature garbage collection
		originalMap.incrementReferenceCount();

		final MerkleMap<NON_MERKLE_KEY, NEW_VALUE> newMap = new MerkleMap<>();
		parent.setChild(mapIndex, newMap);

		originalMap.forEachNode((final MerkleNode node) -> {
			if (node == null || node.getClassId() != MerklePair.CLASS_ID) {
				return;
			}

			final MerklePair<OLD_KEY, OLD_VALUE> pair = node.cast();
			final OLD_KEY oldKey = pair.getLeft();
			final OLD_VALUE oldValue = pair.getRight();

			final NON_MERKLE_KEY newKey = keyConverter.apply(oldKey);
			if (newKey instanceof MerkleNode) {
				throw new IllegalStateException("new key must not be a merkle node");
			}

			final NEW_VALUE convertedValue = valueConverter.apply(oldValue);
			final NEW_VALUE valueToInsert;
			if (oldValue == convertedValue) {
				valueToInsert = oldValue.copy().cast();
			} else {
				valueToInsert = convertedValue;
			}

			newMap.put(newKey, valueToInsert);
		});

		// Release our artificial reference
		originalMap.decrementReferenceCount();
	}
}
