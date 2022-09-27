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

package com.swirlds.common.metrics.writer;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metric.ValueType;
import com.swirlds.common.statistics.internal.AbstractStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * This implementation of {@link MetricWriter} writes the current CSV-format. It is called "legacy", because
 * we plan to replace the CSV-format with something that is closer to the CSV standard.
 * <p>
 * The {@code LegacyCsvWriter} can be configured with the following settings:
 * <dl>
 *     <dt>csvOutputFolder</dt>
 *     <dd>The folder where the CSV-file is stored</dd>
 *
 *     <dt>csvFileName</dt>
 *     <dd>The filename of the generated CSV-file. If this setting is not set, no CSV-file is generated.</dd>
 *
 *     <dt>csvAppend</dt>
 *     <dd>If {@code true} and the file exists, new data is appended. Otherwise a new file is created.</dd>
 *
 *     <dt>showInternalStats</dt>
 *     <dd>If {@code true}, also settings with the category "internal" will be written to file</dd>
 *
 *     <dt>verboseStatistics</dt>
 *     <dd>If {@code true}, also secondary values (e.g. minimum and maximum) are written to the CSV-file</dd>
 * </dl>
 */
public class LegacyCsvWriter implements MetricWriter {

	private static final Logger LOGGER = LogManager.getLogger(LegacyCsvWriter.class);

	/**
	 * category contains this substring should not be expanded even Settings.verboseStatistics is true
	 */
	private static final String EXCLUDE_CATEGORY = "info";

	private static final Set<ValueType> SUPPORTED_TYPES = EnumSet.of(
			ValueType.VALUE,
			ValueType.COUNTER,
			ValueType.MIN,
			ValueType.MAX,
			ValueType.STD_DEV
	);

	/**
	 * path and filename of the .csv file to write to
	 */
	private final Path csvFilePath;

	/**
	 * if true all statistics, including those with category "internal", are written to the CSV file
	 * */
	private final boolean showInternalStats;

