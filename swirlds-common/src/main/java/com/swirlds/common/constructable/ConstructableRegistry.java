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

package com.swirlds.common.constructable;

import com.swirlds.common.Releasable;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.apache.commons.lang3.NotImplementedException;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** A central registry of constructors for {@link RuntimeConstructable} classes */
public abstract class ConstructableRegistry {

	/** A map that holds the constructors of all RuntimeConstructable classes */
	private static Map<Long, ClassConstructorPair> map = new ConcurrentHashMap<>();

	/**
	 * Returns the no-arg constructor for the {@link RuntimeConstructable} if previously registered. If no
	 * constructor is found, it returns null. This method will always return null unless
	 * {@link #registerConstructables(String)} is called beforehand.
	 *
	 * @param classId
	 * 		the unique class ID to get the constructor for
	 * @return the constructor of the class, or null if no constructor is registered
	 */
	public static Supplier<RuntimeConstructable> getConstructor(long classId) {
		ClassConstructorPair p = map.get(classId);
		if (p == null) {
			return null;
		}
		return p.getConstructor();
	}

	/**
	 * Instantiates an object of a class defined by the supplied {@code classId}. If no object is registered with this
	 * ID, it will return null. This method will always return null unless {@link #registerConstructables(String)} is
	 * called beforehand.
	 *
	 * @param classId
	 * 		the unique class ID to create an object of
	 * @param <T>
	 * 		the type of the object
	 * @return an instance of the class, or null if no such class is registered
	 */
	public static <T extends RuntimeConstructable> T createObject(final long classId) {
		Supplier<RuntimeConstructable> c = getConstructor(classId);
		if (c == null) {
			return null;
		}
		return (T) c.get();
	}

	/**
	 * Searches the classpath and registers constructors for {@link RuntimeConstructable} classes.
	 *
	 * The method will search the classpath for any non-abstract classes that implement {@link RuntimeConstructable}.
	 * When a class is found, the method creates a lambda for its no-arguments constructor. The lambda is then
	 * registered by the class ID defined in the implementation.
	 *
	 * @param packagePrefix
	 * 		the package prefix of classes to search for, can be an empty String to search all packages
	 * @param additionalClassloader
	 * 		if any classes are loaded by a non-system classloader, it must be provided to find those classes
	 * @throws ConstructableRegistryException
	 * 		thrown if constructor cannot be registered for any reason
	 */
	public static synchronized void registerConstructables(String packagePrefix,
			URLClassLoaderWithLookup additionalClassloader) throws ConstructableRegistryException {
		for (Class<? extends RuntimeConstructable> aClass :
				getConstructableClasses(packagePrefix, additionalClassloader)) {
			registerConstructable(new ClassConstructorPair(aClass, getConstructorLambda(aClass,
					additionalClassloader)));
		}
	}

	/**
	 * Same as {@link #registerConstructables(String, URLClassLoaderWithLookup)} but with {@code ClassLoader} set to
	 * null
	 *
	 * @param packagePrefix
	 * 		the package prefix of classes to search for, can be an empty String to search all packages
	 * @throws ConstructableRegistryException
	 * 		thrown if constructor cannot be registered for any reason
	 */
	public static synchronized void registerConstructables(String packagePrefix) throws ConstructableRegistryException {
		registerConstructables(packagePrefix, null);
	}

	/**
	 * Register the provided {@link RuntimeConstructable} so that it can be instantiated based on its class ID
	 *
	 * @param aClass
	 * 		the class to register
	 * @throws ConstructableRegistryException
	 * 		thrown if constructor cannot be registered for any reason
	 */
	private static synchronized void registerConstructable(
			Class<? extends RuntimeConstructable> aClass) throws ConstructableRegistryException {
		registerConstructable(new ClassConstructorPair(aClass, getConstructorLambda(aClass, null)));
	}

