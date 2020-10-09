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

import com.swirlds.blob.BinaryObject;
import com.swirlds.blob.BinaryObjectException;
import com.swirlds.common.crypto.Hash;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class BlobStoragePipeline extends Pipeline {

	BlobStoragePipeline(final Connection connection) {
		super(connection);
	}

	public boolean exists(final Hash hash) throws SQLException {
		if (hash == null) {
			throw new IllegalArgumentException("hash");
		}

		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_exists", 1, true))) {
			stmt.registerOutParameter(1, Types.BIGINT);
			setValueOrNull(stmt, 2, hash);

			stmt.execute();

			final long id = stmt.getLong(1);

			return !stmt.wasNull() && id > 0;
		}
	}

	public BinaryObject put(final Hash hash, final byte[] content) throws SQLException {
		if (hash == null) {
			throw new IllegalArgumentException("hash");
		}

		if (content == null) {
			throw new IllegalArgumentException("content");
		}

		Long fileOid = null;

		if (!exists(hash)) {
			fileOid = createFile(content);
		}

		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_store", 6, false))) {
			setValueOrNull(stmt, 1, hash);
			setValueOrNull(stmt, 2, fileOid);

			stmt.registerOutParameter(3, Types.BIGINT);
			stmt.registerOutParameter(4, Types.INTEGER);
			stmt.registerOutParameter(5, Types.ARRAY);
			stmt.registerOutParameter(6, Types.INTEGER);

			stmt.execute();

			final long id = stmt.getLong(3);

			handleErrors(stmt, 4, hash, content);

			return new BinaryObject(id, hash);
		} catch (BinaryObjectException ex) {
			if (fileOid != null && fileOid > 0) {
				deleteFile(fileOid);
			}

			throw ex;
		}
	}

	public BinaryObject append(final long id, final byte[] content) throws SQLException {
		if (content == null) {
			throw new IllegalArgumentException("content");
		}

		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_append", 7, false))) {
			return write(stmt, id, content);
		}
	}

	public BinaryObject update(final long id, final byte[] content) throws SQLException {
		if (content == null) {
			throw new IllegalArgumentException("content");
		}

		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_update", 7, false))) {
			return write(stmt, id, content);
		}
	}

	private BinaryObject write(final CallableStatement stmt, final long id,
			final byte[] content) throws SQLException {
		setValueOrNull(stmt, 1, id);
		setValueOrNull(stmt, 2, content);

		stmt.registerOutParameter(3, Types.BIGINT);
		stmt.registerOutParameter(4, Types.BINARY);
		stmt.registerOutParameter(5, Types.INTEGER);
		stmt.registerOutParameter(6, Types.ARRAY);
		stmt.registerOutParameter(7, Types.INTEGER);

		stmt.execute();

		final long newId = stmt.getLong(3);
		final byte[] hashValue = stmt.getBytes(4);

		handleErrors(stmt, 5, id, content);

		return new BinaryObject(newId, ((hashValue != null) ? new Hash(hashValue) : null));
	}

	public byte[] get(final long id) throws SQLException {
		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_retrieve", 5, false))) {
			setValueOrNull(stmt, 1, id);

			stmt.registerOutParameter(2, Types.BIGINT);
			stmt.registerOutParameter(3, Types.INTEGER);
			stmt.registerOutParameter(4, Types.ARRAY);
			stmt.registerOutParameter(5, Types.INTEGER);

			stmt.execute();

			final long fileOid = stmt.getLong(2);

			handleErrors(stmt, 3, id);

			return readFile(fileOid);
		}
	}

	public void delete(final long id) throws SQLException {
		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_delete", 6, false))) {
			setValueOrNull(stmt, 1, id);

			stmt.registerOutParameter(2, Types.BOOLEAN);
			stmt.registerOutParameter(3, Types.BIGINT);
			stmt.registerOutParameter(4, Types.INTEGER);
			stmt.registerOutParameter(5, Types.ARRAY);
			stmt.registerOutParameter(6, Types.INTEGER);

			stmt.execute();

			final boolean deleted = stmt.getBoolean(2);
			final long fileOid = stmt.getLong(3);

			handleErrors(stmt, 4, id);

			if (deleted) {
				deleteFile(fileOid);
			}
		}
	}

	public void increaseReferenceCount(final long id) throws SQLException {
		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_increment_ref_count", 4, false))) {
			setValueOrNull(stmt, 1, id);

			stmt.registerOutParameter(2, Types.INTEGER);
			stmt.registerOutParameter(3, Types.ARRAY);
			stmt.registerOutParameter(4, Types.INTEGER);

			stmt.execute();

			handleErrors(stmt, 2, id);
		}
	}

	public Long[] restore(final long[] refCounts, final byte[][] blobHashes) throws SQLException {

		final Array refCountArray = asArray(refCounts);
		final Array blobHashArray = asArray(blobHashes);

		try (final CallableStatement stmt = prepareCall(buildCall("bs_blob_restore", 6, false))) {
			stmt.setArray(1, refCountArray);
			stmt.setArray(2, blobHashArray);
			stmt.registerOutParameter(3, Types.ARRAY);
			stmt.registerOutParameter(4, Types.INTEGER);
			stmt.registerOutParameter(5, Types.ARRAY);
			stmt.registerOutParameter(6, Types.INTEGER);

			stmt.execute();

			final Array idArray = stmt.getArray(3);

			final Long[] ids = (stmt.wasNull()) ? new Long[0] : (Long[]) idArray.getArray();

			handleErrors(stmt, 4);

			return ids;
		}
	}

	public long retrieveNumberOfBlobs() throws SQLException {
		try (final PreparedStatement stmt = prepareStatement("SELECT count(*) FROM binary_objects");
			 final ResultSet resultSet = stmt.executeQuery()) {
			if (resultSet.next()) {
				return resultSet.getLong(1);
			}

			throw new SQLException("The query didn't return any result");
		}
	}

	public List<Long> retrieveOidsBinaryObjectTable() throws SQLException {
		try (final PreparedStatement stmt = prepareStatement("SELECT file_oid FROM binary_objects");
			 final ResultSet resultSet = stmt.executeQuery()) {
			final List<Long> list = new ArrayList<>();

			while (resultSet.next()) {
				list.add(resultSet.getLong(1));
			}

			return list;
		}
	}

	public List<Long> retrieveLoIDs() throws SQLException {
		try (final PreparedStatement stmt = prepareStatement("select oid from pg_largeobject_metadata");
			 final ResultSet resultSet = stmt.executeQuery()) {
			final List<Long> list = new ArrayList<>();

			while (resultSet.next()) {
				list.add(resultSet.getLong(1));
			}

			return list;
		}
	}

	private void handleErrors(CallableStatement stmt, int startErrorIndex, Object... params) throws SQLException {
		final int errorCode = stmt.getInt(startErrorIndex);
		final int errorCtx = stmt.getInt(startErrorIndex + 2);

		final Array srcArray = stmt.getArray(startErrorIndex + 1);
		final Integer[] errorSrc = (Integer[]) srcArray.getArray();

		handle(errorCode, errorSrc, errorCtx, params);
	}

	private long createFile(final byte[] content) throws SQLException {
		final PGConnection connection = getConnection().unwrap(PGConnection.class);
		final LargeObjectManager manager = connection.getLargeObjectAPI();

		final long oid = manager.createLO();
		try (final LargeObject lo = manager.open(oid, LargeObjectManager.WRITE)) {
			lo.write(content);
			return oid;
		}
	}

	private void deleteFile(final long oid) throws SQLException {
		final PGConnection connection = getConnection().unwrap(PGConnection.class);
		final LargeObjectManager manager = connection.getLargeObjectAPI();

		manager.delete(oid);
	}

	private byte[] readFile(final long oid) throws SQLException {
		final PGConnection connection = getConnection().unwrap(PGConnection.class);
		final LargeObjectManager manager = connection.getLargeObjectAPI();

		try (final LargeObject lo = manager.open(oid, LargeObjectManager.READ)) {
			return lo.read(lo.size());
		}
	}

	private void appendFile(final long oid, final byte[] bytes) throws SQLException {
		final PGConnection connection = getConnection().unwrap(PGConnection.class);
		final LargeObjectManager manager = connection.getLargeObjectAPI();

		try (final LargeObject lo = manager.open(oid, LargeObjectManager.WRITE)) {
			lo.seek(lo.size());
			lo.write(bytes);
		}
	}
}
