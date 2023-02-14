/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.utility;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** Utility class for formatting and printing an ASCII table. */
public class TextTable {

    private static final char PADDING = ' ';
    private static final char CROSS_JUNCTION = '┼';
    private static final char CROSS_HEADER_JUNCTION = '╇';
    private static final char LEFT_JUNCTION = '┠';
    private static final char LEFT_HEADER_JUNCTION = '┣';
    private static final char RIGHT_JUNCTION = '┨';
    private static final char RIGHT_HEADER_JUNCTION = '┫';
    private static final char BOTTOM_JUNCTION = '┷';
    private static final char TOP_JUNCTION = '┯';
    private static final char THICK_TOP_JUNCTION = '┳';
    private static final char TOP_LEFT_CORNER = '┏';
    private static final char TOP_RIGHT_CORNER = '┓';
    private static final char BOTTOM_LEFT_CORNER = '┗';
    private static final char BOTTOM_RIGHT_CORNER = '┛';
    private static final char HORIZONTAL_BAR = '─';
    private static final char THICK_HORIZONTAL_BAR = '━';
    private static final char VERTICAL_BAR = '│';
    private static final char THICK_VERTICAL_BAR = '┃';
    private static final char NEWLINE = '\n';

    private final String title;
    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    /**
     * Create a new text table.
     *
     * @param title the title of the table
     * @param headers a list of table headers
     */
    public TextTable(final String title, final Object... headers) {

        this.title = title;

        if (headers != null) {
            for (final Object header : headers) {
                this.headers.add(header.toString());
            }
        }
    }

    /**
     * Add a string multiple times to a string builder.
     *
     * @param sb the string builder to add to
     * @param string the string to repeat
     * @param count the number of times to repeat
     */
    public static void addRepeated(final StringBuilder sb, final String string, final int count) {
        sb.append(String.valueOf(string).repeat(Math.max(0, count)));
    }

    /**
     * Add a char multiple times to a string builder.
     *
     * @param sb the string builder to add to
     * @param c the char to repeat
     * @param count the number of times to repeat
     */
    public static void addRepeated(final StringBuilder sb, final char c, final int count) {
        sb.append(String.valueOf(c).repeat(Math.max(0, count)));
    }

    /**
     * Add a string to a string builder with padding. String is on the left, padding on the right.
     *
     * @param sb the string builder to add to
     * @param string the string to be added
     * @param padding the char to be used as padding
     * @param width the total desired width of the string plus padding
     */
    public static void leftPad(
            final StringBuilder sb, final String string, final char padding, final int width) {
        sb.append(string);
        addRepeated(sb, padding, width - string.length());
    }

    /**
     * Add a string to a string builder with padding. String is on the right, padding on the left.
     *
     * @param sb the string builder to add to
     * @param string the string to be added
     * @param padding the char to be used as padding
     * @param width the total desired width of the string plus padding
     */
    public static void rightPad(
            final StringBuilder sb, final String string, final char padding, final int width) {
        addRepeated(sb, padding, width - string.length());
        sb.append(string);
    }

    /**
     * Add a string to a string builder with padding. String is in the center, padding is split
     * between left and right.
     *
     * @param sb the string builder to add to
     * @param string the string to be added
     * @param padding the char to be used as padding
     * @param width the total desired width of the string plus padding
     */
    public static void centerPad(
            final StringBuilder sb, final String string, final char padding, final int width) {

        final int leftPadding = (width - string.length()) / 2;
        final int rightPadding = width - string.length() - leftPadding;

        addRepeated(sb, padding, leftPadding);
        sb.append(string);
        addRepeated(sb, padding, rightPadding);
    }

