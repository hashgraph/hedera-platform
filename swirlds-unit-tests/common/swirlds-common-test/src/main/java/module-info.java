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
open module com.swirlds.common.test {
	exports com.swirlds.common.test;
	exports com.swirlds.common.test.io;
	exports com.swirlds.common.test.state;
	exports com.swirlds.common.test.merkle.util;
	exports com.swirlds.common.test.merkle.dummy;
	exports com.swirlds.common.test.dummy;
	exports com.swirlds.common.test.benchmark;
	exports com.swirlds.common.test.set;
	exports com.swirlds.common.test.map;
	exports com.swirlds.common.test.threading;
	exports com.swirlds.common.test.crypto;
	exports com.swirlds.common.test.constructable to com.swirlds.common;
	exports com.swirlds.common.test.constructable.subpackage to com.swirlds.common;

	requires com.swirlds.test.framework;
	requires com.swirlds.common;

	requires org.bouncycastle.provider;
	requires org.junit.jupiter.api;
	requires org.apache.commons.lang3;

	requires java.scripting;
	requires org.apache.logging.log4j;

	requires com.fasterxml.jackson.databind;
	requires lazysodium.java;
}
