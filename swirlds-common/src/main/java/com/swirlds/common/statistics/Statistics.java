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

package com.swirlds.common.statistics;

import com.swirlds.common.metrics.Metric;

import java.util.List;

/**
 * An interface for Statistics that describe how network is operating
 */
public interface Statistics {

	/**
	 * Get an unmodifiable {@link java.util.List} of all metrics that are registered with this object.
	 *
	 * Even though the returned {@code List} is unmodifiable, it may change. An {@link java.util.Iterator}
	 * of this list ignores concurrent modifications, therefore no special actions to deal with
	 * this situation have to be taken. But when a new {@code Iterator} is created, it will contain the changes.
	 *
	 * @return the list of metrics
	 */
	List<Metric> getMetrics();

	/**
	 * An app can call this to record how often it plans to save statistics to disk. This statistic isn't
	 * actually used by the system, but if all the stats are written out, then this will be one of them, so
	 * it will be recorded with the rest.
	 *
	 * @param writePeriod
	 * 		number of milliseconds between writes
	 * @deprecated The interval is specified in {@link com.swirlds.common.internal.SettingsCommon}
	 */
	@Deprecated(forRemoval = true)
	void setStatsWritePeriod(int writePeriod);

	/**
	 * Returns an array of available statistic entries that describe how the network is currently operating.
	 * Each statistic entry is an array of strings:
	 * <ul>
	 * <li>name - used to retrieve the statistics. And useful for showing concise tables</li>
	 * <li>description - a short description of what the statistic means</li>
	 * <li>format - a default format string, such as "%8.3f" for an 3-digit double with 3 decimal
	 * places</li>
	 * </ul>
	 *
	 * @return the array of statistic entries.
	 * @deprecated To get the information stored in the array, use {@link Statistics#getMetrics()}
	 */
	@Deprecated(forRemoval = true)
	String[][] getAvailableStats();

	/**
	 * Get the number of different statistics that can be retrieved by getStat().
	 *
	 * @return number of different statistics that can be retrieved by getStat().
	 * @deprecated To get the number of metrics, use {@link Statistics#getMetrics()}
	 */
	@Deprecated(forRemoval = true)
	int getNumStats();

	/**
	 * Returns a statistic about how the network is running, given its name. The names can be obtained with
	 * getAvailableStats. Most statistics are doubles. If the statistic is an integer, then it is casted to
	 * a double. If the statistic is a string, then -1.0 is returned. The actual type of a statistic can be
	 * seen from the last letter of the default formatter getAvailableStats()[index][2].
	 *
	 * @param statName
	 * 		the name of the statistic (call getAvailableStats to see the possible choices)
	 * @return the statistic
	 * @deprecated To get the value, use {@link Metric#get(Metric.ValueType)} with {@link Metric.ValueType#VALUE}
	 */
	@Deprecated(forRemoval = true)
	double getStat(String statName);

	/**
	 * Returns a statistic about how the network is running, given its index in the array returned by
	 * getAvailableStats. Most statistics are doubles. If the statistic is an integer, then it is casted to
	 * a double. If the statistic is a string, then -1.0 is returned. The actual type of a statistic can be
	 * seen from the last letter of the default formatter getAvailableStats()[index][2].
	 *
	 * @param index
	 * 		index of the statistic in the list returned by getAvailableStats
	 * @return the statistic
	 * @deprecated To get the value, use {@link Metric#get(Metric.ValueType)} with {@link Metric.ValueType#VALUE}
	 */
	@Deprecated(forRemoval = true)
	double getStat(int index);


	/**
	 * Returns a string representation of a statistic about how the network is running, given its name. The
	 * names can be obtained with getAvailableStats. The statistic is converted to a String using the
	 * default format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param statName
	 * 		the name of the statistic (call getAvailableStats to see the possible choices)
	 * @return the statistic converted to a string
	 * @deprecated To get the value, use {@link Metric#get(Metric.ValueType)} with {@link Metric.ValueType#VALUE}
	 */
	@Deprecated(forRemoval = true)
	String getStatString(String statName);

	/**
	 * Get the index number of a statistic, which can be passed to methods like getStat(). It is equivalent
	 * (and often easier) to just pass the name directly to getStat(). But this method also is provided, for
	 * completeness.
	 *
	 * @param statName
	 * 		the name of the statistic
	 * @return the index, or -1 if that name doesn't match any statistic
	 * @deprecated In the future, it will be possible to add and remove metrics during runtime. Looking them
	 *             up by index will not be supported from that point on.
	 */
	@Deprecated(forRemoval = true)
	int getStatIndex(String statName);

	/**
	 * Returns a string representation of a statistic about how the network is running, given its index in
	 * the array returned by getAvailableStats. The statistic is converted to a String using the default
	 * format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param index
	 * 		index of the statistic in the array returned by getAvailableStats
	 * @return the statistic converted to a string
	 * @deprecated To get the value of a {@link Metric}, use {@link Metric#get(Metric.ValueType)}
	 */
	@Deprecated(forRemoval = true)
	String getStatString(int index);

	/**
	 * Returns a string representation of a statistic category.
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic category
	 * @deprecated To get the category of a {@link Metric}, use {@link Metric#getName()}
	 */
	@Deprecated(forRemoval = true)
	String getStatCategory(int index);

	/**
	 * Returns a string representation of a statistic name
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic name
	 * @deprecated To get the name of a {@link Metric}, use {@link Metric#getName()}
	 */
	@Deprecated(forRemoval = true)
	String getName(int index);
}
