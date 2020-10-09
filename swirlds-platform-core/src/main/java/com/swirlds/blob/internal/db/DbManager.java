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

package com.swirlds.blob.internal.db;

import com.swirlds.blob.BinaryObjectException;
import com.swirlds.blob.internal.db.migration.BlobStorageMigration;
import com.swirlds.platform.Marshal;
import com.swirlds.platform.internal.DatabaseSettings;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static com.swirlds.blob.internal.Utilities.hikariDataSource;
import static com.swirlds.blob.internal.Utilities.pooledConnection;

/**
 * Main classes to handle access/control to the database which is used for {@link com.swirlds.blob.BinaryObject}.
 * The database used for BinaryObjects should be an isolated database with no other user data stored.
 */
public final class DbManager implements PipelineProvider {

	private static final String JDBC_URL_FORMAT = "jdbc:%s://%s:%d/%s";
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private static final Logger log = LogManager.getLogger(DbManager.class);

	private volatile static DbManager instance;

	private volatile HikariDataSource dataSource;

	private DbManager() {
	}

	public static synchronized void ensureTablesExist() {
		try (final BlobStorageMigration migration = new BlobStorageMigration(getInstance().getDataSource())) {
			migration.migrate();
		}
	}

	public static DbManager getInstance(final boolean skipMigration) {
		if (instance == null) {
			synchronized (DbManager.class) {
				if (instance == null) {
					instance = new DbManager();
					instance.initialize(skipMigration);
				}
			}
		}

		return instance;
	}

	public static DbManager getInstance() {
		return getInstance(false);
	}

	public static synchronized void purgeTables() {
		try (BlobStorageMigration migration = new BlobStorageMigration(getInstance().getDataSource())) {
			migration.clean();
			migration.migrate();
		}
	}

	/**
	 * This method truncates the following tables:
	 *
	 * <ul>
	 *     <li>binary_objects</li>
	 *     <li>pg_catalog.pg_largeobject</li>
	 *     <li>pg_catalog.pg_largeobject_metadata</li>
	 * </ul>
	 *
	 * Only for use when the configured database is exclusively used for binary object storage.
	 * <strong>DO NOT USE THIS METHOD IN PRODUCTION CODE</strong>
	 * The main usage of this method is for unit tests.
	 *
	 * @throws SQLException
	 * 		if a database access error occurs or this method is called on a closed connection
	 */
	public static synchronized void purgeData() throws SQLException {
		try (final Connection conn = acquire()) {
			conn.setAutoCommit(false);
			try (final Statement stmt = conn.createStatement()) {
				stmt.executeUpdate("truncate table binary_objects");
				stmt.executeUpdate("delete from pg_catalog.pg_largeobject where 1=1");
				stmt.executeUpdate("delete from pg_catalog.pg_largeobject_metadata where 1=1;");

				conn.commit();
			}
		}
	}

	public static String resolveJdbcUrl() {
		final DatabaseSettings settings = Marshal.getDatabaseSettings();

		final StringBuilder sb = new StringBuilder();

		if (settings.getUserName() != null && !settings.getUserName().isEmpty()) {
			sb.append("user=").append(settings.getUserName());
		}

		if (settings.getPassword() != null && !settings.getPassword().isEmpty()) {
			sb.append("&password=").append(settings.getPassword());
		}

		final String urlParams = sb.toString();
		final String baseUrl = String.format(JDBC_URL_FORMAT, settings.getJdbcUrlPrefix(), settings.getHost(),
				settings.getPort(), settings.getSchema());

		return (!urlParams.isEmpty()) ? baseUrl + "?" + urlParams : baseUrl;
	}

	public synchronized static Connection acquire() throws SQLException {
		return pooledConnection(getInstance().getDataSource());
	}

	public synchronized HikariDataSource getDataSource() {
		return dataSource;
	}

	private void initialize(final boolean skipMigration) {
		try {
			if (Marshal.getDatabaseSettings().isActive()) {
				dataSource = hikariDataSource(resolveJdbcUrl());

				if (!skipMigration) {
					ensureTablesExist();
				}

			} else {
				log.error(LOGM_EXCEPTION,
						"DbManager: Initialization requested but database support is disabled via settings.");
			}
		} catch (SQLException ex) {
			throw new BinaryObjectException(ex.getMessage(), ex);
		}
	}

	@Override
	public BlobStoragePipeline blob() throws SQLException {
		return new BlobStoragePipeline(acquire());
	}
}
