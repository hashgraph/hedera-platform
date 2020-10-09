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
package com.swirlds.platform;

import com.swirlds.common.CommonUtils;

import javax.swing.JPanel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Maintain all the metadata about the state hierarchy, which has 4 levels: (app, swirld, member, state).
 * This includes the list of every app installed locally. For each app, it includes the list of all known
 * swirlds running on it. For each swirld, it maintains the list of all known members stored locally. For
 * each member it maintains the list of all states stored locally. Each app, swirld, member, and state also
 * stores its name, and its parent in the hierarchy.
 */
class StateHierarchy {
	/** list of the known apps, each of which may have some members, swirlds, and states */
	List<InfoApp> apps = new ArrayList<InfoApp>();

	/**
	 * Create the state hierarchy, by finding all .jar files in data/apps/ and also adding in a virtual one
	 * named fromAppName if that parameter is non-null.
	 *
	 * @param fromAppName
	 * 		the name of the virtual data/apps/*.jar file, or null if none.
	 * @param fromAppMain
	 * 		the class of the virtual SwirldMain, or null if none.
	 */
	public StateHierarchy(String fromAppName, Class<?> fromAppMain) {
		File appsDirPath = CommonUtils.canonicalFile("data", "apps");
		File[] appFiles = appsDirPath
				.listFiles((dir, name) -> name.endsWith(".jar"));
		List<String> names = new ArrayList<String>();
		if (fromAppName != null) { // if there's a virtual jar, list it first
			names.add(fromAppName);
			File[] toDelete = appsDirPath
					.listFiles((dir, name) -> name.equals(fromAppName));
			for (File file : toDelete) {
				file.delete(); // deletes if the virtual file also really exists. Else does nothing.
			}
		}
		if (appFiles == null) {
			return;
		}
		for (File app : appFiles) {
			names.add(getMainClass(app.getAbsolutePath()));
		}
		names.sort(null);
		for (String name : names) {
			name = name.substring(0, name.length() - 4);
			apps.add(new InfoApp(name));
		}
	}

	private String getMainClass(String appJarPath) {
		String mainClassname;
		try {
			JarFile jarFile = new JarFile(appJarPath);
			Manifest manifest = jarFile.getManifest();
			Attributes attributes = manifest
					.getMainAttributes();
			mainClassname = attributes.getValue("Main-Class");
			jarFile.close();
			return mainClassname;
		} catch (Exception ex) {
			CommonUtils.tellUserConsolePopup("ERROR",
					"ERROR: Couldn't load app " + appJarPath);
			return null;
		}
	}

	/**
	 * Get the InfoApp for an app stored locally, given the name of the jar file (without the ".jar").
	 *
	 * @param name
	 * 		name the jar file, without the ".jar" at the end
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
	 * InfoState. This holds information common to all of them, such as the name, and the GUI component that
	 * represents it in the browser window.
	 */
	static class InfoEntity {
		/** name of this entity */
		String name;
		/** the JPanel that shows this entity in the browser window (Swirlds tab), or null if none */
		JPanel panel;
	}

	/**
	 * Metadata about an app that is installed locally.
	 */
	static class InfoApp extends InfoEntity {
		List<InfoSwirld> swirlds = new ArrayList<InfoSwirld>(); // children

		public InfoApp(String name) {
			this.name = name;
		}
	}

	/**
	 * Metadata about a swirld running on an app.
	 */
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

	/**
	 * Metadata about a member in a swirld running on an app.
	 */
	static class InfoMember extends InfoEntity {
		InfoSwirld swirld; // parent
		List<InfoState> states = new ArrayList<InfoState>(); // children

		long memberId;
		AbstractPlatform platform;

		public InfoMember(InfoSwirld swirld, long memberId, AbstractPlatform platform) {
			this.swirld = swirld;
			this.memberId = memberId;
			this.platform = platform;
			this.name = platform.getAddress().getNickname() + " - "//
					+ platform.getAddress().getSelfName();
			swirld.members.add(this);
		}
	}

	/**
	 * Metadata about a state stored by a member in a swirld running on an app.
	 */
	static class InfoState extends InfoEntity {
		InfoMember member;

		public InfoState(InfoMember member, String name) {
			this.member = member;
			this.name = name;
			member.states.add(this);
		}
	}
}
