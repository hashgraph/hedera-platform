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
 *
 */
package com.swirlds.common.statistics.internal;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.Statistics;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * An abstract class that can be extended to create a container for statistics.
 */
public abstract class AbstractStatistics implements Statistics {

	private static final Logger log = LogManager.getLogger(AbstractStatistics.class);
	private static final Marker ERROR = MarkerManager.getMarker("EXCEPTION");

	public static final String INFO_CATEGORY = "platform.info";
	public static final String CATEGORY = "platform";
	public static final String PING_CATEGORY = "ping";
	public static final String BPSS_CATEGORY = "bpss";
	public static final String INTERNAL_CATEGORY = "internal";
	public static final String DATABASE_CATEGORY = "database";

	/**
	 * change this to true to print JavaDoc documentation on available stats to console (do this once each
	 * time the code changes, and copy and past it above, then run JavaDoc)
	 */
	protected static boolean printStats = false;

	/**
	 * statistics are logged every this many milliseconds (not used by platform, but can be set by apps)
	 */
	protected int statsWritePeriod = 3000;

	/**
	 * map an index of a statistic to its StatEntry
	 */
	protected Map<Integer, StatEntry> index2entry = new HashMap<>();

	/**
	 * map a StatEntry of a statistic to its index
	 */
	protected Map<StatEntry, Integer> entry2index = new HashMap<>();

	/**
	 * map the name of a statistic to its StatEntry
	 */
	protected Map<String, StatEntry> name2entry = new HashMap<>();

	/**
	 * for each stat (maybe skipping "internal" categories) is {name, description, format}
	 */
	private String[][] allStatEntries;

	/**
	 * descriptions of all the statistics
	 */
	protected StatEntry[] statEntries;

	/**
	 * what is the width if the stats expands
	 */
	private static final int STATS_EXPAND_WIDTH = 4;

	/**
	 * category contains this substring should not be expanded even Settings.verboseStatistics is true
	 */
	private static final String EXCLUDE_CATEGORY = "info";

	/**
	 * pre-Calculated after init so don't have keeps recalculating
	 */
	private int expandableCount;

	/**
	 * Instantiate an object to hold a collection of statistics. If getStatEntriesArray() returns null when this
	 * constructor calls it,then call setupStatEntries() later, after ensuring getStatEntriesArray will return non-null.
	 */
	protected AbstractStatistics() {
		statEntries = getStatEntriesArray();
		setUpStatEntries();
	}

	/**
	 * Set up info related to the statistics. This is called by the constructor, which accesses the field [statEntries].
	 * If a class extends AbstractStatistics and it assigns to the [statEntries] field after calling super(),
	 * then it will need to call setupStatEntries() at the end, to do the setup again.
	 */
	protected void setUpStatEntries() {
		if (statEntries != null) { //if it exists, call it now. Otherwise, the child class must call setUpStatEntries
			for (final StatEntry stat : statEntries) {
				if (stat.init != null) {
					stat.buffered = stat.init.apply(SettingsCommon.halfLife);
				}
			}
			initStatEntries(SettingsCommon.showInternalStats);
			if (printStats) {
				printStats = false;
				printAvailableStats();
			}
		}
	}

	/**
	 * reset all the Speedometer and RunningAverage objects with a half life of Platform.halfLife
	 */
	public void resetAllSpeedometers() {
		for (final StatEntry stat : statEntries) {
			if (stat.reset != null) {
				stat.reset.accept(SettingsCommon.halfLife);
			} else if (stat.buffered != null) {
				stat.buffered.reset(SettingsCommon.halfLife);
			}
		}
	}

	/**
	 * An app can call this to record how often it plans to save statistics to disk. This statistic isn't
	 * actually used by the system, but if all the stats are written out, then this will be one of them, so
	 * it will be recorded with the rest.
	 *
	 * @param writePeriod
	 * 		number of milliseconds between writes
	 */
	@Override
	public void setStatsWritePeriod(final int writePeriod) {
		this.statsWritePeriod = writePeriod;
	}

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
	@Override
	public String[][] getAvailableStats() {
		return allStatEntries;
	}

