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
package com.swirlds.platform.util.iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SkippingIteratorTest {

    private static final String SHOULD_HAVE_NEXT = "Iterator should have a next element.";
    private static final String SHOULD_NOT_HAVE_NEXT = "Iterator should not have a next element.";
    private static final String NEXT_ELEMENT_MISMATCH =
            "The element returned by next() is not correct.";
    private static final String NEXT_SHOULD_THROW =
            "next() should throw an exception when no more elements are " + "available.";

    private static final Set<Integer> NULL_SET = null;

    private static Stream<Arguments> skipParams() {
        return Stream.of(Arguments.of(Collections.emptySet()), Arguments.of(NULL_SET));
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @ParameterizedTest
    @MethodSource("skipParams")
    @DisplayName("SkippingIterator - no skipped indices")
    void testNoSkippedIndices(final Set<Integer> skipIndices) {
        final Integer[] array = {0, 1, 2, 3, 4, 5};

        final Iterator<Integer> iter = new SkippingIterator<>(array, skipIndices);

        for (final Integer i : array) {
            assertHasNext(iter);
            assertNextElement(i, iter);
        }
        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - some skipped indices")
    void testSomeSkippedIndices() {
        final Integer[] array = {0, 1, 2, 3, 4, 5};

        final Iterator<Integer> iter = new SkippingIterator<>(array, Set.of(2, 4));

        assertHasNext(iter);
        assertNextElement(0, iter);

        assertHasNext(iter);
        assertNextElement(1, iter);

        assertHasNext(iter);
        assertNextElement(3, iter);

        assertHasNext(iter);
        assertNextElement(5, iter);

        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - unordered skipped indices")
    void testSomeSkippedIndicesNotOrdered() {
        final Integer[] array = {0, 1, 2, 3, 4, 5};

        final Iterator<Integer> iter = new SkippingIterator<>(array, Set.of(4, 2));

        assertHasNext(iter);
        assertNextElement(0, iter);

        assertHasNext(iter);
        assertNextElement(1, iter);

        assertHasNext(iter);
        assertNextElement(3, iter);

        assertHasNext(iter);
        assertNextElement(5, iter);

        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - first index skipped")
    void testFirstIndexSkipped() {
        final Integer[] array = {0, 1, 2, 3, 4, 5};

        final Iterator<Integer> iter = new SkippingIterator<>(array, Set.of(0));

        for (int i = 1; i < array.length; i++) {
            assertHasNext(iter);
            assertNextElement(i, iter);
        }
        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - last index skipped")
    void testLastIndexSkipped() {
        final Integer[] array = {0, 1, 2, 3, 4, 5};

        final Iterator<Integer> iter = new SkippingIterator<>(array, Set.of(5));

        for (int i = 0; i < array.length - 1; i++) {
            assertHasNext(iter);
            assertNextElement(i, iter);
        }
        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - skip all indices")
    void testAllSkippedIndices() {
        final Integer[] array = {0, 1, 2, 3, 4, 5};

        final Iterator<Integer> iter = new SkippingIterator<>(array, Set.of(5, 1, 2, 3, 4, 0));

        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - null array")
    void testNullArray() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkippingIterator<>(null, Collections.emptySet()));
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - empty array")
    void testEmptyArray() {
        final Iterator<Integer> iter =
                new SkippingIterator<>(new Integer[0], Collections.emptySet());
        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestQualifierTags.MIN_ACCEPTED_TEST)
    @Tag(TestComponentTags.PLATFORM)
    @Test
    @DisplayName("SkippingIterator - skip invalid indices")
    void testSkipInvalidIndices() {
        final Integer[] array = {0, 1};

        final Iterator<Integer> iter = new SkippingIterator<>(array, Set.of(2, 3, 4, 5));

        assertHasNext(iter);
        assertNextElement(0, iter);

        assertHasNext(iter);
        assertNextElement(1, iter);

        assertDoesNotHaveNext(iter);
        assertHasNextThrows(iter);
    }

    private static void assertHasNext(final Iterator<Integer> iter) {
        assertTrue(iter.hasNext(), SHOULD_HAVE_NEXT);
    }

    private static void assertHasNextThrows(final Iterator<Integer> iter) {
        assertThrows(NoSuchElementException.class, iter::next, NEXT_SHOULD_THROW);
    }

    private static void assertDoesNotHaveNext(final Iterator<Integer> iter) {
        assertFalse(iter.hasNext(), SHOULD_NOT_HAVE_NEXT);
    }

    private static void assertNextElement(final Integer expected, final Iterator<Integer> iter) {
        assertEquals(expected, iter.next(), NEXT_ELEMENT_MISMATCH);
    }
}
