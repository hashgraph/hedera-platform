/*
 * Copyright (C) 2016-2021 Hedera Hashgraph, LLC
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
package com.swirlds.platform.test.chatter.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;

/** Misc. utility methods used by gossip simulation. */
public final class GossipSimulationUtils {

    private static final String HEADER_STRING = "-------------------";

    private static final List<String> DATA_UNITS = List.of("bytes", "kb", "mb", "gb", "tb");

    private GossipSimulationUtils() {}

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
     * Write a header in standard format.
     *
     * @param header the content of the header
     */
    public static void printHeader(final String header) {
        System.out.println(HEADER_STRING + " " + header + " " + HEADER_STRING);
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
     * Convert a double to a formatted string with a limited number of decimal places.
     *
     * @param value the value to format
     * @param decimalPlaces the maximum number of decimal places
     * @return a formatted string
     */
    public static String roundDecimal(final Double value, final int decimalPlaces) {
        if (value.isNaN()) {
            return "NaN";
        }
        return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.HALF_UP).toString();
    }

    /**
     * Format a number of bytes into a human-readable string with appropriate units. Does not use
     * decimals.
     *
     * @return a formatted string
     */
    public static String formatByteQuantity(final long bytes) {
        double quantity = bytes;
        int unit = 0;

        while (quantity > 1024 * 10 && unit + 1 < DATA_UNITS.size()) {
            quantity /= 1024;
            unit++;
        }

        return commaSeparatedValue(Math.round(quantity)) + " " + DATA_UNITS.get(unit);
    }
}
