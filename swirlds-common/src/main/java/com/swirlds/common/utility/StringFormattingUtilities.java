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

import java.util.Iterator;

/** Various utilities for formatting strings. */
public final class StringFormattingUtilities {

    private StringFormattingUtilities() {}

    /**
     * Write the provided string to the string builder, followed by a line separator.
     *
     * @param sb a string builder to write to
     * @param line the line to add
     */
    public static void addLine(final StringBuilder sb, final String line) {
        sb.append(line).append(System.lineSeparator());
    }

    /**
     * Write a comma separated list to a string builder.
     *
     * @param sb a string builder to write the list to
     * @param iterator the objects returned by this iterator will be written to the formatted list
     */
    public static void formattedList(final StringBuilder sb, final Iterator<?> iterator) {
        formattedList(sb, iterator, ", ");
    }

    /**
     * Write a formatted list to a string builder.
     *
     * @param sb a string builder to write the list to
     * @param iterator the objects returned by this iterator will be written to the formatted list
     * @param separator the element between list items, e.g. ", "
     */
    public static void formattedList(
            final StringBuilder sb, final Iterator<?> iterator, final String separator) {
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(separator);
            }
        }
    }

    /**
     * Build a comma separated list.
     *
     * @param iterator the objects returned by this iterator will be written to the formatted list
     * @return a comma separated list
     */
    public static String formattedList(final Iterator<?> iterator) {
        return formattedList(iterator, ", ");
    }

    /**
     * Build a delimiter separated list.
     *
     * @param iterator the objects returned by this iterator will be written to the formatted list
     * @param separator the element between list items, e.g. ", "
     * @return a comma separated list
     */
    public static String formattedList(final Iterator<?> iterator, final String separator) {
        final StringBuilder sb = new StringBuilder();
        formattedList(sb, iterator, separator);
        return sb.toString();
    }
}
