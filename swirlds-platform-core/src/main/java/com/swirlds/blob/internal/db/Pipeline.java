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

import com.swirlds.common.crypto.Hash;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.postgresql.util.PGbytea;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static com.swirlds.blob.internal.Utilities.destroy;
import static com.swirlds.blob.internal.Utilities.destroyQuietly;
import static com.swirlds.blob.internal.Utilities.rollbackQuietly;

public abstract class Pipeline implements AutoCloseable {

	private Connection connection;
	private Logger logger;
	private PipelineErrorHandler errorHandler;

	protected Pipeline(final Connection connection) {
		if (connection == null) {
			throw new IllegalArgumentException("connection");
		}

		this.connection = connection;
		this.logger = LogManager.getLogger(getClass());
		this.errorHandler = new PipelineErrorHandler(this);
	}

	protected Connection getConnection() {
		return connection;
	}

	public Logger getLogger() {
		return logger;
	}

	public PipelineErrorHandler getErrorHandler() {
		return errorHandler;
	}

	public Logger log() {
		return logger;
	}

	public void join(final Pipeline other) {
		rollbackQuietly(connection);
		destroyQuietly(connection);
		this.connection = other.connection;
	}

	protected String buildCall(final String spName, final int numArgs, final boolean hasReturn) {
		final StringBuilder builder = new StringBuilder();

		builder.append("{ ");

		if (hasReturn) {
			builder.append("? = ");
		}

		builder.append("call ").append(spName).append('(');

		if (numArgs > 0) {
			for (int i = 0; i < numArgs; i++) {
				builder.append('?');

				if (i != numArgs - 1) {
					builder.append(',').append(' ');
				}
			}
		}

		builder.append(") }");

		return builder.toString();
	}


	protected CallableStatement prepareCall(final String sql) throws SQLException {
		return connection.prepareCall(sql);
	}

	protected PreparedStatement prepareStatement(final String sql) throws SQLException {
		return connection.prepareStatement(sql);
	}

	public void withSerialization() throws SQLException {
		connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
	}

	public void withUncomitted() throws SQLException {
		connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
	}

	public void withTransaction() throws SQLException {
		if (isTransactional()) {
			rollbackQuietly(connection);
		}

		connection.setAutoCommit(false);
	}

	public boolean isTransactional() {
		try {
			return !connection.getAutoCommit();
		} catch (SQLException ex) {
			return false;
		}
	}


	public void commit() throws SQLException {
		connection.commit();
		connection.setAutoCommit(true);
	}

	public void rollback() throws SQLException {
		connection.rollback();
		connection.setAutoCommit(true);
	}

	protected void setValueOrNull(final CallableStatement stmt, final int position,
			final Short value) throws SQLException {
		if (value == null) {
			stmt.setNull(position, Types.SMALLINT);
		} else {
			stmt.setShort(position, value);
		}
	}

	protected void setValueOrNull(final CallableStatement stmt, final int position,
			final Integer value) throws SQLException {
		if (value == null) {
			stmt.setNull(position, Types.INTEGER);
		} else {
			stmt.setInt(position, value);
		}
	}

	protected void setValueOrNull(final CallableStatement stmt, final int position,
			final Long value) throws SQLException {
		if (value == null) {
			stmt.setNull(position, Types.BIGINT);
		} else {
			stmt.setLong(position, value);
		}
	}

	protected void setValueOrNull(final CallableStatement stmt, final int position,
			final byte[] value) throws SQLException {
		if (value == null) {
			stmt.setNull(position, Types.BINARY);
		} else {
			stmt.setBytes(position, value);
		}
	}

	protected void setValueOrNull(final CallableStatement stmt, final int position,
			final String value) throws SQLException {
		if (value == null) {
			stmt.setNull(position, Types.VARCHAR);
		} else {
			stmt.setString(position, value);
		}
	}

