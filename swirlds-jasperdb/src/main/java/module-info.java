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
 * A disk-based VirtualDataSource implementation; complete module documentation
 * to be assembled over time as the full implementation is transplanted here.
 */
open module com.swirlds.jasperdb {
	exports com.swirlds.jasperdb.collections;
	exports com.swirlds.jasperdb.utilities;
	exports com.swirlds.jasperdb.files;
	exports com.swirlds.jasperdb.files.hashmap;
	exports com.swirlds.jasperdb;
	exports com.swirlds.jasperdb.settings;

	requires com.swirlds.common;
	requires com.swirlds.logging;
	requires com.swirlds.virtualmap;

	requires org.apache.commons.lang3;

	requires org.eclipse.collections.impl;
	requires org.eclipse.collections.api;

	requires org.apache.logging.log4j;
	requires org.apache.logging.log4j.core;

	requires java.management;
	requires jdk.management;
	requires jdk.unsupported;
}
