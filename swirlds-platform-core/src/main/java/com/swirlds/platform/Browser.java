/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform;

import com.swirlds.common.StartupTime;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.settings.TemporaryFileSettingsFactory;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.fchashmap.FCHashMapSettingsFactory;
import com.swirlds.jasperdb.settings.JasperDbSettingsFactory;
import com.swirlds.logging.payloads.NodeAddressMismatchPayload;
import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.p2p.portforwarding.PortForwarder;
import com.swirlds.p2p.portforwarding.PortMapping;
import com.swirlds.platform.StateHierarchy.InfoApp;
import com.swirlds.platform.StateHierarchy.InfoMember;
import com.swirlds.platform.StateHierarchy.InfoSwirld;
import com.swirlds.platform.WinBrowser.ScrollableJPanel;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.KeyLoadingException;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.state.address.AddressBookSettingsFactory;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import com.swirlds.virtualmap.VirtualMapSettingsFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import javax.swing.JFrame;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.logging.LogMarker.CERTIFICATES;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JVM_PAUSE_WARN;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.Settings.JVMPauseDetectorSleepMs;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;
import static com.swirlds.platform.state.address.AddressBookUtils.getOwnHostCount;
import static com.swirlds.platform.system.SystemExitReason.NODE_ADDRESS_MISMATCH;
import static com.swirlds.platform.system.SystemUtils.exitSystem;

/**
 * The Browser that launches the Platforms that run the apps. The Browser has only one public method, which
 * normally does nothing. See the javadoc on the main method for how it can be useful for an app to call it
 * during app development.
 * <p>
 * All class member variables and methods of this class are static, and it can't be instantiated.
 */
public abstract class Browser {
	// Each member is represented by an AddressBook entry in config.txt. On a given computer, a single java
	// process runs all members whose listed internal IP address matches some address on that computer. That
	// Java process will instantiate one Platform per member running on that machine. But there will be only
	// one static Browser that they all share.
	//
	// Every member, whatever computer it is running on, listens on 0.0.0.0, on its internal port. Every
	// member connects to every other member, by computing its IP address as follows: If that other member
	// is also on the same host, use 127.0.0.1. If it is on the same LAN[*], use its internal address.
	// Otherwise, use its external address.
	//
	// This way, a single config.txt can be shared across computers unchanged, even if, for example, those
	// computers are on different networks in Amazon EC2.
	//
	// [*] Two members are considered to be on the same LAN if their listed external addresses are the same.

	/** all the Platform objects currently running on this machine */
	static final Collection<SwirldsPlatform> platforms = new LinkedList<>();
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();// must be after the configuration load
	/** the "name" passed in by the app, when it was launched from Eclipse or other IDE (or null if none) */
	private static final String FROM_APP_NAME = null;
	/** the "main" passed in by the app, when it was launched from Eclipse or other IDE (or null if none) */
	private static final Class<?> fromAppMain = null;
	/** the primary window used by Browser */
	static WinBrowser browserWindow = null;
	/* the number of pixels between the edges of a window and interior region that can be used */
	static Insets insets;
	/** metadata about all known apps, swirlds, members, signed states */
	static StateHierarchy stateHierarchy = null;
	/** has the browser started up yet? */
	private static boolean launched = false;
	/** the thread for each Platform.run */
	private static Thread[] platformRunThreads;
	/**
	 * whether a savedState has been loaded.
	 * if it is true, NotificationEngine will send a StateLoadedFromDiskNotification
	 */
	private static boolean loadedSavedState = false;

	/**
	 * freezeTime in seconds;
	 * when the node is started from genesis, and this value is positive,
	 * set this node's freezeTime to be an Instant with this many epoch seconds
	 */
	private static long genesisFreezeTime = -1;

	/**
	 * Prevent this class from being instantiated.
	 */
	protected Browser() {
	}

	/**
	 * Check whether a saved state file has been loaded
	 */
	public static boolean isLoadedSavedState() {
		return loadedSavedState;
	}

	/**
	 * Shut down the browser and all platforms
	 */
	static void stopBrowser() {
		exitSystem(SystemExitReason.BROWSER_WINDOW_CLOSED, true);
	}

