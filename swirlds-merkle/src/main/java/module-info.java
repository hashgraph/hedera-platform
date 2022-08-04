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
/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.merkle {
	exports com.swirlds.merkle.map;
	exports com.swirlds.merkle.tree;

	exports com.swirlds.merkle.tree.internal to com.swirlds.merkle.test;

	requires com.swirlds.common;
	requires com.swirlds.logging;
	requires com.swirlds.platform;
	requires com.swirlds.fcqueue;
	requires com.swirlds.fchashmap;

	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;
	requires org.apache.commons.lang3;

	requires java.sql;
}