	protected void setValueOrNull(final CallableStatement stmt, final int position,
			final Hash value) throws SQLException {
		setValueOrNull(stmt, position, (value != null) ? value.getValue() : null);
	}


	protected Short getShortOrNull(final ResultSet rs, final String fieldName) throws SQLException {
		final int fieldPos = rs.findColumn(fieldName);

		final short value = rs.getShort(fieldPos);

		if (rs.wasNull()) {
			return null;
		}

		return value;
	}

	protected Long getLongOrNull(final ResultSet rs, final String fieldName) throws SQLException {
		final int fieldPos = rs.findColumn(fieldName);

		final long value = rs.getLong(fieldPos);

		if (rs.wasNull()) {
			return null;
		}

		return value;
	}

	protected Integer getIntOrNull(final ResultSet rs, final String fieldName) throws SQLException {
		final int fieldPos = rs.findColumn(fieldName);

		final int value = rs.getInt(fieldPos);

		if (rs.wasNull()) {
			return null;
		}

		return value;
	}

	protected Array asArray(final List<String> items) throws SQLException {
		return connection.createArrayOf("text", (items != null) ? items.toArray() : new Object[0]);
	}


	protected Array asArray(final long[] items) throws SQLException {
		return connection.createArrayOf("bigint", (items != null) ? ArrayUtils.toObject(items) : new Object[0]);
	}

	protected Array asArray(final byte[][] items) throws SQLException {

		String[] wrapper = new String[items.length];

		for (int i = 0; i < items.length; i++) {
			wrapper[i] = PGbytea.toPGString(items[i]);
		}

		return connection.createArrayOf("bytea", wrapper);
	}

	protected Array asArray(final Hash[] items) throws SQLException {

		String[] wrapper = new String[items.length];

		for (int i = 0; i < items.length; i++) {
			if (items[i] != null) {
				wrapper[i] = PGbytea.toPGString(items[i].getValue());
			} else {
				wrapper[i] = null;
			}
		}

		return connection.createArrayOf("bytea", wrapper);
	}

	protected void handle(final int errorCode, final int[] errorSrc, final int errorCtx, final Object... context) {
		errorHandler.throwOnError(errorCode, errorSrc, errorCtx, context);
	}

	protected void handle(final int errorCode, final Integer[] errorSrc, final int errorCtx, final Object... context) {
		errorHandler.throwOnError(errorCode, errorSrc, errorCtx, context);
	}

	protected void handle(final int errorCode, final int errorSrc, final int errorCtx, final Object... context) {
		errorHandler.throwOnError(errorCode, new int[] { errorSrc }, errorCtx, context);
	}

	protected void handle(final int errorCode, final int[] errorSrc, final int errorCtx, boolean suppressUnknown,
			final Object... context) {
		if (suppressUnknown && !errorHandler.isKnown(errorCode)) {
			return;
		}

		errorHandler.throwOnError(errorCode, errorSrc, errorCtx, context);
	}

	protected boolean handle(final SQLException ex, final Object... context) throws SQLException {
		return errorHandler.exception(ex, context);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws SQLException
	 * 		if this resource cannot be closed
	 */
	@Override
	public void close() throws SQLException {
		if (isTransactional()) {
			rollbackQuietly(connection);
		}

		destroy(connection);
	}

	public long getDbSize() throws SQLException {
		try (final PreparedStatement stmt = prepareStatement("SELECT pg_database_size('fcfs');");
			 final ResultSet resultSet = stmt.executeQuery()) {
			while (resultSet.next()) {
				return resultSet.getLong(1);
			}
			throw new SQLException("The query didn't return any result");
		}
	}

	public long getTableDbSize(String tableName) throws SQLException {

		try (final CallableStatement stmt = prepareCall("{? = call pg_table_size(?)}")) {
			stmt.registerOutParameter(1, Types.BIGINT);
			stmt.setString(2, tableName);
			stmt.execute();
			return stmt.getLong(1);
		}
	}
}