	/**
	 * Parses the command line arguments passed to the main method.
	 *
	 * @param args
	 * 		the arguments passed to the main method
	 * @return the {@link Set} of local nodes to be started, if specified on the command line
	 */
	private static Set<Integer> parseCommandLine(final String[] args) {
		final Set<Integer> localNodesToStart = new HashSet<>();

		// Parse command line arguments (rudimentary parsing)
		String currentOption = null;
		if (args != null) {
			for (final String item : args) {
				final String arg = item.trim().toLowerCase();
				switch (arg) {
					case "-local":
					case "-log":
						currentOption = arg;
						break;
					default:
						if (currentOption != null) {
							switch (currentOption) {
								case "-local":
									try {
										localNodesToStart
												.add(Integer.parseInt(arg));
									} catch (final NumberFormatException ex) {
										// Intentionally suppress the NumberFormatException
									}
									break;
								case "-log":
									Settings.logPath = getAbsolutePath(arg);
									break;
							}
						}
				}
			}
		}

		return localNodesToStart;
	}

	/**
	 * Initializes the logging subsystem if a log4j2.xml file is present in the current working directory.
	 */
	private static void startLoggingFramework() {
		// Initialize the log4j2 configuration and logging subsystem if a log4j2.xml file is present in the current
		// working directory
		try {
			if (Files.exists(Settings.logPath)) {
				final LoggerContext context = (LoggerContext) LogManager
						.getContext(false);
				context.setConfigLocation(Settings.logPath.toUri());
			}
		} catch (final Exception e) {
			LogManager.getLogger(Browser.class).fatal("Unable to load log context", e);
			System.err.println("FATAL Unable to load log context: " + e);
		}
	}

	/**
	 * Start the browser running, if it isn't already running. If it's already running, then Browser.main
	 * does nothing. Normally, an app calling Browser.main has no effect, because it was the browser that
	 * launched the app in the first place, so the browser is already running.
	 * <p>
	 * But during app development, it can be convenient to give the app a main method that calls
	 * Browser.main. If there is a config.txt file that says to run the app that is being developed, then
	 * the developer can run the app within Eclipse. Eclipse will call the app's main() method, which will
	 * call the browser's main() method, which launches the browser. The app's main() then returns, and the
	 * app stops running. Then the browser will load the app (because of the config.txt file) and let it run
	 * normally within the browser. All of this happens within Eclipse, so the Eclipse debugger works, and
	 * Eclipse breakpoints within the app will work.
	 *
	 * @param args
	 * 		args is ignored, and has no effect
	 */
	public static synchronized void main(final String[] args) {
		StartupTime.markStartupTime();

		// This set contains the nodes set by the command line to start, if none are passed, then IP
		// addresses will be compared to determine which node to start
		final Set<Integer> localNodesToStart = parseCommandLine(args);

		// Initialize the log4j2 configuration and logging subsystem
		startLoggingFramework();
		log.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

		try {
			if (launched) {
				return;
			}
			launched = true;

			// discover the inset size and set the look and feel
			if (!GraphicsEnvironment.isHeadless()) {
				UIManager.setLookAndFeel(
						UIManager.getCrossPlatformLookAndFeelClassName());
				final JFrame jframe = new JFrame();
				jframe.setPreferredSize(new Dimension(200, 200));
				jframe.pack();
				insets = jframe.getInsets();
				jframe.dispose();
			}

			// Read from data/settings.txt (where data is in same directory as .jar, usually sdk/) to change
			// the default settings given in the Settings class. This file won't normally exist. But it can
			// be used for testing and debugging. This is NOT documented for users.
			//
			// Also, if the settings.txt file exists, then after reading it and changing the settings, write
			// all the current settings to settingsUsed.txt, some of which might have been changed by
			// settings.txt
			Settings.loadSettings();

			//Provide swirlds.common the settings it needs via the SettingsCommon class
			populateSettingsCommon();

			// find all the apps in data/apps and stored states in data/states
			stateHierarchy = new StateHierarchy(FROM_APP_NAME);

			// read from config.txt (in same directory as .jar, usually sdk/)
			// to fill in the following three variables, which define the
			// simulation to run.

			try {

				if (Files.exists(Settings.configPath)) {
					CommonUtils.tellUserConsole(
							"Reading the configuration from the file:   "
									+ Settings.configPath);
				} else {
					CommonUtils.tellUserConsole(
							"A config.txt file could be created here:   "
									+ Settings.configPath);
					return;
				}
				// instantiate all Platform objects, which each instantiates a Statistics object
				log.debug(STARTUP.getMarker(),
						"About to run startPlatforms()");
				startPlatforms(localNodesToStart);

				// if the settings.txt file exists, then write the settingsUsed.txt file.
				// if it doesn't exist, then we will never reach this point, so we won't create
				// settingsUsed.txt.
				if (Settings.settingsTxtExists()) {
					Settings.writeSettingsUsed();
				}

				// create the browser window, which uses those Statistics objects
				showBrowserWindow();
				for (final Frame f : Frame.getFrames()) {
					if (!f.equals(browserWindow)) {
						f.toFront();
					}
				}

				CommonUtils.tellUserConsole(
						"This computer has an internal IP address:  "
								+ Network.getInternalIPAddress());
				log.trace(STARTUP.getMarker(), "All of this computer's addresses: {}",
						() -> (Arrays.toString(Network.getOwnAddresses2())));

				// port forwarding
				if (Settings.doUpnp) {
					final List<PortMapping> portsToBeMapped = new LinkedList<>();
					synchronized (Browser.platforms) {
						for (final Platform p : platforms) {
							final Address address = p.getAddress();
							final String ip = Address
									.ipString(address.getListenAddressIpv4());
							final PortMapping pm = new PortMapping(ip,
									// ip address (not used by portMapper, which tries all external port
									// network
									// interfaces)
									// (should probably use ports >50000, this is considered the dynamic
									// range)
									address.getPortInternalIpv4(),
									address.getPortExternalIpv4(),// internal port

									PortForwarder.Protocol.TCP// transport protocol
							);
							portsToBeMapped.add(pm);
						}
					}
					Network.doPortForwarding(portsToBeMapped);
				}
			} catch (final Exception e) {
				log.error(EXCEPTION.getMarker(), "", e);
			}

		} catch (final Exception e) {
			log.error(EXCEPTION.getMarker(), "", e);
		}

		log.debug(STARTUP.getMarker(), "main() finished");
	}

