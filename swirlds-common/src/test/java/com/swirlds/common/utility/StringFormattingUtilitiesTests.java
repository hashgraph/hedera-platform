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

import static com.swirlds.common.utility.StringFormattingUtilities.formattedList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StringFormattingUtilities Tests")
class StringFormattingUtilitiesTests {

    @Test
    @DisplayName("Empty String Test")
    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    void emptyStringTest() {
        final List<Integer> data = List.of();

        assertEquals("", formattedList(data.iterator()));
        assertEquals("", formattedList(data.iterator(), ", "));
        assertEquals("", formattedList(data.iterator(), " - "));

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator());
            assertEquals("", sb.toString());
        }

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator(), ", ");
            assertEquals("", sb.toString());
        }

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator(), " - ");
            assertEquals("", sb.toString());
        }
    }

    @Test
    @DisplayName("Single Element Test")
    void singleElementTest() {
        final List<Integer> data = List.of(1234);

        assertEquals("1234", formattedList(data.iterator()));
        assertEquals("1234", formattedList(data.iterator(), ", "));
        assertEquals("1234", formattedList(data.iterator(), " - "));

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator());
            assertEquals("1234", sb.toString());
        }

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator(), ", ");
            assertEquals("1234", sb.toString());
        }

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator(), " - ");
            assertEquals("1234", sb.toString());
        }
    }

    @Test
    @DisplayName("Multiple Elements Test")
    void multipleElementsTest() {
        final List<Integer> data = List.of(1234, 5678, 9012, 3456, 7890);

        assertEquals("1234, 5678, 9012, 3456, 7890", formattedList(data.iterator()));
        assertEquals("1234, 5678, 9012, 3456, 7890", formattedList(data.iterator(), ", "));
        assertEquals("1234 - 5678 - 9012 - 3456 - 7890", formattedList(data.iterator(), " - "));

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator());
            assertEquals("1234, 5678, 9012, 3456, 7890", sb.toString());
        }

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator(), ", ");
            assertEquals("1234, 5678, 9012, 3456, 7890", sb.toString());
        }

        {
            final StringBuilder sb = new StringBuilder();
            formattedList(sb, data.iterator(), " - ");
            assertEquals("1234 - 5678 - 9012 - 3456 - 7890", sb.toString());
        }
    }
}
