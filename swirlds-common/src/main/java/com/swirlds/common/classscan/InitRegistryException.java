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

package com.swirlds.common.classscan;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;

public class InitRegistryException extends RuntimeException {
	public InitRegistryException(ClassInfo classInfo, String error) {
		super(String.format(
				"Class '%s' has the following error: '%s'",
				classInfo.getName(), error));
	}

	public InitRegistryException(ClassInfo classInfo, MethodInfo method, String error) {
		super(String.format(
				"Class '%s' has a method '%s' with the following error: '%s'",
				classInfo.getName(), method.getName(), error));
	}

	public InitRegistryException(Class<?> startStopInitClass, Exception e) {
		super(String.format("The class '%s' threw an exception", startStopInitClass.getName()), e);
	}

	public InitRegistryException(Class<?> startStopInitClass, String method, Exception e) {
		super(String.format("The class '%s' method '%s' threw an exception",
				startStopInitClass.getName(), method), e);
	}
}
