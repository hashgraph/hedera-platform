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

package com.swirlds.common.io.extendable;

import java.util.Arrays;
import java.util.List;

public class StreamExtensionList implements StreamExtension {
	private final List<StreamExtension> extensions;

	public StreamExtensionList(StreamExtension... extensions) {
		this.extensions = Arrays.asList(extensions);
	}

	@Override
	public void newByte(int aByte) {
		for (StreamExtension extension : extensions) {
			extension.newByte(aByte);
		}
	}

	@Override
	public void newBytes(byte[] b, int off, int len) {
		for (StreamExtension extension : extensions) {
			extension.newBytes(b, off, len);
		}
	}
}
