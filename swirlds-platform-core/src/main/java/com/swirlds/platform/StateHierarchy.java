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
package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;

import com.swirlds.common.utility.CommonUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.swing.JPanel;

/**
 * Maintain all the metadata about the state hierarchy, which has 4 levels: (app, swirld, member,
 * state). This includes the list of every app installed locally. For each app, it includes the list
 * of all known swirlds running on it. For each swirld, it maintains the list of all known members
 * stored locally. For each member it maintains the list of all states stored locally. Each app,
 * swirld, member, and state also stores its name, and its parent in the hierarchy.
 */
class StateHierarchy {
    /** list of the known apps, each of which may have some members, swirlds, and states */
    List<InfoApp> apps = new ArrayList<>();

    /**
     * Create the state hierarchy, by finding all .jar files in data/apps/ and also adding in a
     * virtual one named fromAppName if that parameter is non-null.
     *
     * @param fromAppName the name of the virtual data/apps/*.jar file, or null if none.
     */
    public StateHierarchy(final String fromAppName) {
        final Path appsDirPath = getAbsolutePath().resolve("data").resolve("apps");
        final List<Path> appFiles =
                rethrowIO(
                        () ->
                                Files.list(appsDirPath)
                                        .filter(path -> path.toString().endsWith(".jar"))
                                        .toList());
        final List<String> names = new ArrayList<>();

        if (fromAppName != null) { // if there's a virtual jar, list it first
            names.add(fromAppName);
            final List<Path> toDelete =
                    rethrowIO(
                            () ->
                                    Files.list(appsDirPath)
                                            .filter(
                                                    path ->
                                                            path.getFileName()
                                                                    .toString()
                                                                    .equals(fromAppName))
                                            .toList());
            if (toDelete != null) {
                for (final Path file : toDelete) {
                    rethrowIO(() -> Files.deleteIfExists(file));
                }
            }
        }

        if (appFiles != null) {
            for (final Path app : appFiles) {
                names.add(getMainClass(app.toAbsolutePath().toString()));
            }
        }

        names.sort(null);
        for (String name : names) {
            name = name.substring(0, name.length() - 4);
            apps.add(new InfoApp(name));
        }
    }

    private static String getMainClass(final String appJarPath) {
        String mainClassname;
        try (final JarFile jarFile = new JarFile(appJarPath)) {
            final Manifest manifest = jarFile.getManifest();
            final Attributes attributes = manifest.getMainAttributes();
            mainClassname = attributes.getValue("Main-Class");
            return mainClassname;
        } catch (Exception ex) {
            CommonUtils.tellUserConsolePopup("ERROR", "ERROR: Couldn't load app " + appJarPath);
            return null;
        }
    }

    /**
     * Get the InfoApp for an app stored locally, given the name of the jar file (without the
     * ".jar").
     *
     * @param name name the jar file, without the ".jar" at the end
     * @return the InfoApp for that app
     */
    InfoApp getInfoApp(String name) {
        for (InfoApp app : apps) {
            if (name.equals(app.name)) {
                return app;
            }
        }
        return null;
    }

    /**
     * The class that all 4 levels of the hierarchy inherit from: InfoApp, InfoSwirld, InfoMember,
     * InfoState. This holds information common to all of them, such as the name, and the GUI
     * component that represents it in the browser window.
     */
    static class InfoEntity {
        /** name of this entity */
        String name;
        /**
         * the JPanel that shows this entity in the browser window (Swirlds tab), or null if none
         */
        JPanel panel;
    }

    /** Metadata about an app that is installed locally. */
    static class InfoApp extends InfoEntity {
        List<InfoSwirld> swirlds = new ArrayList<InfoSwirld>(); // children

        public InfoApp(String name) {
            this.name = name;
        }
    }

    /** Metadata about a swirld running on an app. */
    static class InfoSwirld extends InfoEntity {
        InfoApp app; // parent
        List<InfoMember> members = new ArrayList<InfoMember>(); // children

        Reference swirldId;

        public InfoSwirld(InfoApp app, byte[] swirldIdBytes) {
            this.app = app;
            this.swirldId = new Reference(swirldIdBytes);
            name = "Swirld " + swirldId.to62Prefix();
            app.swirlds.add(this);
        }
    }

    /** Metadata about a member in a swirld running on an app. */
    static class InfoMember extends InfoEntity {
        InfoSwirld swirld; // parent
        List<InfoState> states = new ArrayList<InfoState>(); // children

        long memberId;
        SwirldsPlatform platform;

        public InfoMember(InfoSwirld swirld, long memberId, SwirldsPlatform platform) {
            this.swirld = swirld;
            this.memberId = memberId;
            this.platform = platform;
            this.name =
                    platform.getAddress().getNickname()
                            + " - " //
                            + platform.getAddress().getSelfName();
            swirld.members.add(this);
        }
    }

    /** Metadata about a state stored by a member in a swirld running on an app. */
    static class InfoState extends InfoEntity {
        InfoMember member;

        public InfoState(InfoMember member, String name) {
            this.member = member;
            this.name = name;
            member.states.add(this);
        }
    }
}
