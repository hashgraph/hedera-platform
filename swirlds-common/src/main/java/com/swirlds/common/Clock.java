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

package com.swirlds.common;

/**
 * The class {@code Clock} can be used to simplify tests of time-sensitive code.
 *
 * Instead of calling {@link System#nanoTime()} directly, a time-sensitive class should keep an instance of
 * {@code Clock} and call its {@link Clock#now()}. In production the class would use {@link #DEFAULT}, which
 * simply forwards the call to {@link System#nanoTime()}. But in a test scenario, one can provide ones own
 * implementation (e.g. a mock), providing close control over the passing time.
 */
@FunctionalInterface
public interface Clock {

	/**
	 * Default implementation of {@code Clock} that simply forwards to {@link System#nanoTime()}
	 */
	Clock DEFAULT = System::nanoTime;

	/**
	 * A method that returns passing time in nanoseconds.
	 *
	 * @return the current nanoseconds value
	 */
	long now();
}
