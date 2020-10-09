/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

/**
 *
 */
package com.swirlds.common.internal;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.StatEntry;
import com.swirlds.common.Statistics;
import com.swirlds.common.StatsBuffered;
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

	/** statistics are logged every this many milliseconds (not used by platform, but can be set by apps) */
	protected int statsWritePeriod = 3000;
	/** map an index of a statistic to its StatEntry */
	protected Map<Integer, StatEntry> index2entry = new HashMap<Integer, StatEntry>();
	/** map a StatEntry of a statistic to its index */
	protected Map<StatEntry, Integer> entry2index = new HashMap<StatEntry, Integer>();
	/** map the name of a statistic to its StatEntry */
	protected Map<String, StatEntry> name2entry = new HashMap<String, StatEntry>();
	/** for each stat (maybe skipping "internal" categories) is {name, description, format} */
	String[][] allStatEntries;

	/** descriptions of all the statistics */
	public StatEntry[] statEntries;

	/** what is the width if the stats expands */
	static private final int STATS_EXPAND_WIDTH = 4;

	/** category contains this substring should not be expanded even Settings.verboseStatistics is true */
	static private final String EXCLUDE_CATEGORY = "info";

	/** pre-Calculated after init so don't have keeps recalculating */
	private int expandableCount;

	/**
	 * Instantiate an object to hold a collection of statistics. If getStatEntriesArray() returns null when this
	 * constructor calls it,then call setupStatEntries() later, after ensuring getStatEntriesArray will return non-null.
	 */
	public AbstractStatistics() {
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
			for (StatEntry stat : statEntries) {
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
		for (StatEntry stat : statEntries) {
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
	public void setStatsWritePeriod(int writePeriod) {
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
	public double getStat(String statName) {
		StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return -1.0;
		}
		Object s = stat.supplier.get();
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
	public double getStat(int index) {
		StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return -1.0;
		}
		Object s = stat.supplier.get();
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
	public StatsBuffer getAllHistory(String statName) {
		StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return null;
		}
		StatsBuffered buf = stat.buffered;
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
	public StatsBuffer getRecentHistory(String statName) {
		StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return null;
		}
		StatsBuffered buf = stat.buffered;
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
	public StatsBuffer getAllHistory(int index) {
		StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return null;
		}
		StatsBuffered buf = stat.buffered;
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
	public StatsBuffer getRecentHistory(int index) {
		StatEntry stat = index2entry.get(index);
		if (stat == null) {
			return null;
		}
		StatsBuffered buf = stat.buffered;
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
	public String getStatString(String statName) {
		StatEntry stat = name2entry.get(statName);
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
	public int getStatIndex(String statName) {
		StatEntry stat = name2entry.get(statName);
		if (stat == null) {
			return -1;
		}
		Integer index = entry2index.get(stat);
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
	 * @return the statistic converted to a string
	 */
	@Override
	public String getStatString(int index) {
		try {
			StatEntry stat = index2entry.get(index);
			if (stat == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.supplier.get());
		} catch (IllegalFormatException e) {
			log.error(ERROR, "", e);
		}
		return "";
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
	public String getMinString(int index) {
		try {
			StatEntry stat = index2entry.get(index);
			if (stat == null || stat.buffered == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.buffered.getMin());
		} catch (IllegalFormatException e) {
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
	public String getMaxString(int index) {
		try {
			StatEntry stat = index2entry.get(index);
			if (stat == null || stat.buffered == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.buffered.getMax());
		} catch (IllegalFormatException e) {
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
	public String getStdDevString(int index) {
		try {
			StatEntry stat = index2entry.get(index);
			if (stat == null || stat.buffered == null) {
				return "";
			}
			return String.format(Locale.US, stat.format, stat.buffered.getStdDev());
		} catch (IllegalFormatException e) {
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
	public String getStatCategory(int index) {
		StatEntry stat = index2entry.get(index);
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
	public String getName(int index) {
		StatEntry stat = index2entry.get(index);
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
	String getStatString(StatEntry stat) {
		try {
			return String.format(Locale.US, stat.format, stat.supplier.get());
		} catch (IllegalFormatException e) {
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
	public void initStatEntries(boolean includeInternal) {
		ArrayList<StatEntry> toShow = new ArrayList<>();
		Arrays.sort(statEntries,
				(a, b) -> a.name.toLowerCase().compareTo(b.name.toLowerCase()));
		int index = 0;
		for (int i = 0; i < statEntries.length; i++) {
			String category = statEntries[i].category;
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

			StatEntry stat = toShow.get(i);
			allStatEntries[i][0] = stat.name;
			allStatEntries[i][1] = stat.desc;
			allStatEntries[i][2] = stat.format;
		}

		// count how many category can be expanded
		expandableCount = (int) Arrays.stream(statEntries)
				.filter(
						(f) -> !f.category.contains(EXCLUDE_CATEGORY)
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

		String[] result = getStringArray();

		if (allStatEntries != null && name2entry != null) {
			int stringCount = 0;
			for (int index = 0; index < allStatEntries.length; index++) {
				StatEntry entry = name2entry.get(allStatEntries[index][0]);
				String category = entry.category;

				if (SettingsCommon.verboseStatistics && (!category.contains(EXCLUDE_CATEGORY))) { //info category no
					// need
					// to be expanded
					for (int repeat = 0; repeat < STATS_EXPAND_WIDTH; repeat++) {
						result[stringCount++] = entry.category;
					}
				} else {
					result[stringCount++] = entry.category;
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
	public String[] getNameStrings(boolean allowExpand) {
		String[] result = allowExpand ? getStringArray() :
				(allStatEntries != null ? new String[allStatEntries.length] : new String[0]);

		if (allStatEntries != null && name2entry != null) {
			int stringCount = 0;

			for (int index = 0; index < allStatEntries.length; index++) {

				StatEntry entry = name2entry.get(allStatEntries[index][0]);
				String category = entry.category;

				if (allowExpand && SettingsCommon.verboseStatistics && (!category.contains(
						EXCLUDE_CATEGORY))) { //info category no need to be
					// expanded

					result[stringCount++] = entry.name;
					result[stringCount++] = entry.name + "Max";
					result[stringCount++] = entry.name + "Min";
					result[stringCount++] = entry.name + "Std";

				} else {
					result[stringCount++] = entry.name;
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
	public String[] getValueStrings() {

		String[] result = getStringArray();

		if (allStatEntries != null && name2entry != null) {
			int stringCount = 0;
			for (int index = 0; index < allStatEntries.length; index++) {
				StatEntry entry = name2entry.get(allStatEntries[index][0]);
				String category = entry.category;

				if (SettingsCommon.verboseStatistics && (!category.contains(EXCLUDE_CATEGORY))) { //info category no
					// need
					// to be expanded
					result[stringCount++] = this.getStatString(index);
					result[stringCount++] = this.getMaxString(index);
					result[stringCount++] = this.getMinString(index);
					result[stringCount++] = this.getStdDevString(index);
				} else {
					result[stringCount++] = this.getStatString(index);
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
		String[] result = statEntries != null ? new String[statEntries.length] : new String[0];
		if (statEntries != null) {
			for (int index = 0; index < statEntries.length; index++) {
				StatEntry entry = statEntries[index];
				result[index] = entry.desc;
			}
		}
		return result;
	}

}
