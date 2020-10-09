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

package com.swirlds.common.constructable;

import java.util.Random;

/**
 * Generates random long to use as a class ID for RuntimeConstructable.
 */
public class GenerateClassId {
	public static void main(String[] args) {
		System.out.printf(
				"Simple program to generate a random long to use as a class ID for RuntimeConstructable.\n\n" +
						"\tprivate static final long CLASS_ID = 0x%sL;\n" +
						"\tprivate static final int VERSION_ORIGINAL = 1;\n" +
						"\tprivate static final int VERSION_MIGRATE_TO_SERIALIZABLE = 2;\n" +
						"\tprivate static final int CLASS_VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;\n" +
						"\n" +
						"\t@Override\n" +
						"\tpublic long getClassId() {\n" +
						"\t\treturn CLASS_ID;\n" +
						"\t}\n" +
						"\n" +
						"\t@Override\n" +
						"\tpublic int getVersion() {\n" +
						"\t\treturn CLASS_VERSION;\n" +
						"\t}",
				Long.toHexString(new Random().nextLong())
		);
	}
}
