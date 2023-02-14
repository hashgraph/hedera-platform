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
package com.swirlds.cli;

import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** The Swirlds Platform CLI. */
@Command(
        name = "pcli",
        version = "0.34.0",
        mixinStandardHelpOptions = true,
        description = "Miscellaneous platform utilities.")
public class PlatformCli extends AbstractCommand {

    /** Set the paths where jar files should be loaded from. */
    @CommandLine.Option(
            names = {"-l", "--load", "--cp"},
            scope = CommandLine.ScopeType.INHERIT,
            description =
                    "A path where additional java libs should be loaded from. Can be a path to a"
                            + " jar file or a path to a directory containing jar files.")
    private void setLoadPath(final List<Path> loadPath) {
        throw buildParameterException(
                "The load path parameter is expected to be parsed prior to the JVM being "
                        + "started");
    }

    @CommandLine.Option(
            names = {"-j", "--jvm"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "An argument that will be passed to the JVM, e.g. '-Xmx10g'")
    private void setJvmArgs(final List<String> jvmArgs) {
        throw buildParameterException(
                "The jvm args parameter is expected to be parsed prior to the JVM being started. "
                        + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"-d", "--debug"},
            scope = CommandLine.ScopeType.INHERIT,
            description =
                    "Pause the JVM at startup, and wait until a debugger "
                            + "is attached to port 8888 before continuing.")
    private void setDebug(final boolean debug) {
        throw buildParameterException(
                "The debug parameter is expected to be parsed prior to the JVM being started. "
                        + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"-m", "--memory"},
            scope = CommandLine.ScopeType.INHERIT,
            description =
                    "Set the amount of memory to allocate to the JVM, in gigabytes. "
                            + "'-m 16' is equivalent to '-j -Xmx16g'.")
    private void setJvmArgs(final int memory) {
        throw buildParameterException(
                "The memory parameter is expected to be parsed prior to the JVM being started. "
                        + "This argument is included here for documentation purposes only.");
    }

    /** Set the log4j path. */
    @CommandLine.Option(
            names = {"-L", "--log4j"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "The path where the log4j configuration file can be found.")
    private void setLog4jPath(final Path log4jPath) {
        if (Files.exists(log4jPath)) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.setConfigLocation(log4jPath.toUri());
        } else {
            throw buildParameterException("File " + log4jPath + " does not exist.");
        }
    }

    /**
     * Describes a subcommand.
     *
     * @param subcommandClass the class of the subcommand
     * @param parentClass the parent command of the subcommand
     */
    private record Subcommand(Class<?> subcommandClass, Class<?> parentClass) {}

    /**
     * Walk the class graph and find all classes that implement {@link SubcommandOf}.
     *
     * @return a list of all classes annotated as sub-commands
     */
    private static List<Subcommand> getSubcommands() {
        final ClassGraph classGraph = new ClassGraph().enableClassInfo().enableAnnotationInfo();

        final List<Subcommand> subcommands = new ArrayList<>();

        try (final ScanResult scanResult = classGraph.scan()) {
            for (final ClassInfo classInfo :
                    scanResult.getClassesWithAnnotation(SubcommandOf.class.getName())) {

                final Class<?> subcommandClass = classInfo.loadClass();
                final SubcommandOf subcommandOf =
                        (SubcommandOf)
                                classInfo
                                        .getAnnotationInfo(SubcommandOf.class.getName())
                                        .loadClassAndInstantiate();
                final Class<?> parentClass = subcommandOf.value();

                subcommands.add(new Subcommand(subcommandClass, parentClass));
            }
        }

        return subcommands;
    }

    /**
     * Build a map of subcommands to their parents.
     *
     * @param subcommands a list of subcommands
     * @return a map of subcommands to their parents
     */
    private static Map<Class<?> /* subcommand class */, Class<?> /* parent class */> buildParentMap(
            final List<Subcommand> subcommands) {

        final Map<Class<?>, Class<?>> map = new HashMap<>();
        subcommands.forEach(
                subcommand -> map.put(subcommand.subcommandClass, subcommand.parentClass));
        return map;
    }

    /**
     * Build command lines for all commands/subcommands and return a map between each class and its
     * associated command line object.
     *
     * @param subcommands a list of subcommands
     * @return a map from command class to corresponding CommandLine object
     */
    private static Map<Class<?> /* subcommand class */, CommandLine /* subcommand CommandLine */>
            buildCommandLines(final List<Subcommand> subcommands) {

        final Map<Class<?>, CommandLine> map = new HashMap<>();
        map.put(PlatformCli.class, new CommandLine(PlatformCli.class));
        subcommands.forEach(
                subcommand ->
                        map.put(
                                subcommand.subcommandClass,
                                new CommandLine(subcommand.subcommandClass)));
        return map;
    }

    /**
     * Link all subcommands with their parent command lines.
     *
     * @param parentMap a map of subcommand class to parent class
     * @param commandLineMap a map of command to corresponding CommandLine object
     */
    private static void linkCommandLines(
            Map<Class<?> /* subcommand class */, Class<?> /* parent class */> parentMap,
            Map<Class<?> /* subcommand class */, CommandLine /* subcommand CommandLine */>
                    commandLineMap) {

        parentMap.keySet().stream()
                .sorted(Comparator.comparing(Class::toString))
                .forEachOrdered(
                        command -> {
                            final CommandLine parentCommandLine =
                                    commandLineMap.get(parentMap.get(command));
                            if (parentCommandLine == null) {
                                return;
                            }

                            parentCommandLine.addSubcommand(commandLineMap.get(command));
                        });
    }

    /**
     * Get the root command line.
     *
     * @param commandLineMap a map of command to corresponding CommandLine object
     * @return the root command line
     */
    private static CommandLine getRootCommandLine(
            Map<Class<?> /* subcommand class */, CommandLine /* subcommand CommandLine */>
                    commandLineMap) {
        return commandLineMap.get(PlatformCli.class);
    }

    /** Walk the class graph and register any and all subcommands. */
    private static CommandLine buildCommandLine() {
        final List<Subcommand> subcommands = getSubcommands();
        final Map<Class<?>, Class<?>> parentMap = buildParentMap(subcommands);
        final Map<Class<?>, CommandLine> commandLineMap = buildCommandLines(subcommands);
        linkCommandLines(parentMap, commandLineMap);
        return getRootCommandLine(commandLineMap);
    }

    /**
     * Main entrypoint for the platform CLI.
     *
     * @param args program arguments
     */
    public static void main(final String[] args) {
        System.exit(buildCommandLine().execute(args));
    }
}
