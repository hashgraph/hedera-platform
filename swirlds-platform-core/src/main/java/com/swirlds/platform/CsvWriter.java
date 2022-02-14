/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform;

import com.swirlds.common.NodeId;
import com.swirlds.common.statistics.internal.AbstractStatistics;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

/**
 * The background thread responsible for logging all platform statistic values to the CSV file format.
 */
class CsvWriter implements Runnable {
	/**
	 * use this for all logging
	 */
	private static final Logger log = LogManager.getLogger(CsvWriter.class);

	/**
	 * the default frequency at which values should be written if the configured write period is out of range
	 */
	public static final int DEFAULT_WRITE_PERIOD = 3000;

	/** the {@link com.swirlds.common.Platform} instance that owns this {@link CsvWriter} */
	private final AbstractPlatform platform;

	/** path and filename of the .csv file to write to */
	private final File csvFilePath;

	/** number of milliseconds between writes to the log file */
	private final int writePeriod;

	/** if true all statistics, including those with category "internal", are written to the CSV file */
	private final boolean showInternalStats;

	/**
	 * if true, then statistics with the category "database" are written to the CSV file
	 */
	private final boolean showDbStats;

	/**
	 * if true, then the file will be appended to continuing from the last record written; otherwise the file will be
	 * truncated and headers written
	 */
	private final boolean append;

	/**
	 * Constructs a new CsvWriter instance with the provided settings.
	 *
	 * @param platform
	 * 		the {@link SwirldsPlatform} instance with which this CsvWriter is associated
	 * @param selfId
	 * 		the identifier of the network node to which this CsvWriter instance is associated
	 * @param folderName
	 * 		the optional folder path to which the CSV files should be written, may be {@code null} or an empty-string
	 * @param fileNamePrefix
	 * 		the filename prefix to use when naming the CSV file
	 * @param writePeriod
	 * 		the frequency, in milliseconds, at which values are written to the statistics CSV file
	 * @param append
	 * 		if true then any existing file will be opened in append mode; otherwise any existing file will be
	 * 		overwritten
	 * @param showInternalStats
	 * 		if true all statistics with the "internal" category will be included in the CSV file output
	 * @param showDbStats
	 * 		if true then statistics with the "database" category will be included in the CSV file output
	 */
	public CsvWriter(final AbstractPlatform platform, final NodeId selfId, final String folderName,
			final String fileNamePrefix, final int writePeriod, final boolean append, final boolean showInternalStats,
			final boolean showDbStats) {

		if (fileNamePrefix == null || fileNamePrefix.isBlank()) {
			throw new IllegalArgumentException("fileNamePrefix");
		}

		if (selfId == null) {
			throw new IllegalArgumentException("selfId");
		}


		final File folderPath = new File(
				(folderName == null || folderName.isBlank()) ? System.getProperty("user.dir") : folderName);

		this.platform = platform;
		this.csvFilePath = new File(folderPath, String.format("%s%s.csv", fileNamePrefix, selfId)).getAbsoluteFile();
		this.writePeriod = (writePeriod > 0) ? writePeriod : DEFAULT_WRITE_PERIOD;
		this.showInternalStats = showInternalStats;
		this.showDbStats = showDbStats;
		this.append = append;
	}

	public File getCsvFilePath() {
		return csvFilePath;
	}

	public int getWritePeriod() {
		return writePeriod;
	}

