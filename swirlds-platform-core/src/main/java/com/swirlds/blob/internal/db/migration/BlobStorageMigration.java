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

package com.swirlds.blob.internal.db.migration;


import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlobStorageMigration extends Migration {

	public static final String[] COMMON_LOCATIONS = {
			"classpath:/com/swirlds/blob/internal/db/migration/common"
	};


	public static final String[] POSTGRESQL_LOCATIONS = {
			"classpath:/com/swirlds/blob/internal/db/migration/postgres"
	};

	public static final String[] SCHEMAS = {
			"PUBLIC"
	};

	public static final String PH_BLOB_TYPE = "PH_BLOB_TYPE";
	public static final String PH_FS_HASH_TYPE = "PH_FS_HASH_TYPE";
	public static final String PH_FS_META_TYPE = "PH_FS_META_TYPE";
	public static final String PH_BYTE_TYPE = "PH_BYTE_TYPE";
	public static final String PH_BOOL_FALSE = "PH_BOOL_FALSE";

	public BlobStorageMigration(final DataSource dataSource) {
		super(dataSource);

		if (isPostgreSql()) {
			SCHEMAS[0] = "public";
		}
	}

	public BlobStorageMigration(final DataSource dataSource, final String installedBy) {
		super(dataSource, installedBy);

		if (isPostgreSql()) {
			SCHEMAS[0] = "public";
		}
	}

	@Override
	protected String[] getLocations() {
		final List<String> locations = new ArrayList<>(Arrays.asList(COMMON_LOCATIONS));

		if (isPostgreSql()) {
			locations.addAll(Arrays.asList(POSTGRESQL_LOCATIONS));
		}

		return locations.toArray(new String[0]);
	}

	@Override
	protected String[] getSchemas() {
		return SCHEMAS;
	}

	@Override
	protected Map<String, String> getPlaceholders() {
		final Map<String, String> placeholders = new HashMap<>();

		placeholders.put(PH_FS_HASH_TYPE, isPostgreSql() ? "BYTEA" : "BINARY(48)");
		placeholders.put(PH_FS_META_TYPE, isPostgreSql() ? "BYTEA" : "VARBINARY(100)");
		placeholders.put(PH_BLOB_TYPE, isPostgreSql() ? "BYTEA" : "BLOB");
		placeholders.put(PH_BYTE_TYPE, isPostgreSql() ? "SMALLINT" : "TINYINT");
		placeholders.put(PH_BOOL_FALSE, isPostgreSql() ? "FALSE" : "0");

		return placeholders;
	}

	@Override
	protected File[] getAdditionalJars() {
		return null;
	}
}
