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

package com.swirlds.common.constructable;

import java.util.Random;

/**
 * Generates random long to use as a class ID for RuntimeConstructable.
 */
public class GenerateClassId {
	public static void main(String[] args) {
		System.out.printf(
				"Simple program to generate a random long to use as a class ID for RuntimeConstructable.\n\n" +
						"\tprivate static final long CLASS_ID = 0x%sL;\n\n" +
						"\tprivate static final class ClassVersion {\n" +
						"\t\tpublic static final int ORIGINAL = 1;\n" +
						"\t}\n\n" +
						"\t@Override\n" +
						"\tpublic long getClassId() {\n" +
						"\t\treturn CLASS_ID;\n" +
						"\t}\n" +
						"\n" +
						"\t@Override\n" +
						"\tpublic int getVersion() {\n" +
						"\t\treturn ClassVersion.ORIGINAL;\n" +
						"\t}",
				Long.toHexString(new Random().nextLong())
		);
	}
}
