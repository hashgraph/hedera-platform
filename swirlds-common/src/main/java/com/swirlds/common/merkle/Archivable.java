/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.merkle;

import com.swirlds.common.merkle.exceptions.ArchivedException;

/**
 * Some merkle data structures such as FCMap maintain special internal metadata that are used to satisfy queries.
 * These special data structures may have large memory footprints.
 * <p>
 * An object that is archivable can prune away these optional data structures. After being archived,
 * such an object will continue to hold data in the merkle tree, but will no longer keep metadata
 * for satisfying queries. Queries that are unsupported after an archive will throw an
 * {@link com.swirlds.common.merkle.exceptions.ArchivedException ArchivedException}.
 */
public interface Archivable {

	/**
	 * Archive this object. Can cause data structures required for queries to be garbage collected.
	 * Some methods in this class may throw an
	 * {@link com.swirlds.common.merkle.exceptions.ArchivedException ArchivedException} if called after an archive.
	 * <p>
	 * Some objects that implement the Archivable interface don't actually require archiving.
	 * These objects may treat calls to archive() as a no-op and may always advertise themselves
	 * as not being archived.
	 */
	void archive();

	/**
	 * Check if this object has been archived and has thrown away metadata required to satisfy some forms of queries.
	 * <p>
	 * Internal nodes that have descendants that are archived are not required to return true when this method is
	 * called unless there are query methods directly against that internal node that are required to throw
	 * an exception after archive has completed.
	 *
	 * @return true if this object has been archived
	 */
	default boolean isArchived() {
		// Some objects that implement the Archivable interface don't actually require archiving.
		// These objects may treat calls to archive() as a no-op and may always advertise themselves
		// as not being archived.
		return false;
	}

	/**
	 * Throw an exception if this object has been archived.
	 *
	 * @throws ArchivedException
	 * 		if this object has been archived
	 */
	default void throwIfArchived() {
		if (isArchived()) {
			throw new ArchivedException("operation can not be performed after this object has been archived");
		}
	}
}
