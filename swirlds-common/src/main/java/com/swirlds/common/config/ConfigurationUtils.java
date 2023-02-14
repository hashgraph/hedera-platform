/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.config;

import com.swirlds.common.constructable.URLClassLoaderWithLookup;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigurationBuilder;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/** Miscellaneous configuration utilities. */
public final class ConfigurationUtils {

    private ConfigurationUtils() {}

    /**
     * Scan all classes in a classpath and register all configuration data types with a
     * configuration builder.
     *
     * @param configurationBuilder a configuration builder
     * @param packagePrefix the package prefix to scan
     * @param additionalClassLoaders additional classloaders to scan
     */
    @SuppressWarnings("unchecked")
    public static void scanAndRegisterAllConfigTypes(
            final ConfigurationBuilder configurationBuilder,
            final String packagePrefix,
            final URLClassLoaderWithLookup... additionalClassLoaders) {

        final ClassGraph classGraph =
                new ClassGraph().enableAnnotationInfo().whitelistPackages(packagePrefix);

        if (additionalClassLoaders != null) {
            for (final URLClassLoaderWithLookup classloader : additionalClassLoaders) {
                classGraph.addClassLoader(classloader);
            }
        }

        try (final ScanResult result = classGraph.scan()) {
            final ClassInfoList classInfos =
                    result.getClassesWithAnnotation(ConfigData.class.getName());
            classInfos.stream()
                    .map(classInfo -> (Class<? extends Record>) classInfo.loadClass())
                    .forEach(configurationBuilder::withConfigDataType);
        }
    }
}
