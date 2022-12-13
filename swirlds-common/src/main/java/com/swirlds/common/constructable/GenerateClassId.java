/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.constructable;

import java.util.Random;

/** Generates random long to use as a class ID for RuntimeConstructable. */
public class GenerateClassId {
    public static void main(String[] args) {
        System.out.printf(
                "Simple program to generate a random long to use as a class ID for"
                        + " RuntimeConstructable.\n\n"
                        + "\tprivate static final long CLASS_ID = 0x%sL;\n\n"
                        + "\tprivate static final class ClassVersion {\n"
                        + "\t\tpublic static final int ORIGINAL = 1;\n"
                        + "\t}\n\n"
                        + "\t@Override\n"
                        + "\tpublic long getClassId() {\n"
                        + "\t\treturn CLASS_ID;\n"
                        + "\t}\n"
                        + "\n"
                        + "\t@Override\n"
                        + "\tpublic int getVersion() {\n"
                        + "\t\treturn ClassVersion.ORIGINAL;\n"
                        + "\t}",
                Long.toHexString(new Random().nextLong()));
    }
}