	/**
	 * Get the number of different statistics that can be retrieved by getStat().
	 *
	 * @return number of different statistics that can be retrieved by getStat().
	 */
	@Override
	public int getNumStats() {
		return getAvailableStats().length;
	}

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
	@Override
	public double getStat(final String statName) {
		final StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return -1.0;
		}
		final Object s = stat.statsStringSupplier.get();
		if (s instanceof Number) {
			return (Double) s;
		}
		return -1.0;
	}

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
	@Override
	public double getStat(final int index) {
		final StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return -1.0;
		}
		final Object s = stat.statsStringSupplier.get();
		if (s instanceof Number) {
			return (Double) s;
		}
		return -1.0;
	}

	/**
	 * return a StatsBuffer for the entire history of the given statistic, or null if there is none
	 *
	 * @param statName
	 * 		the name of the statistic to get
	 * @return the StatsBuffer, or null if there is no statistic for that name, or there is one but it
	 * 		doesn't keep a history
	 */
	public StatsBuffer getAllHistory(final String statName) {
		final StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return null;
		}
		final StatsBuffered buf = stat.buffered;
		if (buf == null) {
			return null;
		}
		return buf.getAllHistory();
	}

	/**
	 * return a StatsBuffer for the recent history of the given statistic, or null if there is none
	 *
	 * @param statName
	 * 		the name of the statistic to get
	 * @return the StatsBuffer, or null if there is no statistic for that name, or there is one but it
	 * 		doesn't keep a history
	 */
	public StatsBuffer getRecentHistory(final String statName) {
		final StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return null;
		}
		final StatsBuffered buf = stat.buffered;
		if (buf == null) {
			return null;
		}
		return buf.getRecentHistory();
	}

	/**
	 * return a StatsBuffer for the entire history of the given statistic (skipping the first
	 * Settings.statsSkipSeconds seconds), or null if there is none
	 *
	 * @param index
	 * 		the index of the statistic to get
	 * @return the StatsBuffer, or null if there is none
	 */
	public StatsBuffer getAllHistory(final int index) {
		final StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return null;
		}
		final StatsBuffered buf = stat.buffered;
		if (buf == null) {
			return null;
		}
		return buf.getAllHistory();
	}

	/**
	 * return a StatsBuffered for the recent history of the given statistic, or null if there is none
	 *
	 * @param index
	 * 		the index of the statistic to get
	 * @return the StatsBuffered, or null if there is none
	 */
	public StatsBuffer getRecentHistory(final int index) {
		final StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return null;
		}
		final StatsBuffered buf = stat.buffered;
		if (buf == null) {
			return null;
		}
		return buf.getRecentHistory();
	}

	/**
	 * Returns a string representation of a statistic about how the network is running, given its name. The
	 * names can be obtained with getAvailableStats. The statistic is converted to a String using the
	 * default format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param statName
	 * 		the name of the statistic (call getAvailableStats to see the possible choices)
	 * @return the statistic converted to a string
	 */
	@Override
	public String getStatString(final String statName) {
		final StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return "";
		}
		return getStatString(stat);
	}

	/**
	 * Get the index number of a statistic, which can be passed to methods like getStat(). It is equivalent
	 * (and often easier) to just pass the name directly to getStat(). But this method also is provided, for
	 * completeness.
	 *
	 * @param statName
	 * 		the name of the statistic
	 * @return the index, or -1 if that name doesn't match any statistic
	 */
	@Override
	public int getStatIndex(final String statName) {
		final StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return -1;
		}
		final Integer index = entry2index.get(stat);
		if (index == null) {
			return -1;
		}
		return index;
	}

	/**
	 * Returns a string representation of a statistic about how the network is running, given its index in
	 * the array returned by getAvailableStats. The statistic is converted to a String using the default
	 * format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param index
	 * 		index of the statistic in the array returned by getAvailableStats
	 * @param reset
	 * 		whether to reset the value when returning
	 * @return the statistic converted to a string
	 */
	public String getStatString(final int index, final boolean reset) {
		final StatEntry stat = index2entry.get(index);
		try {
			if (stat == null) {
				return "";
			}
			final Supplier<Object> supplier = reset ? stat.resetStatsStringSupplier : stat.statsStringSupplier;
			return String.format(Locale.US, stat.format, supplier.get());
		} catch (final IllegalFormatException e) {
			log.error(EXCEPTION.getMarker(), "unable to compute string for {}", stat.name, e);
		}
		return "";
	}

	/**
	 * Same as {@link #getStatString(int, boolean)} with reset=false
	 */
	@Override
	public String getStatString(final int index) {
		return getStatString(index, false);
	}

	/**
	 * Returns a string representation of a statistic minimal value in history.
	 * The statistic is converted to a String using the default
	 * format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic converted to a string
	 */
	public String getMinString(final int index) {
		try {
			final StatEntry stat = index2entry.get(index);
			if (stat == null || stat.buffered == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.buffered.getMin());
		} catch (final IllegalFormatException e) {
			log.error(ERROR, "", e);
		}
		return "";
	}

	/**
	 * Returns a string representation of a statistic maximum value in history.
	 * The statistic is converted to a String using the default
	 * format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic converted to a string
	 */
	public String getMaxString(final int index) {
		try {
			final StatEntry stat = index2entry.get(index);
			if (stat == null || stat.buffered == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.buffered.getMax());
		} catch (final IllegalFormatException e) {
			log.error(ERROR, "", e);
		}
		return "";
	}

	/**
	 * Returns a string representation of a statistic standard deviation value in history.
	 * The statistic is converted to a String using the default
	 * format (right justified, spaces on the left), or "" if the name is invalid
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic converted to a string
	 */
	public String getStdDevString(final int index) {
		try {
			final StatEntry stat = index2entry.get(index);
			if (stat == null || stat.buffered == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.buffered.getStdDev());
		} catch (final IllegalFormatException e) {
			log.error(ERROR, "", e);
		}
		return "";
	}


	/**
	 * Returns a string representation of a statistic category.
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic category
	 */
	@Override
	public String getStatCategory(final int index) {
		final StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return "";
		}
		return stat.category;
	}

	/**
	 * Returns a string representation of a statistic name
	 *
	 * @param index
	 * 		index of the statistic in the array returned by index2entry
	 * @return the statistic name
	 */
	@Override
	public String getName(final int index) {
		final StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return "";
		}
		return stat.name;
	}

	/**
	 * Returns a string representation of a statistic about how the network is running, given its StatEntry.
	 * The statistic is converted to a String using the default format (right justified, spaces on the
	 * left).
	 *
	 * @param stat
	 * 		the StatEntry describing this statistic
	 * @return the statistic converted to a string
	 */
	private static String getStatString(final StatEntry stat) {
		try {
			return String.format(Locale.US, stat.format, stat.statsStringSupplier.get());
		} catch (final IllegalFormatException e) {
			log.error(ERROR, "", e);
		}
		return "";
	}

	/**
	 * Print the available statistics to standard out in a format suitable for copying and pasting into the
	 * JavaDoc for this class. This should be re-run and the documentation updated whenever this information
	 * is changed.
	 */
	public void printAvailableStats() {
		CommonUtils.tellUserConsole("* <ul>");
		for (int i = 0; i < statEntries.length; i++) {
			CommonUtils.tellUserConsole("* <li><b>" + statEntries[i].name
					+ "</b> - " + statEntries[i].desc);
		}
		CommonUtils.tellUserConsole("* </ul>");
	}

	/**
	 * initialize statEntries and the various maps etc related to it
	 *
	 * @param includeInternal
	 * 		true if all stats should be included, including category "internal"
	 */
	public void initStatEntries(final boolean includeInternal) {
		final ArrayList<StatEntry> toShow = new ArrayList<>();
		Arrays.sort(statEntries,
				(a, b) -> a.name.toLowerCase().compareTo(b.name.toLowerCase()));
		int index = 0;
		for (int i = 0; i < statEntries.length; i++) {
			final String category = statEntries[i].category;
			if (includeInternal || !category.equals(INTERNAL_CATEGORY)) {

				toShow.add(statEntries[i]);
				name2entry.put(statEntries[i].name, statEntries[i]);
				index2entry.put(index, statEntries[i]);
				entry2index.put(statEntries[i], index);
				index++;
			}
		}
		allStatEntries = new String[toShow.size()][3];
		for (int i = 0; i < toShow.size(); i++) {

			final StatEntry stat = toShow.get(i);
			allStatEntries[i][0] = stat.name;
			allStatEntries[i][1] = stat.desc;
			allStatEntries[i][2] = stat.format;
		}

		// count how many category can be expanded
		expandableCount = (int) Arrays.stream(statEntries)
				.filter(
						f -> !f.category.contains(EXCLUDE_CATEGORY)
				).count();
	}

	/**
	 * once a second, update all the statistics that aren't updated by any other class
	 */
	public abstract void updateOthers();

	/**
	 * create all the data for the statEntries array
	 *
	 * @return the newly instantiated array
	 */
	public abstract StatEntry[] getStatEntriesArray();

	/**
	 * Get a big enough string array for preparing names, categories, etc
	 *
	 * @return empty string array or the size of string array the same as
	 * 		number of entries
	 */
	private String[] getStringArray() {
		if (allStatEntries == null || allStatEntries.length == 0) {
			return new String[0];
		} else {
			// if Settings.verboseStatistics true, all expandable items appear expandableCount * STATS_EXPAND_WIDTH
			// non expandable items appear (statEntries.length-expandableCount)
			return SettingsCommon.verboseStatistics ?
					new String[expandableCount * STATS_EXPAND_WIDTH + allStatEntries.length - expandableCount]
					: new String[allStatEntries.length];
		}
	}


	/**
	 * Get category names of all entries
	 *
	 * @return String array of category names
	 */
	public String[] getCategoryStrings() {

		final String[] result = getStringArray();

		if (allStatEntries != null && name2entry != null) {
			int stringCount = 0;
			for (int index = 0; index < allStatEntries.length; index++) {
				final StatEntry entry = name2entry.get(allStatEntries[index][0]);
				final String category = entry.category;

				if (SettingsCommon.verboseStatistics && (!category.contains(EXCLUDE_CATEGORY))) { //info category no
					// need
					// to be expanded
					for (int repeat = 0; repeat < STATS_EXPAND_WIDTH; repeat++) {
						result[stringCount] = entry.category;
						stringCount++;
					}
				} else {
					result[stringCount] = entry.category;
					stringCount++;
				}
			}
		}
		return result;
	}


	/**
	 * Get stats names of all entries
	 *
	 * @param allowExpand
	 * 		allow expand if Settings.verboseStatistics is true
	 * @return String array of stats names
	 */
	public String[] getNameStrings(final boolean allowExpand) {
		final String[] result;
		if (allowExpand) {
			result = getStringArray();
		} else if (allStatEntries != null) {
			result = new String[allStatEntries.length];
		} else {
			result = new String[0];
		}

		if (allStatEntries != null && name2entry != null) {
			int stringCount = 0;

			for (int index = 0; index < allStatEntries.length; index++) {

				final StatEntry entry = name2entry.get(allStatEntries[index][0]);
				final String category = entry.category;

				if (allowExpand && SettingsCommon.verboseStatistics && (!category.contains(
						EXCLUDE_CATEGORY))) { //info category no need to be
					// expanded

					result[stringCount] = entry.name;
					stringCount++;
					result[stringCount] = entry.name + "Max";
					stringCount++;
					result[stringCount] = entry.name + "Min";
					stringCount++;
					result[stringCount] = entry.name + "Std";
					stringCount++;

				} else {
					result[stringCount] = entry.name;
					stringCount++;
				}
			}
		}
		return result;
	}

	/**
	 * Get stats values of all entries
	 *
	 * @return String array of stats values
	 */
	public String[] getResetValueStrings() {

		final String[] result = getStringArray();

		if (allStatEntries != null && name2entry != null) {
			int stringCount = 0;
			for (int index = 0; index < allStatEntries.length; index++) {
				final StatEntry entry = name2entry.get(allStatEntries[index][0]);
				final String category = entry.category;

				if (SettingsCommon.verboseStatistics && (!category.contains(EXCLUDE_CATEGORY))) { //info category no
					// need
					// to be expanded
					result[stringCount] = this.getStatString(index);
					stringCount++;
					result[stringCount] = this.getMaxString(index);
					stringCount++;
					result[stringCount] = this.getMinString(index);
					stringCount++;
					result[stringCount] = this.getStdDevString(index);
					stringCount++;
				} else {
					result[stringCount] = this.getStatString(index, true);
					stringCount++;
				}
			}
		}
		return result;
	}

	/**
	 * Get stats descriptions of all entries
	 *
	 * @return String array of stats descriptions
	 */
	public String[] getDescriptionStrings() {
		final String[] result = statEntries != null ? new String[statEntries.length] : new String[0];
		if (statEntries != null) {
			for (int index = 0; index < statEntries.length; index++) {
				final StatEntry entry = statEntries[index];
				result[index] = entry.desc;
			}
		}
		return result;
	}

}
