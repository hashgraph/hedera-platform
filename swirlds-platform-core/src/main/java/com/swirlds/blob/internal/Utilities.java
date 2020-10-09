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

package com.swirlds.blob.internal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.locks.StampedLock;

public class Utilities {

	/**
	 * log all exceptions, and serious problems. These should never happen unless we are either receiving packets from
	 * an attacker, or there is a bug in the code. In most cases, this should include a full stack trace of the
	 * exception.
	 */
	public static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	/** logs events related to the startup of the application */
	public static final Marker LOGM_STARTUP = MarkerManager.getMarker("STARTUP");

	/** logs verbose diagnostic events related to the FCFileSystem */
	public static final Marker LOGM_FCFS_DIAGNOSTICS = MarkerManager.getMarker("FCFS_DIAGNOSTICS");

	private static final Logger log = LogManager.getLogger();
	private static final File HIKARI_CONFIG_FILE = new File("hikari.properties");

	/**
	 * Private constructor to prevent instantiation.
	 */
	private Utilities() {

	}

	public static int bytesToInt(int[] b) {
		int result = 0;
		for (int i = 0; i < 4; i++) {
			result <<= 4;
			result |= (b[i] & 0xFF);
		}
		return result;
	}

	public static long bytesToLong(byte[] b) {
		long result = 0;
		for (int i = 0; i < 8; i++) {
			result <<= 8;
			result |= (b[i] & 0xFF);
		}
		return result;
	}

	public static void destroy(final DataSource dataSource) throws SQLException {
		if (dataSource instanceof HikariDataSource) {
			((HikariDataSource) dataSource).close();
		}
	}

	public static void destroy(final Connection connection) throws SQLException {
		if (connection != null) {
			if (!connection.isClosed()) {
				connection.close();
			}
		}
	}

	public static void destroy(final Statement statement) throws SQLException {
		if (statement != null) {
			if (!statement.isClosed()) {
				statement.close();
			}
		}
	}

	public static void destroy(final ResultSet resultSet) throws SQLException {
		if (resultSet != null) {
			if (!resultSet.isClosed()) {
				resultSet.close();
			}
		}
	}

	public static void destroyQuietly(final DataSource dataSource) {
		try {
			destroy(dataSource);
		} catch (SQLException ex) {
			log.trace(LOGM_EXCEPTION, "FCFS: Failed to close the DataSource quietly", ex);
		}
	}

	public static void destroyQuietly(final Connection connection) {
		try {
			destroy(connection);
		} catch (SQLException ex) {
			log.trace(LOGM_EXCEPTION, "FCFS: Failed to close the SQL Connection quietly", ex);
		}
	}

	public static void destroyQuietly(final Statement statement) {
		try {
			destroy(statement);
		} catch (SQLException ex) {
			log.trace(LOGM_EXCEPTION, "FCFS: Failed to close the SQL Statement quietly", ex);
		}
	}

	public static void destroyQuietly(final ResultSet resultSet) {
		try {
			destroy(resultSet);
		} catch (SQLException ex) {
			log.trace(LOGM_EXCEPTION, "FCFS: Failed to close the ResultSet quietly", ex);
		}
	}

