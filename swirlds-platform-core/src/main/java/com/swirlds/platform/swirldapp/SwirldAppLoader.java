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

package com.swirlds.platform.swirldapp;

import com.swirlds.common.SwirldMain;
import com.swirlds.common.constructable.URLClassLoaderWithLookup;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

public class SwirldAppLoader {
	/** the name of the app class inheriting from {@link SwirldMain} */
	private String mainClassName;
	/** the location of the JAR where the apps classes are located */
	private File appJarPath;
	/** the constructor for the main program of the app */
	private Constructor<?> appMainConstructor;
	/** The classloader used to load the app */
	private URLClassLoaderWithLookup classLoader;

	private SwirldAppLoader(String mainClassName, File appJarPath, Constructor<?> appMainConstructor,
			URLClassLoaderWithLookup classLoader) {
		this.mainClassName = mainClassName;
		this.appJarPath = appJarPath;
		this.appMainConstructor = appMainConstructor;
		this.classLoader = classLoader;
	}

	/**
	 * Creates a new instance of {@link SwirldMain} and returns it
	 *
	 * @return a new instance of {@link SwirldMain}
	 * @throws AppLoaderException
	 * 		if any issue occurs while instantiating the object
	 */
	public SwirldMain instantiateSwirldMain() throws AppLoaderException {
		try {
			return (SwirldMain) appMainConstructor.newInstance();
		} catch (InstantiationException | InvocationTargetException e) {
			throw new AppLoaderException("ERROR: Couldn't instantiate the class " + mainClassName, e);
		} catch (IllegalAccessException e) {
			throw new AppLoaderException("ERROR: Couldn't access the class " + mainClassName, e);
		}
	}

	/**
	 * @param mainClassName
	 * 		the name of the app class inheriting from {@link SwirldMain}
	 * @param appJarPath
	 * 		the location of the JAR where the apps classes are located
	 * @return the SwirldAppLoader object
	 * @throws AppLoaderException
	 * 		if any problems occur while loading the app
	 */
	public static SwirldAppLoader loadSwirldApp(String mainClassName, File appJarPath) throws AppLoaderException {
		URLClassLoaderWithLookup classLoader;
		try {
			classLoader = new URLClassLoaderWithLookup(
					new URL[] { appJarPath.toURI().toURL() },
					Thread.currentThread().getContextClassLoader());
			Class<?> mainClass = Class.forName(mainClassName, true, classLoader);
			Constructor<?>[] constructors = mainClass.getDeclaredConstructors();
			Constructor<?> constructor = null;
			for (Constructor<?> constructor2 : constructors) {
				if (constructor2.getGenericParameterTypes().length == 0) {
					constructor = constructor2;
					break;
				}
			}
			if (constructor == null) {
				throw new AppLoaderException("The class '" + mainClassName
						+ "' must have a default constructor");
			}

			return new SwirldAppLoader(mainClassName, appJarPath, constructor, classLoader);
		} catch (ClassNotFoundException e) {
			throw new AppLoaderException("ERROR: Couldn't find the class \"" + mainClassName + "\"", e);
		} catch (Exception e) {
			throw new AppLoaderException("ERROR: There are problems loading the class " + mainClassName, e);
		}
	}

	/**
	 * @return the classloader used to load the app
	 */
	public URLClassLoaderWithLookup getClassLoader() {
		return classLoader;
	}
}
