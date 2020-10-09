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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A registry used to store and call classes annotated with {@link StartStopInit}
 */
public abstract class InitRegistry {
	private static final String START_INIT = "startInit";
	private static final String STOP_INIT = "stopInit";
	private static final List<String> methodsNames = List.of(START_INIT, STOP_INIT);
	private static volatile Set<Class<?>> initClasses = null;

	/**
	 * Finds all classes in the classpath that are annotated with {@link StartStopInit} and stores them in the registry
	 */
	public static void registerInitClasses() {
		ClassGraph classGraph = new ClassGraph()
				.enableClassInfo()
				.enableAnnotationInfo()
				.enableMethodInfo()
				.disableDirScanning();
		Set<Class<?>> initClasses = new HashSet<>();
		try (final ScanResult scanResult = classGraph.scan()) {
			for (final ClassInfo classInfo : scanResult.getClassesWithAnnotation(
					StartStopInit.class.getName())) {

				Class<?> initClass = classInfo.loadClass(false);
				for (String methodName : methodsNames) {
					checkMethod(classInfo, methodName);

				}
				initClasses.add(initClass);
			}
		}
		InitRegistry.initClasses = initClasses;
	}

	/**
	 * Calls {@code startInit()} on all registered classes
	 */
	public static void callStartInit() {
		callMethod(START_INIT);
	}

	/**
	 * Calls {@code stopInit()} on all registered classes
	 */
	public static void callStopInit() {
		callMethod(STOP_INIT);
	}

	private static void callMethod(String methodName) {
		for (Class<?> initClass : initClasses) {
			try {
				Method method = initClass.getMethod(methodName);
				method.invoke(null);
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				throw new InitRegistryException(initClass, methodName, e);
			}
		}
	}

	private static void checkMethod(ClassInfo classInfo, String methodName) {
		if (!classInfo.hasDeclaredMethod(methodName)) {
			classError(classInfo, "Must declare method " + methodName);
		}
		MethodInfo method = classInfo.getMethodInfo().getSingleMethod(methodName);
		if (method == null) {
			classError(classInfo, "Cannot get method: " + methodName);
		}
		if (!method.isPublic()) {
			methodError(classInfo, method, "Must be public");
		}
		if (!method.isStatic()) {
			methodError(classInfo, method, "Must be static");
		}
		if (method.getParameterInfo().length > 0) {
			methodError(classInfo, method, "Must not have any parameters");
		}
		if (!method.getTypeDescriptor().getResultType().toString().equals("void")) {
			methodError(classInfo, method, "Must return void");
		}
	}

	private static void methodError(ClassInfo classInfo, MethodInfo method, String error) {
		throw new InitRegistryException(classInfo, method, error);
	}

	private static void classError(ClassInfo classInfo, String error) {
		throw new InitRegistryException(classInfo, error);
	}
}
