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

package com.swirlds.platform;

import com.swirlds.common.internal.AbstractStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;

import static com.swirlds.logging.LogMarker.EXCEPTION;

class CsvWriter implements Runnable {
	/** the app is run by this */
	private AbstractPlatform platform;
	/** path and filename of the .csv file to write to */
	private String path;
	/** number of milliseconds between writes to the log file */
	private int writePeriod = 3000;

	SimpleDateFormat format = new SimpleDateFormat("HH.mm.ss.SSS");
	/**
	 * use this for all logging
	 */
	private static final Logger log = LogManager.getLogger(CsvWriter.class);

	private static final Marker ERROR = MarkerManager.getMarker(EXCEPTION.name());

	CsvWriter(final AbstractPlatform platform, final String selfId, final String name) {
		super();
		this.platform = platform;
		path = System.getProperty("user.dir") + File.separator + name
				+ selfId + ".csv";
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
	private boolean shouldWrite(AbstractStatistics statsObj, int pos) {
		boolean result = true;
		//comment out the next two lines to get all the statistics
//		result = result && !statsObj.getCategoryStrings()[pos].equals(Statistics.PING_CATEGORY);
//		result = result && !statsObj.getCategoryStrings()[pos].equals(Statistics.BPSS_CATEGORY);
		return result;
	}

	@Override
	public void run() {
		boolean displayDBStats = platform.displayDBStats();
		int arraysize = (displayDBStats) ? 4 : 3;
		AbstractStatistics[] statisticsObjs = new AbstractStatistics[arraysize];
		statisticsObjs[0] = platform.getStats();
		statisticsObjs[1] = CryptoStatistics.getInstance();
		statisticsObjs[2] = platform.getAppStats();
		if (displayDBStats) {
			statisticsObjs[3] = platform.getDBStatistics();
		}

		if (!Settings.csvAppend) {
			// if we're not appending to the CSV, erase the old file, if any
			eraseFile();
		}

		// if csvAppend is off, or it's on but the file doesn't exist, write the definitions and the headings.
		// otherwise, they will already be there so we can skip it
		if (!Settings.csvAppend || !fileExists()) {
			// write the definitions at the top (name is stats[i][0], description is stats[i][1])
			write(String.format("%14s: ", "filename"));
			write(String.format("%s", path));
			newline();

			// write descriptions
			for (final AbstractStatistics abstractStatistics : statisticsObjs) {
				writeDescriptions(abstractStatistics);
			}
			newline();

			//write category names
			write("");// indent by two columns
			write("");
			for (final AbstractStatistics obj : statisticsObjs) {
				writeCategories(obj);
			}
			newline();


			// write the column headings again
			write("");// indent by two columns
			write("");
			for (final AbstractStatistics statisticsObj : statisticsObjs) {
				writeStatNames(statisticsObj);
			}
			newline();
		} else { // make sure last line of previous test was ended, and a blank line is inserted between tests.
			newline();
			newline();
		}

		while (true) { // keep logging forever
			try {
				// write a row of numbers
				write("");
				write("");
				for (int i = 0; i < statisticsObjs.length; i++) {
					writeStatValues(statisticsObjs[i]);
				}
				newline();
				Thread.sleep(writePeriod); // add new rows infrequently
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

	}

	/** Erase the existing file (if one exists) */
	private void eraseFile() {
		// erase file in current directory
		try (BufferedWriter file = new BufferedWriter(
				new FileWriter(path, false))) {
			file.write("");
		} catch (IOException e) {
			log.error(ERROR, "CSVWriter:", e);
		}
	}

	private boolean fileExists() {
		File file = new File(path);
		return file.exists();
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
	private void write(String message, boolean newline) {
		// create or append to file in current directory
		try (BufferedWriter file = new BufferedWriter(
				new FileWriter(path, true))) {
			if (newline) {
				file.write("\n");
			} else {
				file.write(message.trim().replaceAll(",", "") + ",");
			}
		} catch (IOException e) {
			log.error(ERROR, "CSVWriter:", e);
		}
	}

	/**
	 * Same as write(String message, boolean newline), except it does not start a new line after it.
	 *
	 * @param message
	 * 		the String to write
	 */
	private void write(String message) {
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
	private void writeDescriptions(AbstractStatistics statsObj) {
		String[] names = statsObj.getNameStrings(false);
		String[] descriptions = statsObj.getDescriptionStrings();

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
	private void writeCategories(AbstractStatistics statsObj) {
		String[] categories = statsObj.getCategoryStrings();
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
	private void writeStatNames(AbstractStatistics statsObj) {
		String[] names = statsObj.getNameStrings(true);
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
	private void writeStatValues(AbstractStatistics statsObj) {
		String[] values = statsObj.getValueStrings();
		for (int i = 0; i < values.length; i++) {
			if (shouldWrite(statsObj, i)) {
				write(values[i]);
			}
		}
	}
}