	/**
	 * Should the statistic in position i within object statsObj be written to the CSV file?
	 * Update this method to filter out certain categories of stats. Or to implement more
	 * complex filters. Or just make it always return true to output all the statistics.
	 *
	 * @param statsObj
	 * 		the AbstractStatistics object holding the statistic
	 * @param pos
	 * 		which statistic within that object
	 * @return true if this statistic should be written
	 */
	private boolean shouldWrite(final AbstractStatistics statsObj, final int pos) {
		boolean result = true;

		// Only write internal stats if the Settings.showInternalStats are enabled
		if (!showInternalStats) {
			result = !statsObj.getCategoryStrings()[pos].equals(AbstractStatistics.INTERNAL_CATEGORY);
		}

		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		final AbstractStatistics[] statisticsObjs = new AbstractStatistics[(showDbStats) ? 4 : 3];
		statisticsObjs[0] = platform.getStats();
		statisticsObjs[1] = CryptoStatistics.getInstance();
		statisticsObjs[2] = platform.getAppStats();

		if (showDbStats) {
			statisticsObjs[3] = platform.getDBStatistics();
		}

		log.info(STARTUP.getMarker(),
				"CsvWriter: Initializing statistics output in CSV format [ writePeriod = '{}', csvOutputFolder = " +
						"'{}', csvFileName = '{}' ]",
				writePeriod, csvFilePath.getParentFile(), csvFilePath.getName());

		ensureFolderExists();

		if (!append) {
			// if we're not appending to the CSV, erase the old file, if any
			eraseFile();
		}

		// if csvAppend is off, or it's on but the file doesn't exist, write the definitions and the headings.
		// otherwise, they will already be there so we can skip it
		if (!append || !fileExists()) {
			// write the definitions at the top (name is stats[i][0], description is stats[i][1])
			write(String.format("%14s: ", "filename"));
			write(String.format("%s", csvFilePath));
			newline();

			// write descriptions
			for (final AbstractStatistics abstractStatistics : statisticsObjs) {
				if (abstractStatistics != null) {
					writeDescriptions(abstractStatistics);
				}
			}
			newline();

			//write category names
			write("");// indent by two columns
			write("");
			for (final AbstractStatistics obj : statisticsObjs) {
				if (obj != null) {
					writeCategories(obj);
				}
			}
			newline();


			// write the column headings again
			write("");// indent by two columns
			write("");
			for (final AbstractStatistics statisticsObj : statisticsObjs) {
				if (statisticsObj != null) {
					writeStatNames(statisticsObj);
				}
			}
			newline();
		} else { // make sure last line of previous test was ended, and a blank line is inserted between tests.
			newline();
			newline();
		}

		try {
			while (true) { // keep logging forever
				// write a row of numbers
				write("");
				write("");
				for (final AbstractStatistics statisticsObj : statisticsObjs) {
					writeStatValues(statisticsObj);
				}
				newline();
				Thread.sleep(writePeriod); // add new rows infrequently
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}

	}

	/** Erase the existing file (if one exists) */
	private void eraseFile() {
		// erase file in current directory
		try (final BufferedWriter file = new BufferedWriter(
				new FileWriter(csvFilePath, false))) {
			file.write("");
		} catch (final IOException ex) {
			log.error(EXCEPTION.getMarker(),
					"CsvWriter: Failed to erase the CSV file, no stats will be logged [ file = '{}' ] ",
					csvFilePath, ex);
		}
	}

	private boolean fileExists() {
		return csvFilePath.exists();
	}

	/**
	 * Ensure that the parent folder specified by {@link #csvFilePath} exists and if not create it recursively.
	 */
	private void ensureFolderExists() {
		final File parentFolder = csvFilePath.getParentFile();

		if (!parentFolder.exists()) {
			log.debug(LogMarker.STARTUP.getMarker(), "CsvWriter: Creating the stats folder [ folder = '{}' ]",
					parentFolder);
			if (!parentFolder.mkdirs()) {
				log.warn(EXCEPTION.getMarker(),
						"CsvWriter: Unable to create the stats folder, no stats will be logged [ folder = '{}' ]",
						parentFolder);
			}
		} else {
			log.debug(LogMarker.STARTUP.getMarker(), "CsvWriter: Using the existing stats folder [ folder = '{}' ]",
					parentFolder);
		}
	}

	/**
	 * Write a message to the log file, skip a line after writing, if newline is true. This method opens the file at the
	 * start and closes it at the end, to deconflict with any other process trying to read the same file. For example,
	 * this app could run headless on a server, and an FTP session could download the log file, and the file it received
	 * would have only complete log messages, never half a message.
	 * <p>
	 * The file is created if it doesn't exist. It will be named "[fileName][selfId].csv", with the number incrementing
	 * for each member currently running on the local machine, if there is more than one. The location is the
	 * "current" directory. If run from a shell script, it will be the current folder that the shell script has.
	 * If run from Eclipse, it will be at the top of the project folder. If there is a console, it prints the
	 * location there. If not, it can be found by searching the file system for "[fileName][selfId].csv".
	 *
	 * @param message
	 * 		the String to write
	 * @param newline
	 * 		true if a new line should be started after this one
	 */
	private void write(final String message, final boolean newline) {
		// create or append to file in current directory
		try (final BufferedWriter file = new BufferedWriter(
				new FileWriter(csvFilePath, true))) {
			if (newline) {
				file.write("\n");
			} else {
				file.write(message.trim().replaceAll(",", "") + ",");
			}
		} catch (final IOException ex) {
			log.error(EXCEPTION.getMarker(),
					"CsvWriter: Unable to write to the CSV file, no stats will be logged [ file = '{}' ]",
					csvFilePath, ex);
		}
	}

	/**
	 * Same as write(String message, boolean newline), except it does not start a new line after it.
	 *
	 * @param message
	 * 		the String to write
	 */
	private void write(final String message) {
		write(message, false);
	}

	/** Start the next line */
	private void newline() {
		write("", true);
	}

	/**
	 * Write statistic name and descriptions
	 *
	 * @param statsObj
	 * 		statistics instance hold an array of statistic entry
	 */
	private void writeDescriptions(final AbstractStatistics statsObj) {
		final String[] names = statsObj.getNameStrings(false);
		final String[] descriptions = statsObj.getDescriptionStrings();

		for (int i = 0; i < names.length; i++) {
			if (shouldWrite(statsObj, i)) {
				write(String.format("%14s: ", names[i]));
				write(descriptions[i]);
				newline();
			}
		}
	}

	/**
	 * Write statistic entry's category
	 *
	 * @param statsObj
	 * 		statistics instance hold an array of statistic entry
	 */
	private void writeCategories(final AbstractStatistics statsObj) {
		final String[] categories = statsObj.getCategoryStrings();
		for (int i = 0; i < categories.length; i++) {
			if (shouldWrite(statsObj, i)) {
				write(categories[i]);
			}
		}
	}


	/**
	 * Write statistic entry's name
	 *
	 * @param statsObj
	 * 		statistics instance hold an array of statistic entry
	 */
	private void writeStatNames(final AbstractStatistics statsObj) {
		final String[] names = statsObj.getNameStrings(true);
		for (int i = 0; i < names.length; i++) {
			if (shouldWrite(statsObj, i)) {
				write(names[i]);
			}
		}
	}

	/**
	 * Write statistic current values
	 *
	 * @param statsObj
	 * 		statistics instance hold an array of statistic entry
	 */
	private void writeStatValues(final AbstractStatistics statsObj) {
		if (statsObj == null) {
			return;
		}

		final String[] values = statsObj.getResetValueStrings();
		for (int i = 0; i < values.length; i++) {
			if (shouldWrite(statsObj, i)) {
				write(values[i]);
			}
		}
	}
}
