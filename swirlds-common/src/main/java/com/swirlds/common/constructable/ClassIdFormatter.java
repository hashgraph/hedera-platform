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

package com.swirlds.common.constructable;

import com.swirlds.common.io.SerializableDet;

/**
 * This class provides utility methods for converting class IDs into human readable strings.
 */
public final class ClassIdFormatter {

	private ClassIdFormatter() {

	}

	/**
	 * Convert a class ID to a human readable string in the format "123456789(0x75BCD15)"
	 *
	 * @param classId
	 * 		the class ID to convert to a string
	 * @return a formatted string
	 */
	public static String classIdString(final long classId) {
		return String.format("%d(0x%X)", classId, classId);
	}

	/**
	 * Convert a runtime constructable object to a human readable string in the format
	 * "com.swirlds.ClassName:123456789(0x75BCD15)"
	 *
	 * @param object
	 * 		the object to form about
	 * @return a formatted string
	 */
	public static String classIdString(final RuntimeConstructable object) {
		return String.format("%s:%s", object.getClass().getName(), classIdString(object.getClassId()));
	}

	/**
	 * Convert a serializable object to a human readable string in the format
	 * "com.swirlds.ClassName:123456789(0x75BCD15)v1"
	 *
	 * @param object
	 * 		the object to form about
	 * @return a formatted string
	 */
	public static String versionedClassIdString(final SerializableDet object) {
		return String.format("%s:%sv%d",
				object.getClass().getName(),
				classIdString(object.getClassId()),
				object.getVersion());
	}

}