	/**
	 * Constructor of a {@code LegacyCsvWriter}
	 *
	 * @param csvFilePath path to the CSV-file
	 */
	public LegacyCsvWriter(final Path csvFilePath) {
		this.csvFilePath = csvFilePath;
		this.showInternalStats = SettingsCommon.showInternalStats;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void prepareFile(final List<Metric> metrics) throws IOException {
		LOGGER.info(
				STARTUP.getMarker(),
				"CsvWriter: Initializing statistics output in CSV format [ csvOutputFolder = '{}', csvFileName = '{}' ]",
				csvFilePath.getParent(),
				csvFilePath.getFileName()
		);

		// eventually filter out internal metrics
		final List<Metric> filteredMetrics = metrics.stream().filter(this::shouldWrite).toList();

		// create parent folder, if it does not exist
		ensureFolderExists();

		if (SettingsCommon.csvAppend && Files.exists(csvFilePath)) {
			// make sure last line of previous test was ended, and a blank line is inserted between tests.
			Files.writeString(csvFilePath, "\n\n", StandardOpenOption.APPEND);
		} else {
			// if csvAppend is off, or it is on but the file does not exist, write the definitions and the headings.
			// otherwise, they will already be there, so we can skip it
			final ContentBuilder builder = new ContentBuilder();
			// add the definitions at the top
			builder.addCell("filename:").addCell(csvFilePath).newRow();

			// add descriptions
			for (final Metric metric : filteredMetrics) {
				builder.addCell(metric.getName() + ":").addCell(metric.getDescription()).newRow();
			}
			
			// add empty row
			builder.newRow();

			// add rows with categories and names
			addHeaderRows(builder, filteredMetrics);

			// write to file
			Files.writeString(csvFilePath, builder.toString(), CREATE, TRUNCATE_EXISTING);
		}
	}

	// Add two rows, one with all categories, the other with all names
	private static void addHeaderRows(final ContentBuilder builder, final List<Metric> metrics) {
		final List<String> categories = new ArrayList<>();
		final List<String> names = new ArrayList<>();
		for (final Metric metric : metrics) {
			// Check, if we also want to write secondary values (e.g. minimum and maximum)
			if (SettingsCommon.verboseStatistics && !metric.getCategory().contains(EXCLUDE_CATEGORY)) {
				// Add category and name for all supported value-types
				addAllSupportedTypes(categories, names, metric);
			} else {
				// Only main value needs to be added
				categories.add(metric.getCategory());
				names.add(metric.getName());
			}
		}
		builder.addCell("").addCell("").addCells(categories).newRow(); // indent by two columns
		builder.addCell("").addCell("").addCells(names).newRow(); // indent by two columns
	}

	// Add category and name for all supported value-types
	private static void addAllSupportedTypes(
			final List<String> categories,
			final List<String> names,
			final Metric metric
	) {
		for (final ValueType metricType : metric.getValueTypes()) {
			categories.add(metric.getCategory());
			switch (metricType) {
				case VALUE, COUNTER -> names.add(metric.getName());
				case MAX -> names.add(metric.getName() + "Max");
				case MIN -> names.add(metric.getName() + "Min");
				case STD_DEV -> names.add(metric.getName() + "Std");
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void writeMetrics(final List<Snapshot> snapshots) throws IOException {
		// eventually filter out internal metrics
		final List<Snapshot> filteredSnapshots =
				snapshots.stream().filter(snapshot -> shouldWrite(snapshot.getMetric())).toList();

		final ContentBuilder builder = new ContentBuilder();

		// add two empty columns
		builder.addCell("").addCell("");

		// extract values
		for (final Snapshot snapshot : filteredSnapshots) {
			if (SettingsCommon.verboseStatistics && !snapshot.getMetric().getCategory().contains(EXCLUDE_CATEGORY)) {
				// add all supported value-types
				snapshot.getEntries().stream()
						.filter(entry -> SUPPORTED_TYPES.contains(entry.getValueType()))
						.forEach(entry -> builder.addCell(format(snapshot.getMetric().getFormat(), entry.getValue())));
			} else {
				// add only main value
				final List<Snapshot.SnapshotValue> entries = snapshot.getEntries();
				final Object value = entries.size() == 1 ? entries.get(0).getValue()
						: entries.stream()
						.filter(entry -> entry.getValueType() == ValueType.VALUE)
						.findAny()
						.map(Snapshot.SnapshotValue::getValue)
						.orElse(null);

				builder.addCell(format(snapshot.getMetric().getFormat(), value));
			}
		}

		// write to file
		builder.newRow();
		Files.writeString(csvFilePath, builder.toString(), APPEND);
	}

	// Format the given value according to the given format
	private static String format(final String format, final Object value) {
		try {
			return String.format(Locale.US, format, value);
		} catch (final IllegalFormatException e) {
			LOGGER.error(EXCEPTION.getMarker(), "", e);
		}
		return "";
	}

	// Returns false, if a Metric is internal and internal metrics should not be written
	private boolean shouldWrite(final Metric metric) {
		return showInternalStats || !metric.getCategory().equals(AbstractStatistics.INTERNAL_CATEGORY);
	}

	// Ensure that the parent folder specified by {@link #csvFilePath} exists and if not create it recursively.
	private void ensureFolderExists() throws IOException {
		final Path parentFolder = csvFilePath.getParent();

		if (!Files.exists(parentFolder)) {
			LOGGER.debug(
					STARTUP.getMarker(),
					"CsvWriter: Creating the metrics folder [ folder = '{}' ]",
					parentFolder
			);
			Files.createDirectories(parentFolder);

		} else {
			LOGGER.debug(
					STARTUP.getMarker(),
					"CsvWriter: Using the existing metrics folder [ folder = '{}' ]",
					parentFolder
			);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "LegacyCsvWriter{csvFilePath=" + csvFilePath + '}';
	}

	// Collects cells for one or more rows in the CSV-file. Handles all formatting.
	private static class ContentBuilder {

		private final StringBuilder builder = new StringBuilder();

		// add a list of cells
		private ContentBuilder addCells(final List<?> cells) {
			for (final Object cell : cells) {
				addCell(cell);
			}
			return this;
		}

		// add a single cell and format it
		private ContentBuilder addCell(final Object cell) {
			builder.append(Objects.toString(cell).trim().replace(",", ""))
					.append(',');
			return this;
		}

		// finish a row
		private void newRow() {
			builder.append('\n');
		}

		// convert the collected content to a String
		@Override
		public String toString() {
			return builder.toString();
		}
	}
}
