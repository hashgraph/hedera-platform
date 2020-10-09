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

package com.swirlds.common.crypto.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Provides a list of known GPU device vendor name patterns.
 */
public enum DeviceVendor {
	INTEL, NVIDIA, AMD, APPLE, MESA, OTHER;

	private static final Pattern VENDOR_AMD_PATTERN = Pattern.compile(".*(AMD|Advanced Micro Devices)+.*",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern VENDOR_INTEL_PATTERN = Pattern.compile(".*(Intel)+.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern VENDOR_NVIDIA_PATTERN = Pattern.compile(".*(NVIDIA)+.*", Pattern.CASE_INSENSITIVE);

	private static final Map<DeviceVendor, Pattern> vendorLookup = new HashMap<>();

	static {
		vendorLookup.put(INTEL, VENDOR_INTEL_PATTERN);
		vendorLookup.put(NVIDIA, VENDOR_NVIDIA_PATTERN);
		vendorLookup.put(AMD, VENDOR_AMD_PATTERN);
	}

	public static DeviceVendor resolve(final String vendor) {

		for (Map.Entry<DeviceVendor, Pattern> entry : vendorLookup.entrySet()) {
			final Pattern pattern = entry.getValue();

			if (pattern.matcher(vendor).matches()) {
				return entry.getKey();
			}
		}

		return OTHER;
	}

	public String getCompilerFlag() {
		long vendorFlag = 0;
		int cudaFlag = 0;

		switch (this) {
			case AMD:
				vendorFlag = (1 << 0);
				break;
			case APPLE:
				vendorFlag = (1 << 1);
				break;
			case INTEL:
				vendorFlag = (1 << 2);
				break;
			case MESA:
				vendorFlag = (1 << 4);
				break;
			case NVIDIA:
				vendorFlag = (1 << 5);
				cudaFlag = 1;
				break;
			case OTHER:
				vendorFlag = (1 << 31);
				break;
			default:
				vendorFlag = (1 << 31);
				break;
		}

		return String.format("-D VENDOR_ID=%d -D CUDA_ARCH=%d", vendorFlag, cudaFlag);
	}
}