	/**
	 * Register the provided {@link ClassConstructorPair} so that it can be instantiated based on its class ID
	 *
	 * @param pair
	 * 		the ClassConstructorPair to register
	 * @throws ConstructableRegistryException
	 * 		thrown if constructor cannot be registered for any reason
	 */
	public static void registerConstructable(ClassConstructorPair pair)
			throws ConstructableRegistryException {
		Long classId;
		try {

			RuntimeConstructable obj = pair.getConstructor().get();
			classId = obj.getClassId();
			if (obj instanceof Releasable) {
				try {
					((Releasable) obj).release();
				} catch (NotImplementedException ignored) {

				}
			}

		} catch (Throwable e) {
			// In case the constructor throws an exception
			throw new ConstructableRegistryException(
					String.format("Lambda constructor threw an Exception: %s", e.getMessage()),
					e
			);
		}
		ClassConstructorPair old = map.get(classId);
		if (old != null && !old.classEquals(pair)) {
			throw new ConstructableRegistryException(
					String.format(
							"Two classes ('%s' and '%s') have the same classId:%d (hex:%s). " +
									"ClassId must be unique!",
							old.getConstructableClass().getCanonicalName(),
							pair.getConstructableClass().getCanonicalName(),
							classId, Long.toHexString(classId)
					)
			);
		}
		map.put(classId, pair);
	}

	@SuppressWarnings("unchecked")
	private static Supplier<RuntimeConstructable> getConstructorLambda(
			Class<? extends RuntimeConstructable> constructable,
			URLClassLoaderWithLookup additionalClassloader) throws ConstructableRegistryException {
		Supplier<RuntimeConstructable> constructor;
		try {
			ConstructableRegistry.class.getModule().addReads(constructable.getModule());
			MethodHandles.Lookup lookup = getLookup(constructable, additionalClassloader);
			MethodHandle mh = lookup.findConstructor(constructable, MethodType.methodType(void.class));

			CallSite site = LambdaMetafactory.metafactory(
					lookup,
					"get",
					MethodType.methodType(Supplier.class),
					mh.type().generic(),
					mh,
					mh.type()
			);

			constructor = (Supplier<RuntimeConstructable>) site.getTarget().invokeExact();
		} catch (Throwable throwable) {
			throw new ConstructableRegistryException(
					String.format("Could not create a lambda for constructor: %s", throwable.getMessage()),
					throwable
			);
		}
		return constructor;
	}

	/**
	 * Depending on which classloader loaded the class, the Lookup object should come from the context of that
	 * classloader. So we check which loader loaded the class to get the appropriate lookup object.
	 */
	private static MethodHandles.Lookup getLookup(
			Class<? extends RuntimeConstructable> constructable,
			URLClassLoaderWithLookup additionalClassloader) throws ConstructableRegistryException {
		if (additionalClassloader == null) {
			return MethodHandles.lookup();
		}
		if (additionalClassloader != null && additionalClassloader.equals(constructable.getClassLoader())) {
			try {
				return additionalClassloader.getLookup();
			} catch (Exception e) {
				throw new ConstructableRegistryException(
						"Issue while getting the MethodHandles.Lookup from URLClassLoaderWithLookup", e);
			}
		}
		return MethodHandles.lookup();
	}

	private static List<Class<? extends RuntimeConstructable>> getConstructableClasses(String packagePrefix)
			throws ConstructableRegistryException {
		return getConstructableClasses(packagePrefix, null);
	}

	private static List<Class<? extends RuntimeConstructable>> getConstructableClasses(
			String packagePrefix,
			URLClassLoaderWithLookup additionalClassloader)
			throws ConstructableRegistryException {

		final List<Class<? extends RuntimeConstructable>> list = new LinkedList<>();
		ClassGraph classGraph = new ClassGraph()
				.enableClassInfo()
				.disableDirScanning()
				.whitelistPackages(packagePrefix);
		if (additionalClassloader != null) {
			classGraph.addClassLoader(additionalClassloader);
		}
		try (final ScanResult scanResult = classGraph.scan()) {
			for (final ClassInfo classInfo : scanResult.getClassesImplementing(
					RuntimeConstructable.class.getCanonicalName())) {
				final Class<? extends RuntimeConstructable> subType = classInfo.loadClass(RuntimeConstructable.class);
				if (isSkippable(subType)) {
					continue;
				}

				if (!hasNoArgsConstructor(subType)) {
					throw new ConstructableRegistryException(
							String.format("Cannot find no-args constructor for class: %s", subType.getCanonicalName())
					);
				}
				list.add(subType);
			}
		}

		return list;
	}

	private static boolean isSkippable(Class<? extends RuntimeConstructable> subType) {
		return subType.isInterface() ||
				Modifier.isAbstract(subType.getModifiers()) ||
				subType.isAnnotationPresent(ConstructableIgnored.class);
	}

	private static boolean hasNoArgsConstructor(Class<? extends RuntimeConstructable> constructable) {
		return Stream.of(constructable.getConstructors()).anyMatch((c) -> c.getParameterCount() == 0);
	}

}