	public static boolean exists(final Statement stmt, final String tableName, final String whereClause)
			throws SQLException {
		if (stmt == null) {
			throw new IllegalArgumentException("stmt");
		}

		if (tableName == null || tableName.isEmpty()) {
			throw new IllegalArgumentException("tableName");
		}

		if (whereClause == null || whereClause.isEmpty()) {
			throw new IllegalArgumentException("whereClause");
		}

		try (final ResultSet rs = stmt
				.executeQuery(String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereClause))) {
			return rs.next() && rs.getLong(1) > 0;
		}
	}

	public static String hex(byte b) {
		return String.format("%02x", b);
	}

	public static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();

		if (bytes != null) {
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
		}

		return sb.toString();
	}


	public static byte[] unhex(CharSequence bytes) {

		if (bytes.length() % 2 != 0) {
			throw new IllegalArgumentException("bytes");
		}

		final int len = bytes.length();
		final byte[] data = new byte[(len / 2)];
		for (int i = 0; i < len; i += 2) {
			data[(i / 2)] = (byte) ((Character.digit(bytes.charAt(i), 16) << 4)
					+ Character.digit(bytes.charAt(i + 1), 16));
		}

		return data;
	}

	public static byte[] hexPgByteaToBytes(byte[] s) {
		int len = s.length;
		byte[] data = new byte[(len / 2) - 1];
		for (int i = 2; i < len; i += 2) {
			data[(i / 2) - 1] = (byte) ((Character.digit(s[i], 16) << 4)
					+ Character.digit(s[i + 1], 16));
		}
		return data;
	}

	/**
	 * Creates a pooled datasource from a jdbc url.
	 *
	 * @param jdbcUrl
	 * 		the jdbc url the underlying connection should use
	 * @return the HikariCP datasource
	 * @throws SQLException
	 * 		if any sql errors occur
	 */
	public static HikariDataSource hikariDataSource(final String jdbcUrl) throws SQLException {
		Properties properties = new Properties();

		if (HIKARI_CONFIG_FILE.exists() && HIKARI_CONFIG_FILE.isFile()) {
			try (FileInputStream istream = new FileInputStream(HIKARI_CONFIG_FILE)) {
				properties.load(istream);

				properties.setProperty("jdbcUrl", properties.getProperty("jdbcUrl", jdbcUrl));
				properties.setProperty("autoCommit", properties.getProperty("autoCommit", "false"));
			} catch (IOException ex) {
				log.info(LOGM_EXCEPTION, "FCFS: Unable to read HikariCP configuration file", ex);
			}
		} else {
			properties.setProperty("autoCommit", "false");
			properties.setProperty("jdbcUrl", jdbcUrl);
			properties.setProperty("maximumPoolSize", "5");
		}

		return new HikariDataSource(new HikariConfig(properties));
	}

	public static byte[] intToBytes(int l) {
		byte[] result = new byte[4];
		for (int i = 3; i >= 0; i--) {
			result[i] = (byte) (l & 0xFF);
			l >>= 4;
		}
		return result;
	}

	public static byte[] longToBytes(long l) {
		byte[] result = new byte[8];
		for (int i = 7; i >= 0; i--) {
			result[i] = (byte) (l & 0xFF);
			l >>= 8;
		}
		return result;
	}

	public static Connection pooledConnection(final DataSource dataSource) throws SQLException {
		if (dataSource == null) {
			throw new IllegalArgumentException("dataSource");
		}

		final Connection conn = dataSource.getConnection();
		conn.setAutoCommit(true);

		return conn;
	}

	public static String repeatChars(final int count, final char c) {
		final char[] chars = new char[count];
		Arrays.fill(chars, c);

		return new String(chars);
	}

	public static void rollbackQuietly(final Connection connection) {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.rollback();
			}
		} catch (SQLException ex) {
			log.trace(LOGM_EXCEPTION, "FCFS: Failed to rollback the SQL Connection quietly", ex);
		}
	}

	public static void safeUnlock(final StampedLock lock, final long lockStamp) {
		try {
			if (StampedLock.isLockStamp(lockStamp)) {
				lock.unlock(lockStamp);
			}
		} catch (IllegalMonitorStateException ex) {
			// Suppressed
		}
	}

	public static int highestSetBitPosition(final int num) {
		int n = num;

		// Below steps set bits after
		// MSB (including MSB)
		n |= n >> 1;
		n |= n >> 2;
		n |= n >> 4;
		n |= n >> 8;
		n |= n >> 16;

		// Increment n by 1 to set bit above msb
		n = n + 1;

		// downshift back to original msb position
		return (n >> 1);
	}
}
