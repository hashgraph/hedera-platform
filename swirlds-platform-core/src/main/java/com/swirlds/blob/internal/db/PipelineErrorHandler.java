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
import com.swirlds.blob.BinaryObjectNotFoundException;
import org.apache.commons.lang3.ArrayUtils;

import java.sql.SQLException;
import java.util.Arrays;

public class PipelineErrorHandler {

	private static final String SQLSTATE_PATH_NOT_FOUND = "F2128";

	private Pipeline pipeline;

	PipelineErrorHandler(final Pipeline pipeline) {
		this.pipeline = pipeline;
	}

	public void throwOnError(final int errorCode, final Integer[] errorSrc, final int errorCtx,
			final Object... context) {
		final int[] intErrorSrc = (errorSrc != null) ? ArrayUtils.toPrimitive(errorSrc) : null;
		throwOnError(errorCode, intErrorSrc, errorCtx, context);
	}

	public void throwOnError(final int errorCode, final int[] errorSrc, final int errorCtx, final Object... context) {
		if (!isError(errorCode)) {
			return;
		}

		SQLException priorException = null;

		try {
			if (pipeline.isTransactional()) {
				pipeline.rollback();
			}
		} catch (SQLException ex) {
			priorException = ex;
		}

		final DbErrorCode dbErrorCode = DbErrorCode.valueOf(errorCode);
		final DbErrorContext dbErrorContext = DbErrorContext.valueOf(errorCtx);
		final DbErrorSource[] dbErrorSources = (errorSrc != null && errorSrc.length > 0) ?
				new DbErrorSource[errorSrc.length] :
				new DbErrorSource[0];

		for (int i = 0; i < dbErrorSources.length; i++) {
			dbErrorSources[i] = DbErrorSource.valueOf(errorSrc[i]);
		}

		Object[] finalContext = context;

//		if (context != null && context.length > 1) {
//			if (DbErrorContext.SOURCE_PATH.equals(dbErrorContext)) {
//				finalContext = new Object[] { context[0] };
//			} else if (DbErrorContext.DEST_PATH.equals(dbErrorContext)) {
//				finalContext = new Object[] { context[1] };
//			}
//		}

		if (DbErrorCode.NOT_FOUND.equals(dbErrorCode) && DbErrorContext.IDENTIFIER.equals(dbErrorContext)) {
			throw new BinaryObjectNotFoundException(
					String.format("BinaryObject: Object Not Found [ context = %s, code = %s, sources = %s ]",
							dbErrorContext, dbErrorCode, Arrays.toString(dbErrorSources)), priorException);
		}

//		if (DbErrorCode.NOT_FOUND.equals(dbErrorCode)) {
//			if (DbErrorContext.PATH.equals(dbErrorContext) ||
//					DbErrorContext.SOURCE_PATH.equals(dbErrorContext) ||
//					DbErrorContext.DEST_PATH.equals(dbErrorContext)) {
//
//				throw new PathNotFoundException(
//						String.format("FCFileSystem: Path not found [ context = %s, code = %s, sources = %s ]",
//								Arrays.toString(finalContext), dbErrorCode,
//								Arrays.toString(dbErrorSources)), priorException);
//			} else {
//				throw new FileSystemException(
//						String.format("FCFileSystem: Resource not found [ context = %s, code = %s, sources = %s ]",
//								dbErrorContext, dbErrorCode, Arrays.toString(dbErrorSources)), priorException);
//			}
//		} else if (DbErrorCode.ALREADY_EXISTS.equals(dbErrorCode)) {
//			if (DbErrorContext.PATH.equals(dbErrorContext) ||
//					DbErrorContext.SOURCE_PATH.equals(dbErrorContext) ||
//					DbErrorContext.DEST_PATH.equals(dbErrorContext)) {
//
//				throw new EntityAlreadyExistsException(
//						String.format("FCFileSystem: Path already exists [ context = %s, code = %s, sources = %s ]",
//								Arrays.toString(finalContext), dbErrorCode,
//								Arrays.toString(dbErrorSources)), priorException);
//			} else {
//				throw new FileSystemException(
//						String.format("FCFileSystem: Resource already exists[ context = %s, code = %s, sources = %s ]",
//								dbErrorContext, dbErrorCode, Arrays.toString(dbErrorSources)), priorException);
//			}
//		}

		throw new BinaryObjectException(
				String.format("BinaryObject: Error during operation [ context = %s, code = %s, sources = %s ]",
						dbErrorContext, dbErrorCode, Arrays.toString(dbErrorSources)), priorException);
	}

	public boolean isKnown(final int errorCode) {
		return DbErrorCode.isKnown(errorCode);
	}

	public boolean exception(final SQLException ex, final Object... context) throws SQLException {
		try {
			if (pipeline.isTransactional()) {
				pipeline.rollback();
			}
		} catch (SQLException e) {
			// Suppressing Intentionally
		}

		throw new SQLException(ex);
	}

	public boolean isError(final int errorCode) {
		return errorCode < 0;
	}
}