    /**
     * Format a number into a comma-separated string.
     *
     * @param value the value to format
     * @return the value separated by commas
     */
    public static String commaSeparatedValue(final long value) {
        if (value == 0) {
            return "0";
        }

        final StringBuilder sb = new StringBuilder();

        long runningValue = value;
        if (value < 0) {
            sb.append("-");
            runningValue *= -1;
        }

        final List<Integer> parts = new LinkedList<>();

        while (runningValue > 0) {
            parts.add(0, (int) (runningValue % 1000));
            runningValue /= 1000;
        }

        for (int index = 0; index < parts.size(); index++) {

            if (index == 0) {
                sb.append(parts.get(index));
            } else {
                final String digits = Integer.toString(parts.get(index));
                rightPad(sb, digits, '0', 3);
            }

            if (index + 1 < parts.size()) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    /**
     * Add a row to the table.
     *
     * @param row a single row
     * @return this object
     */
    public TextTable addRow(final Object... row) {
        if (row != null) {
            final List<String> rowString = new ArrayList<>();

            for (final Object o : row) {
                rowString.add(o.toString());
            }

            rows.add(rowString);
        }

        return this;
    }

    /**
     * Expand column widths to fit a given row.
     *
     * @param row the row that needs to be fitted into the table
     */
    private static void expandColumnWidthsForRow(
            final List<Integer> columnWidths, final List<String> row) {
        for (int column = 0; column < row.size(); column++) {
            final int columnWidth = row.get(column).length();
            if (columnWidths.size() <= column) {
                columnWidths.add(columnWidth);
            } else {
                columnWidths.set(column, Math.max(columnWidths.get(column), columnWidth));
            }
        }
    }

    /**
     * Compute the width for each column.
     *
     * @return a list of widths indexed by column
     */
    private List<Integer> computeColumnWidths() {

        final List<Integer> columnWidths = new ArrayList<>();

        expandColumnWidthsForRow(columnWidths, headers);
        for (final List<String> row : rows) {
            expandColumnWidthsForRow(columnWidths, row);
        }

        return columnWidths;
    }

    /** Generate the top of the table. */
    private static void generateTop(
            final StringBuilder sb, final int columnWidthSum, final int columnCount) {
        sb.append(TOP_LEFT_CORNER);
        addRepeated(sb, THICK_HORIZONTAL_BAR, columnWidthSum + columnCount * 3 - 1);
        sb.append(TOP_RIGHT_CORNER).append(NEWLINE);
    }

    /** Generate the line containing the title. */
    private void generateTitleLine(
            final StringBuilder sb, final int columnWidthSum, final int columnCount) {
        sb.append(THICK_VERTICAL_BAR);
        sb.append(PADDING);
        centerPad(sb, title, PADDING, columnWidthSum + columnCount * 3 - 3);
        sb.append(PADDING);
        sb.append(THICK_VERTICAL_BAR).append(NEWLINE);
    }

    /** Generate the line between the title and the headers. */
    private static void generateLineBelowTitle(
            final StringBuilder sb, final List<Integer> columnWidths) {
        sb.append(LEFT_HEADER_JUNCTION);
        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            addRepeated(sb, THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2);
            if (columnIndex + 1 < columnWidths.size()) {
                sb.append(THICK_TOP_JUNCTION);
            }
        }
        sb.append(RIGHT_HEADER_JUNCTION).append(NEWLINE);
    }

    /** Generate the line below the title if there are no headers. */
    private static void generateLineBelowTitleNoHeaders(
            final StringBuilder sb, final List<Integer> columnWidths) {
        sb.append(LEFT_HEADER_JUNCTION);
        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            addRepeated(sb, THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2);
            if (columnIndex + 1 < columnWidths.size()) {
                sb.append(TOP_JUNCTION);
            }
        }
        sb.append(RIGHT_HEADER_JUNCTION).append(NEWLINE);
    }