	/**
	 * Instantiate and start the deadlock detector timer, if enabled via the {@link Settings#deadlockCheckPeriod}
	 * setting.
	 */
	private static void startDeadlockDetector() {
		// Once a second, check for deadlocks (which only takes 1 ms).
		// If there is a deadlock, print all the deadlocked threads to the console, then log them.
		final Timer deadlockDetectorTimer;
		if (Settings.deadlockCheckPeriod > 0) { // if it's -1, then never check and don't create the thread
			deadlockDetectorTimer = new Timer("deadlock detector timer", true);
			deadlockDetectorTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					final String err = Utilities.deadlocks();
					if (err != null) {
						// println lets the app dev see there was a deadlock, in case it's their fault
						CommonUtils.tellUserConsole(err);
						log.error(EXCEPTION.getMarker(), err);
					}
				}
			}, 0, Settings.deadlockCheckPeriod); // check for deadlocks periodically, starting now
		}
	}

	/**
	 * Instantiate and start the thread dump generator, if enabled via the {@link Settings#threadDumpPeriodMs}
	 * setting.
	 */
	private static void startThreadDumpGenerator() {
		if (Settings.threadDumpPeriodMs > 0) {
			final Path dir = getAbsolutePath(Settings.threadDumpLogDir);
			if (!Files.exists(dir)) {
				rethrowIO(() -> Files.createDirectories(dir));
			}
			ThreadDumpGenerator.generateThreadDumpAtIntervals(Settings.threadDumpPeriodMs);
		}
	}

	/**
	 * Instantiate and start the JVMPauseDetectorThread, if enabled via the {@link Settings#JVMPauseDetectorSleepMs}
	 * setting.
	 */
	private static void startJVMPauseDetectorThread() {
		if (JVMPauseDetectorSleepMs > 0) {
			final JVMPauseDetectorThread jvmPauseDetectorThread = new JVMPauseDetectorThread(
					(pauseTimeMs, allocTimeMs) -> {
						if (pauseTimeMs > Settings.JVMPauseReportMs) {
							log.warn(JVM_PAUSE_WARN.getMarker(),
									"jvmPauseDetectorThread detected JVM paused for {} ms, allocation pause {} ms",
									pauseTimeMs, allocTimeMs);
						}
					}, JVMPauseDetectorSleepMs);
			jvmPauseDetectorThread.start();
			log.debug(STARTUP.getMarker(), "jvmPauseDetectorThread started");
		}
	}

	/**
	 * Parses the configuration file specified by the {@link Settings#configPath} setting, configures all appropriate
	 * system settings, and returns a generic {@link ApplicationDefinition}.
	 *
	 * @param localNodesToStart
	 * 		the {@link Set} of local nodes to be started, if specified
	 * @return an {@link ApplicationDefinition} specifying the application to be loaded and all related configuration
	 * @throws UnknownHostException
	 * 		if no IP address for the {@code host} could be found, or if a scope_id was specified for a global IPv6
	 * 		address.
	 * @throws SocketException
	 * 		if there are any errors getting the addresses
	 * @throws ConfigurationException
	 * 		if the configuration file specified by {@link Settings#configPath} does not exist
	 */
	public static ApplicationDefinition loadConfigFile(final Set<Integer> localNodesToStart)
			throws UnknownHostException, SocketException, ConfigurationException {

		/* symbolic part of the name of the swirld, passed to its Platform */
		String swirldName = "";
		/* parameters from one line of config.txt */
		String[] lineParameters = null;
		/* parameters from the app line of config.txt */
		String[] appParameters = null;
		/* name of app jar file, such as Example.jar */
		String appJarFilename = "";
		/* name of app SwirldMain class, such as a.b.c.ExampleMain */
		String mainClassname = "";
		/* the path to the application jar file */
		Path appJarPath = null;

		/* interim list of Address instances */
		final List<Address> bookData = Collections
				.synchronizedList(new ArrayList<>());

		// Load config.txt file, parse application jar file name, main class name, address book, and parameters
		if (!Files.exists(Settings.configPath)) {
			log.error(EXCEPTION.getMarker(),
					"ERROR: Browser.startPlatforms called on non-existent config.txt");
			throw new ConfigurationException("ERROR: Browser.startPlatforms called on non-existent config.txt");
		}

		try (final Scanner scanner = new Scanner(Settings.configPath, StandardCharsets.UTF_8.name())) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				final int pos = line.indexOf("#");
				if (pos > -1) {
					line = line.substring(0, pos);
				}
				line = line.trim();
				if (!line.isEmpty()) {
					lineParameters = splitLine(line);
					final int len = Math.max(10, lineParameters.length);
					// pars is the comma-separated parameters, trimmed, lower-cased, then padded with "" to have
					// at least 10 parameters
					final String[] pars = new String[len];
					final String[] parsOriginalCase = new String[len];
					for (int i = 0; i < len; i++) {
						parsOriginalCase[i] = i >= lineParameters.length ? ""
								: lineParameters[i].trim();
						pars[i] = parsOriginalCase[i].toLowerCase(Locale.ENGLISH);
					}
					switch (pars[0]) {
						case "app":
							if (!appJarFilename.isEmpty()) {
								CommonUtils.tellUserConsolePopup("ERROR",
										"ERROR: config.txt had more than one line starting with \"app\"."
												+ " All but the last will be ignored.");
							}
							appParameters = lineParameters.clone();
							// the line is: app, jarFilename, optionalParameters
							appJarFilename = lineParameters[1];
							if (appJarFilename.equals(Browser.FROM_APP_NAME)) {
								// this is the virtual .jar file because running in Eclipse or IDE
								mainClassname = Browser.fromAppMain.getName();
								break;
							}
							// this is a real .jar file, so load from data/apps/
							appJarPath = Settings.appsDirPath.resolve(appJarFilename);
							mainClassname = "";
							try (final JarFile jarFile = new JarFile(appJarPath.toFile())) {
								final Manifest manifest = jarFile.getManifest();
								final Attributes attributes = manifest
										.getMainAttributes();
								mainClassname = attributes.getValue("Main-Class");
							} catch (final Exception e) {
								CommonUtils.tellUserConsolePopup("ERROR",
										"ERROR: Couldn't load app " + appJarPath);
								log.error(EXCEPTION.getMarker(),
										"Couldn't find Main-Class name in jar file {}", appJarPath, e);
							}
							break;
						case "address":
							// read an address line: address, nickname, selfname, stake, internal port,
							// internal IP, external port, externalIp

							// The set localNodesToStart contains the nodes set by the command line to start, if
							// none are passed, then IP addresses will be compared to determine which node to
							// start. If some are passed, then the IP addresses will be ignored. This must be
							// considered for ownHost
							final boolean isOwnHost = (localNodesToStart.size() == 0
									&& Network
									.isOwn(InetAddress.getByName(pars[4])))
									|| localNodesToStart.contains(bookData.size());
							bookData.add(new Address(//
									bookData.size(), // Id
									parsOriginalCase[1], // nickname
									parsOriginalCase[2], // selfName
									Long.parseLong(pars[3]), // stake
									isOwnHost, // ownHost
									InetAddress.getByName(pars[4]).getAddress(), // addressInternalIpv4
									Integer.parseInt(pars[5]), // portInternalIpv4
									InetAddress.getByName(pars[6]).getAddress(), // addressExternalIpv4
									Integer.parseInt(pars[7]), // portExternalIpv4
									null, // addressInternalIpv6
									-1, // portInternalIpv6
									null, // addressExternalIpv6
									-1, // portExternalIpv6
									null, // sigPublicKey
									null, // encPublicKey
									(SerializablePublicKey) null, // agreePublicKey
									parsOriginalCase[8] // memo, optional
							));
							/**
							 * the Id parameter above is the member ID, and in the current software, it is equal
							 * to the position of the address in the list of addresses in the address book, and
							 * is also equal to the comm ID. The comm ID is currently set to the position of the
							 * address in the config.txt file: the first one has comm ID 0, the next has comm ID
							 * 1, and so on. In future versions of the software, each member ID can be any long,
							 * and they may not be contiguous numbers. But the comm IDs must always be the
							 * numbers from 0 to N-1 for N members. The comm IDs can then be used with the
							 * RandomGraph to select which members to connect to.
							 */

							break;
						case "swirld":
							swirldName = parsOriginalCase[1];
							break;
						case "tls":
							// "TLS, ON" turns on TLS (or: true, 1, yes, t, y)
							// "TLS, OFF" turns off TLS (or: false, 0, no, f, n)
							Settings.useTLS = lineParameters.length < 2 || Utilities.parseBoolean(pars[1]);
							break;
						case "maxsyncs": {
							// maximum number of simultaneous syncs initiated by this member
							// (the max that can be received will be this plus 1)
							final int n = Integer.parseInt(pars[1]);
							Settings.maxOutgoingSyncs = Math.max(n, 1);
							break;
						}
						case "transactionmaxbytes": {
							// maximum number of bytes allowed per transaction
							final int n = Integer.parseInt(pars[1]);
							Settings.transactionMaxBytes = Math.max(n, 100);
							break;
						}
						case "iptos":
							// IPv4 Type of Service (0 to 255, or -1 to not use IP_TOS)
							Settings.socketIpTos = Integer.parseInt(pars[1]);
							break;
						case "savestateperiod":
							Settings.state.saveStatePeriod = Integer.parseInt(pars[1]);
							break;
						case "waitatstartup":
							// "waitatstartup, ON" turns on TLS (or: true, 1, yes, t, y)
							// "waitatstartup, OFF" turns off TLS (or: false, 0, no, f, n)
							if (lineParameters.length >= 2) {
								Settings.waitAtStartup = Utilities.parseBoolean(pars[1]);
							} else {
								CommonUtils.tellUserConsolePopup("Error", "waitAtStartup needs a parameter");
							}
							break;
						case "genesisfreezetime":
							genesisFreezeTime = Long.parseLong(pars[1]);
							break;
						default:
							CommonUtils.tellUserConsolePopup("Error", "\"" + pars[0]
									+ "\" in config.txt isn't a recognized first parameter for a line");
							break;
					}
				}
			}
		} catch (final FileNotFoundException ex) { // this should never happen
			log.error(EXCEPTION.getMarker(),
					"Config.txt file was not found but File#exists() claimed the file does exist", ex);
		} catch (final IOException ex) {
			throw new UncheckedIOException(ex);
		}


		if (appJarFilename.isEmpty()) {
			CommonUtils.tellUserConsolePopup("ERROR",
					"ERROR: config.txt did not have a valid line starting with \"app\"."
							+ " No app will run.");
		}

		if (bookData.size() == 0) {
			CommonUtils.tellUserConsolePopup("ERROR",
					"ERROR: config.txt did not have a valid line starting with \"address\"."
							+ " Nothing will run.");
		}


		return new ApplicationDefinition(swirldName, appParameters, appJarFilename, mainClassname, appJarPath,
				bookData);
	}

	private static void createLocalPlatforms(final ApplicationDefinition appDefinition, final Crypto[] crypto,
			final InfoSwirld infoSwirld, final SwirldAppLoader appLoader) {

		final AddressBook addressBook = appDefinition.getAddressBook();

		final int fontSize = 12; // 14 is good for 4 windows

		int ownHostIndex = 0;

		for (int i = 0; i < addressBook.getSize(); i++) {
			if (addressBook.getAddress(i).isOwnHost()) {
				final SwirldsPlatform platform = new SwirldsPlatform(
						// window index
						ownHostIndex,
						// parameters from the app line of the config.txt file
						Arrays.copyOfRange(appDefinition.getAppParameters(), 2,
								appDefinition.getAppParameters().length),
						// all key pairs and CSPRNG state for this member
						crypto[i],
						// the ID for this swirld (immutable since creation of this swirld)
						appDefinition.getSwirldId(),
						// address book index, which is the member ID
						NodeId.createMain(i),
						// copy of the address book,
						addressBook.copy(),
						// suggested font size for windows created
						fontSize,
						// name of the app's SwirldMain class
						appDefinition.getMainClassName(),
						// the name of this swirld
						appDefinition.getSwirldName(),
						// the loader for the user app
						appLoader);

				// check the disk for saved states and load them
				try {
					loadedSavedState |= platform.loadSavedStateFromDisk();
				} catch (final SignedStateLoadingException e) {
					log.error(EXCEPTION.getMarker(), "Saved state not loaded:", e);
					// if requireStateLoad is on, we exit. if not, we just log it
					if (Settings.requireStateLoad) {
						SystemUtils.exitSystem(SystemExitReason.SAVED_STATE_NOT_LOADED);
						return;
					}
				}

				// if genesisFreezeTime is positive, and the nodes start from genesis
				if (!loadedSavedState && genesisFreezeTime > 0) {
					platform.setGenesisFreezeTime(genesisFreezeTime);
				}

				// give infoMember and platform a reference to each other
				final InfoMember infoMember = new InfoMember(infoSwirld, i, platform);
				platform.setInfoMember(infoMember);

				platformRunThreads[ownHostIndex] = new ThreadConfiguration()
						.setDaemon(false)
						.setPriority(Settings.threadPriorityNonSync)
						.setNodeId((long) ownHostIndex)
						.setComponent(PLATFORM_THREAD_POOL_NAME)
						.setThreadName("platformRun")
						.setRunnable(platform::run)
						.build();

				ownHostIndex++;
				synchronized (Browser.platforms) {
					platforms.add(platform);
				}
			}
		}
	}

	/**
	 * Instantiate and run all the local platforms specified in the given config.txt file. This method reads
	 * in and parses the config.txt file.
	 *
	 * @throws UnknownHostException
	 * 		problems getting an IP address for another user
	 * @throws SocketException
	 * 		problems getting the IP address for self
	 * @throws AppLoaderException
	 * 		if there are issues loading the user app
	 * @throws ConstructableRegistryException
	 * 		if there are issues registering
	 *        {@link com.swirlds.common.constructable.RuntimeConstructable} classes
	 */
	private static void startPlatforms(final Set<Integer> localNodesToStart)
			throws UnknownHostException, SocketException, AppLoaderException, ConstructableRegistryException {


		// Initialize the deadlock detector if enabled via settings
		startDeadlockDetector();

		// Load config.txt file, parse application jar file name, main class name, address book, and parameters
		final ApplicationDefinition appDefinition;
		final AddressBook addressBook;
		try {
			appDefinition = loadConfigFile(localNodesToStart);
			addressBook = appDefinition.getAddressBook();
		} catch (final ConfigurationException ex) {
			return;
		}

		// If enabled, clean out the signed state directory. Needs to be done before the platform/state is started up,
		// as we don't want to delete the temporary file directory if it ends up being put in the saved state directory.
		if (Settings.state.cleanSavedStateDirectory) {
			SignedStateFileUtils.cleanStateDirectory(appDefinition.getMainClassName());
		}

		final int ownHostCount = getOwnHostCount(addressBook);
		log.info(STARTUP.getMarker(), "there are {} nodes with local IP addresses", ownHostCount);

		// if the local machine did not match any address in the address book then we should log an error and exit
		if (ownHostCount < 1) {
			final String externalIpAddress = (Network.getExternalIpAddress() != null) ?
					Network.getExternalIpAddress().getIpAddress() : null;
			log.error(EXCEPTION.getMarker(),
					new NodeAddressMismatchPayload(Network.getInternalIPAddress(), externalIpAddress));
			SystemUtils.exitSystem(NODE_ADDRESS_MISMATCH);
		}

		// the thread for each Platform.run
		// will create a new thread with a new Platform for each local address
		// general address number addIndex is local address number i
		platformRunThreads = new Thread[ownHostCount];
		appDefinition.setMasterKey(new byte[CryptoConstants.SYM_KEY_SIZE_BYTES]);
		appDefinition.setSwirldId(new byte[CryptoConstants.HASH_SIZE_BYTES]);


		// Create the various keys and certificates (which are saved in various Crypto objects).
		// Save the certificates in the trust stores.
		// Save the trust stores in the address book.
		//
		log.debug(STARTUP.getMarker(), "About do crypto instantiation");
		final Crypto[] crypto = initNodeSecurity(
				appDefinition.getAddressBook()
		);
		log.debug(STARTUP.getMarker(), "Done with crypto instantiation");

		// the AddressBook is not changed after this point, so we calculate the hash now
		CryptoFactory.getInstance().digestSync(addressBook);

		final InfoApp infoApp = stateHierarchy.getInfoApp(appDefinition.getApplicationName());
		final InfoSwirld infoSwirld = new InfoSwirld(infoApp, appDefinition.getSwirldId());

		log.debug(STARTUP.getMarker(), "Starting platforms");

		// Try to load the app
		final SwirldAppLoader appLoader;
		try {
			appLoader = SwirldAppLoader.loadSwirldApp(
					appDefinition.getMainClassName(),
					appDefinition.getAppJarPath()
			);
		} catch (final AppLoaderException e) {
			CommonUtils.tellUserConsolePopup("ERROR", e.getMessage());
			throw e;
		}

		// Register all RuntimeConstructable classes
		log.debug(STARTUP.getMarker(), "Scanning the classpath for RuntimeConstructable classes");
		final long start = System.currentTimeMillis();
		ConstructableRegistry.registerConstructables("", appLoader.getClassLoader());
		log.debug(STARTUP.getMarker(), "Done with registerConstructables, time taken {}ms",
				System.currentTimeMillis() - start);

		// Create all instances for all nodes that should run locally
		createLocalPlatforms(appDefinition, crypto, infoSwirld, appLoader);

		// Partially initialize the platforms before we dispatch the StateLoadedFromDiskNotification
		for (final SwirldsPlatform platform : platforms) {
			platform.initializeFirstStep();
		}

		// Notify listeners that loading state from disk has been completed successfully
		// The notification should come after appMain.init() so that the app has a chance to register a listener
		if (loadedSavedState) {
			NotificationFactory.getEngine().dispatch(
					StateLoadedFromDiskCompleteListener.class,
					new StateLoadedFromDiskNotification()
			);
		}

		// the platforms need to start after all the initial loading has been done
		for (final Thread platformRunThread : platformRunThreads) {
			platformRunThread.start();
		}

		// Initialize the thread dump generator, if enabled via settings
		startThreadDumpGenerator();

		// Initialize JVMPauseDetectorThread, if enabled via settings
		startJVMPauseDetectorThread();

		log.debug(STARTUP.getMarker(), "Done with starting platforms");
	}

	/**
	 * Split the given string on its commas, and trim each result
	 *
	 * @param line
	 * 		the string of comma-separated values to split
	 * @return the array of trimmed elements.
	 */
	static String[] splitLine(final String line) {
		final String[] elms = line.split(",");
		for (int i = 0; i < elms.length; i++) {
			elms[i] = elms[i].trim();
		}

		return elms;

	}

	/**
	 * Make the browser window visible. If it doesn't yet exist, then create it. Then switch to the given
	 * tab, with a component name of the form Browser.browserWindow.tab* such as
	 * Browser.browserWindow.tabCalls to switch to the "Calls" tab.
	 *
	 * @param comp
	 * 		the index of the tab to select
	 */
	static void showBrowserWindow(final ScrollableJPanel comp) {
		showBrowserWindow();
		Browser.browserWindow.goTab(comp);
	}

	/**
	 * Make the browser window visible. If it doesn't yet exist, then create it.
	 */
	static void showBrowserWindow() {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}
		if (browserWindow != null) {
			browserWindow.setVisible(true);
			return;
		}
		browserWindow = new WinBrowser();
	}

	protected static void populateSettingsCommon() {
		SettingsCommon.maxTransactionCountPerEvent = Settings.maxTransactionCountPerEvent;
		SettingsCommon.maxTransactionBytesPerEvent = Settings.maxTransactionBytesPerEvent;
		SettingsCommon.maxAddressSizeAllowed = Settings.maxAddressSizeAllowed;
		SettingsCommon.transactionMaxBytes = Settings.transactionMaxBytes;
		SettingsCommon.halfLife = Settings.halfLife;
		SettingsCommon.logStack = Settings.logStack;
		SettingsCommon.showInternalStats = Settings.showInternalStats;
		SettingsCommon.verboseStatistics = Settings.verboseStatistics;
		SettingsCommon.enableBetaMirror = Settings.enableBetaMirror;
		SettingsCommon.threadPriorityNonSync = Settings.threadPriorityNonSync;
		SettingsCommon.csvFileName = Settings.csvFileName;
		SettingsCommon.csvOutputFolder = Settings.csvOutputFolder;
		SettingsCommon.csvAppend = Settings.csvAppend;
		SettingsCommon.csvWriteFrequency = Settings.csvWriteFrequency;

		CryptoFactory.configure(Settings.crypto);
		ReconnectSettingsFactory.configure(Settings.reconnect);
		FCHashMapSettingsFactory.configure(Settings.fcHashMap);
		VirtualMapSettingsFactory.configure(Settings.virtualMap);
		AddressBookSettingsFactory.configure(Settings.addressBook);
		JasperDbSettingsFactory.configure(Settings.jasperDb);
		TemporaryFileSettingsFactory.configure(Settings.temporaryFiles);
	}

	static Crypto[] initNodeSecurity(final AddressBook addressBook) {
		final ExecutorService cryptoThreadPool = Executors.newFixedThreadPool(
				Settings.numCryptoThreads,
				new ThreadConfiguration()
						.setComponent("browser")
						.setThreadName("crypto-verify")
						.setDaemon(false)
						.buildFactory()
		);

		final Path keysDirPath = Settings.keysDirPath;
		final KeysAndCerts[] keysAndCerts;
		try {
			if (Settings.loadKeysFromPfxFiles) {
				try (final Stream<Path> list = Files.list(keysDirPath)) {
					CommonUtils.tellUserConsole(
							"Reading crypto keys from the files here:   " + list.filter(
									path -> path.getFileName().endsWith("pfx")).toList());
					log.debug(STARTUP.getMarker(), "About start loading keys");
					keysAndCerts = CryptoStatic.loadKeysAndCerts(addressBook, keysDirPath,
							Settings.crypto.getKeystorePassword().toCharArray());
					log.debug(STARTUP.getMarker(), "Done loading keys");
				}
			} else {
				// if there are no keys on the disk, then create our own keys
				CommonUtils.tellUserConsole(
						"Creating keys, because there are no files in " + keysDirPath);
				log.debug(STARTUP.getMarker(), "About to start creating generating keys");
				keysAndCerts = CryptoStatic.generateKeysAndCerts(addressBook, cryptoThreadPool);
				log.debug(STARTUP.getMarker(), "Done generating keys");
			}
		} catch (final InterruptedException | ExecutionException
					   | KeyStoreException | KeyLoadingException
					   | UnrecoverableKeyException | NoSuchAlgorithmException | IOException e) {
			log.error(EXCEPTION.getMarker(), "Exception while loading/generating keys", e);
			if (Utilities.isRootCauseSuppliedType(e, NoSuchAlgorithmException.class)
					|| Utilities.isRootCauseSuppliedType(e, NoSuchProviderException.class)) {
				CommonUtils.tellUserConsolePopup("ERROR",
						"ERROR: This Java installation does not have the needed cryptography " +
								"providers installed");
			}
			SystemUtils.exitSystem(SystemExitReason.KEY_LOADING_FAILED);
			throw new CryptographyException(e);// will never reach this line due to exit above
		}

		final String msg = Settings.loadKeysFromPfxFiles
				? "Certificate loaded: {}"
				: "Certificate generated: {}";
		Arrays.stream(keysAndCerts).filter(Objects::nonNull).forEach(
				k -> {
					log.debug(CERTIFICATES.getMarker(), msg, k.sigCert());
					log.debug(CERTIFICATES.getMarker(), msg, k.encCert());
					log.debug(CERTIFICATES.getMarker(), msg, k.agrCert());
				}
		);

		return Arrays.stream(keysAndCerts).map(kc -> new Crypto(kc, cryptoThreadPool)).toArray(Crypto[]::new);
	}
}
