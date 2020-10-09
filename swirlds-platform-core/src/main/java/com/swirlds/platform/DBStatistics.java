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

import com.swirlds.blob.BinaryObjectException;
import com.swirlds.blob.internal.db.BlobStoragePipeline;
import com.swirlds.blob.internal.db.DbManager;
import com.swirlds.common.internal.AbstractStatistics;
import com.swirlds.common.StatEntry;

import java.sql.SQLException;
import java.util.concurrent.locks.StampedLock;

class DBStatistics extends AbstractStatistics {

	private static final StampedLock lock = new StampedLock();

	long currentDBSize = 0;
	long currentDBBinaryObjects = 0;
	long currentDBLargeObjects;

	@Override
	public void updateOthers() {
		currentDBSize = getCurrentDBSize(false, "");
		currentDBBinaryObjects = getCurrentDBSize(true, "binary_objects");
		currentDBLargeObjects = getCurrentDBSize(true, "pg_largeobject");
	}

	@Override
	public StatEntry[] getStatEntriesArray() {
		return new StatEntry[] {//
				new StatEntry(
						DATABASE_CATEGORY,
						"DBSize",
						"current size of the database",
						"%d",
						null,
						null,
						null,
						() -> currentDBSize),
				new StatEntry(
						DATABASE_CATEGORY,
						"DBBinaryObjectsSize",
						"current size of the database table 'binary_objects",
						"%d",
						null,
						null,
						null,
						() -> currentDBBinaryObjects),
				new StatEntry(
						DATABASE_CATEGORY,
						"DBlargeObjectsSize",
						"current size of the database table 'binary_objects",
						"%d",
						null,
						null,
						null,
						() -> currentDBLargeObjects),
		};
	}

	long getCurrentDBSize(boolean isTable, String tableName) {
		long readLock = lock.readLock();
		long returnValue;

		try (BlobStoragePipeline pipeline = DbManager.getInstance().blob()) {

			pipeline.withTransaction();

			if (isTable) {
				returnValue = pipeline.getTableDbSize(tableName);
			} else {
				returnValue = pipeline.getDbSize();
			}

			pipeline.commit(); // locks aren't released if commit if not performed
			return returnValue;
		} catch (SQLException e) {
			throw new BinaryObjectException("Failed to get BinaryObject", e);
		} finally {
			lock.unlock(readLock);
		}
	}
}