    /** Generate the line containing the headers. */
    private void generateHeaderRow(final StringBuilder sb, final List<Integer> columnWidths) {
        sb.append(THICK_VERTICAL_BAR);
        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            sb.append(PADDING);

            final String header = headers.size() > columnIndex ? headers.get(columnIndex) : "";
            leftPad(sb, header, PADDING, columnWidths.get(columnIndex));
            sb.append(PADDING);
            sb.append(THICK_VERTICAL_BAR);
        }
        sb.append(NEWLINE);
    }

    /** Generate the line below the headers and above the rest of the table. */
    private static void generateLineBelowHeaders(
            final StringBuilder sb, final List<Integer> columnWidths) {
        sb.append(LEFT_HEADER_JUNCTION);

        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            addRepeated(sb, THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2);
            if (columnIndex + 1 < columnWidths.size()) {
                sb.append(CROSS_HEADER_JUNCTION);
            }
        }
        sb.append(RIGHT_HEADER_JUNCTION).append(NEWLINE);
    }

    /** Generate a row containing column data. */
    private static void generateDataRow(
            final StringBuilder sb, final List<String> row, final List<Integer> columnWidths) {

        sb.append(THICK_VERTICAL_BAR);
        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            sb.append(PADDING);
            final String value = columnIndex < row.size() ? row.get(columnIndex) : "";
            leftPad(sb, value, PADDING, columnWidths.get(columnIndex));
            sb.append(PADDING);
            if (columnIndex + 1 < columnWidths.size()) {
                sb.append(VERTICAL_BAR);
            }
        }
        sb.append(THICK_VERTICAL_BAR);
        sb.append(NEWLINE);
    }

    /** Generate the line below a row containing data. */
    private static void generateLineBelowDataRow(
            final StringBuilder sb, final List<Integer> columnWidths) {
        sb.append(LEFT_JUNCTION);

        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            addRepeated(sb, HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2);
            if (columnIndex + 1 < columnWidths.size()) {
                sb.append(CROSS_JUNCTION);
            }
        }
        sb.append(RIGHT_JUNCTION).append(NEWLINE);
    }

    /** Generate the rows in the table. */
    private void generateRows(final StringBuilder sb, final List<Integer> columnWidths) {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            generateDataRow(sb, rows.get(rowIndex), columnWidths);

            // Line below row
            if (rowIndex + 1 < rows.size()) {
                generateLineBelowDataRow(sb, columnWidths);
            }
        }
    }

    /** Generate the last line in the table. */
    private static void generateBottomLine(
            final StringBuilder sb, final List<Integer> columnWidths) {
        sb.append(BOTTOM_LEFT_CORNER);

        for (int columnIndex = 0; columnIndex < columnWidths.size(); columnIndex++) {
            addRepeated(sb, THICK_HORIZONTAL_BAR, columnWidths.get(columnIndex) + 2);
            if (columnIndex + 1 < columnWidths.size()) {
                sb.append(BOTTOM_JUNCTION);
            }
        }

        sb.append(BOTTOM_RIGHT_CORNER);
    }

    /**
     * Add this table to a string builder.
     *
     * @param sb the string builder to add to
     */
    public void addToStringBuilder(final StringBuilder sb) {
        final List<Integer> columnWidths = computeColumnWidths();

        int columnWidthSum = 0;
        for (final int columnWidth : columnWidths) {
            columnWidthSum += columnWidth;
        }

        final int columnCount = columnWidths.size();

        final int minimumWidth = title.length() + columnCount * 3 - 3;
        if (columnWidthSum < minimumWidth) {
            // Title is too wide, expand a column to balance it out

            final int expansion = minimumWidth - columnWidthSum;
            columnWidthSum += expansion;
            final int lastIndex = columnWidths.size() - 1;
            columnWidths.set(lastIndex, columnWidths.get(lastIndex) + expansion);
        }

        generateTop(sb, columnWidthSum, columnCount);
        generateTitleLine(sb, columnWidthSum, columnCount);

        if (headers.isEmpty()) {
            generateLineBelowTitleNoHeaders(sb, columnWidths);
        } else {
            generateLineBelowTitle(sb, columnWidths);
            generateHeaderRow(sb, columnWidths);
            generateLineBelowHeaders(sb, columnWidths);
        }

        generateRows(sb, columnWidths);
        generateBottomLine(sb, columnWidths);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        addToStringBuilder(sb);

        return sb.toString();
    }
}
