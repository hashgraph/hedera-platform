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
package com.swirlds.fchashmap.test;

import com.swirlds.fchashmap.FCHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** This class is used to encapsulate an {@link FCHashMap} and a sequence of copies of that map. */
public class FCHashMapSeries<K, V> implements Iterable<AccessibleFCHashMap<K, V>> {

    private final Map<Long, AccessibleFCHashMap<K, V>> copies;
    private final List<Long> copiesEligibleForDeletion;

    private long latestVersion;

    /** Create a new series of FCHashMaps with a single mutable copy. */
    public FCHashMapSeries() {
        copies = new HashMap<>();
        final AccessibleFCHashMap<K, V> map = new AccessibleFCHashMap<>();
        latestVersion = map.version();
        copies.put(map.version(), map);

        copiesEligibleForDeletion = new ArrayList<>();
    }

    /**
     * Get a copy at a given version.
     *
     * @throws IllegalStateException if the given version does not exist.
     */
    public AccessibleFCHashMap<K, V> get(final long version) {
        final AccessibleFCHashMap<K, V> map = copies.get(version);
        if (map == null) {
            throw new IllegalStateException("No copy at version " + version + " exists");
        }
        return map;
    }

    /** Get an iterator over all undeleted copies. No order guarantees are provided. */
    @Override
    public Iterator<AccessibleFCHashMap<K, V>> iterator() {
        return copies.values().iterator();
    }

    /**
     * Get the expected number of copies that have not been garbage collected.
     *
     * @return the expected number of copies that haven't yet been garbage collected
     */
    public int copyCount() {
        long lowestUnreleasedVersion = Integer.MAX_VALUE;
        long highestUnreleasedVersion = Integer.MIN_VALUE;

        for (final long copyIndex : copies.keySet()) {
            if (copyIndex < lowestUnreleasedVersion) {
                lowestUnreleasedVersion = copyIndex;
            }
            if (copyIndex > highestUnreleasedVersion) {
                highestUnreleasedVersion = copyIndex;
            }
        }

        return (int) (highestUnreleasedVersion - lowestUnreleasedVersion + 1);
    }

    /** Get the latest (mutable) copy of the map. */
    public AccessibleFCHashMap<K, V> getLatest() {
        if (copies.size() == 0) {
            throw new IllegalStateException("all copies have been released");
        }
        return get(latestVersion);
    }

    /**
     * Get the version number of the oldest unreleased copy.
     *
     * @return the oldest version
     */
    public long getOldestVersion() {
        if (copiesEligibleForDeletion.isEmpty()) {
            return latestVersion;
        }

        return copies.get(copiesEligibleForDeletion.get(0)).version();
    }

    /** Make a fast copy of the map. */
    public void copy() {
        final AccessibleFCHashMap<K, V> previousMutableCopy = getLatest();
        final AccessibleFCHashMap<K, V> copy = previousMutableCopy.copy();
        copiesEligibleForDeletion.add(previousMutableCopy.version());
        latestVersion = copy.version();
        copies.put(copy.version(), copy);
    }

    /** Get the number of copies, including the mutable copy. */
    public int getNumberOfCopies() {
        return copies.size();
    }

    /**
     * Delete a random copy. Will not delete the mutable copy.
     *
     * @param random a source of randomness
     */
    public void delete(final Random random) {
        if (copiesEligibleForDeletion.size() > 1) {
            delete(
                    copiesEligibleForDeletion.get(
                            random.nextInt(copiesEligibleForDeletion.size() - 1)));
        } else {
            delete(copiesEligibleForDeletion.get(0));
        }
    }

    /** Delete a given version of the map. Can not be used ot delete the latest version. */
    public void delete(final long version) {
        if (version == latestVersion) {
            throw new IllegalStateException(
                    "this method can not be used to delete the mutable copy");
        }
        get(version).release();

        final Iterator<Long> versionIterator = copiesEligibleForDeletion.iterator();
        while (versionIterator.hasNext()) {
            if (versionIterator.next() == version) {
                versionIterator.remove();
                break;
            }
        }
        copies.remove(version);
    }

    /**
     * Delete the mutable copy of the map.
     *
     * @throws IllegalStateException if there are any immutable copies of the map that haven't yet
     *     been deleted
     */
    public void deleteMutableCopy() {
        if (!copiesEligibleForDeletion.isEmpty()) {
            throw new IllegalStateException("all immutable copies must first be deleted");
        }
        get(latestVersion).release();
        copies.remove(latestVersion);
    }
}
