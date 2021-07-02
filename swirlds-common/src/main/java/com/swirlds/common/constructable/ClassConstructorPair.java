/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common.constructable;

import java.util.function.Supplier;

public class ClassConstructorPair {
	private Class<? extends RuntimeConstructable> aClass;
	private Supplier<RuntimeConstructable> constructor;

	public ClassConstructorPair(Class<? extends RuntimeConstructable> aClass,
			Supplier<RuntimeConstructable> constructor) {
		this.aClass = aClass;
		this.constructor = constructor;
	}

	public Class<? extends RuntimeConstructable> getConstructableClass() {
		return aClass;
	}

	public Supplier<RuntimeConstructable> getConstructor() {
		return constructor;
	}

	public boolean classEquals(ClassConstructorPair pair) {
		return this.aClass.equals(pair.getConstructableClass());
	}
}
