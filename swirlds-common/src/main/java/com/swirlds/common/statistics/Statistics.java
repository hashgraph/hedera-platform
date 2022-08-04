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

/**
 * An interface for Statistics that describe how network is operating
 */
public interface Statistics {

	/**
	 * An app can call this to record how often it plans to save statistics to disk. This statistic isn't
	 * actually used by the system, but if all the stats are written out, then this will be one of them, so
	 * it will be recorded with the rest.
	 *
	 * @param writePeriod
	 * 		number of milliseconds between writes
	 */
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
	 */
	String[][] getAvailableStats();

	/**
	 * Get the number of different statistics that can be retrieved by getStat().
	 *
	 * @return number of different statistics that can be retrieved by getStat().
	 */
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
	 */
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
	 */
	double getStat(int index);


	/**
	 * Returns a string representation of a statistic about how the network is running, given its name. The
	 * names can be obtained with getAvailableStats. The statistic is converted to a String using the
	 * default format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param statName
	 * 		the name of the statistic (call getAvailableStats to see the possible choices)
	 * @return the statistic converted to a string
	 */
	String getStatString(String statName);

	/**
	 * Get the index number of a statistic, which can be passed to methods like getStat(). It is equivalent
	 * (and often easier) to just pass the name directly to getStat(). But this method also is provided, for
	 * completeness.
	 *
	 * @param statName
	 * 		the name of the statistic
	 * @return the index, or -1 if that name doesn't match any statistic
	 */
	int getStatIndex(String statName);

	/**
	 * Returns a string representation of a statistic about how the network is running, given its index in
	 * the array returned by getAvailableStats. The statistic is converted to a String using the default
	 * format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param index
	 * 		index of the statistic in the array returned by getAvailableStats
	 * @return the statistic converted to a string
	 */
	String getStatString(int index);

	/**
	 * Returns a string representation of a statistic category.
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic category
	 */
	String getStatCategory(int index);

	/**
	 * Returns a string representation of a statistic name
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic name
	 */
	String getName(int index);


}
