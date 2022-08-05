/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.io;

/**
 * An object implementing this interface can return its length even before serialization.
 *
 * Different instances of the same class could have different lengths due to different
 * internal values, i.e, different length of an array variable.
 */
public interface SerializableWithKnownLength extends SelfSerializable {
	/** get an object's serialized length without doing serialization first */
	int getSerializedLength();
}
