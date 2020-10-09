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

import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.merkle.synchronization.ReconnectSettingsFactory;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.threading.StandardThreadFactory;
import com.swirlds.p2p.portforwarding.PortForwarder;
import com.swirlds.p2p.portforwarding.PortMapping;
import com.swirlds.platform.StateHierarchy.InfoApp;
import com.swirlds.platform.StateHierarchy.InfoMember;
import com.swirlds.platform.StateHierarchy.InfoSwirld;
import com.swirlds.platform.WinBrowser.ScrollableJPanel;
import com.swirlds.platform.internal.PlatformThreadFactory;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.internal.SystemExitReason;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import javax.swing.JFrame;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static com.swirlds.common.CommonUtils.canonicalFile;
import static com.swirlds.common.CommonUtils.nameToAlias;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.Settings.JVMPauseDetectorSleepMs;

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

	/** has the browser started up yet? */
	private static boolean launched = false;

	/** is the Browser currently in the process of shutting down? */
	private static volatile boolean shuttingDown = false;

	/** all the Platform objects currently running on this machine */
	static final Collection<SwirldsPlatform> platforms = new LinkedList<>();

	/** the primary window used by Browser */
	static WinBrowser browserWindow = null;
	/* the number of pixels between the edges of a window and interior region that can be used */
	static Insets insets;
	/** the thread for each Platform.run */
	private static Thread platformRunThreads[];
	/** metadata about all known apps, swirlds, members, signed states */
	static StateHierarchy stateHierarchy = null;

	/** the "name" passed in by the app, when it was launched from Eclipse or other IDE (or null if none) */
	private static String fromAppName = null;
	/** the "main" passed in by the app, when it was launched from Eclipse or other IDE (or null if none) */
	private static Class<?> fromAppMain = null;

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();// must be after the configuration load

	/**
	 * whether a savedState has been loaded.
	 * if it is true, NotificationEngine will send a StateLoadedFromDiskNotification
	 */
	private static boolean loadedSavedState = false;

	/**
	 * Prevent this class from being instantiated.
	 */
	protected Browser() {
	}

	/**
	 * Indicates whether the Browser is currently shutting down.
	 *
	 * @return True if the Browser is shutting down, otherwise false
	 */
	public static boolean isShuttingDown() {
		return shuttingDown;
	}

	/**
	 * Shut down the browser and all platforms
	 */
	static void stopBrowser() {
		exitSystem(SystemExitReason.BROWSER_WINDOW_CLOSED, true);
	}

	/**
	 * Exits the system
	 *
	 * @param reason
	 * 		the reason for the exit
	 * @param haltRuntime
	 * 		whether to halt the java runtime or not
	 */
	static void exitSystem(SystemExitReason reason, boolean haltRuntime) {
		shuttingDown = true;
		if (reason.isError()) {
			String exitMsg = "Exiting system, reason: " + reason.toString();
			log.error(EXCEPTION.getMarker(), exitMsg);
			System.out.println(exitMsg);
		}
		System.exit(reason.getExitCode());
		if (haltRuntime) {
			Runtime.getRuntime().halt(reason.getExitCode());
		}
	}

	/**
	 * Same as {@link #exitSystem(SystemExitReason, boolean)}, but with haltRuntime set to false
	 *
	 * @see #exitSystem(SystemExitReason, boolean)
	 */
	static void exitSystem(SystemExitReason reason) {
		exitSystem(reason, false);
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
				String arg = item.trim().toLowerCase();
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
									} catch (NumberFormatException ex) {
										// Intentionally suppress the NumberFormatException
									}
									break;
								case "-log":
									Settings.logPath = canonicalFile(".", arg);
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
			if (Settings.logPath.exists()) {
				LoggerContext context = (LoggerContext) LogManager
						.getContext(false);
				context.setConfigLocation(Settings.logPath.toURI());
			}
		} catch (Exception e) {
			// should log this, but the log can't exist at this point.
			// e.printStackTrace();
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
	public static synchronized void main(String[] args) {
		// Register a shutdown hook that sets the shuttingDown variable to true
		Runtime.getRuntime().addShutdownHook(new Thread(() -> shuttingDown = true));

		// This set contains the nodes set by the command line to start, if none are passed, then IP
		// addresses will be compared to determine which node to start
		final Set<Integer> localNodesToStart = parseCommandLine(args);

		// Initialize the log4j2 configuration and logging subsystem
		startLoggingFramework();
		log.debug(STARTUP.getMarker(), "main() started");

		try {
			if (launched) {
				return;
			}
			launched = true;


			macOsSpecific();

			// discover the inset size and set the look and feel
			if (!GraphicsEnvironment.isHeadless()) {
				UIManager.setLookAndFeel(
						UIManager.getCrossPlatformLookAndFeelClassName());
				JFrame jframe = new JFrame();
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
			stateHierarchy = new StateHierarchy(fromAppName, fromAppMain);

			// read from config.txt (in same directory as .jar, usually sdk/)
			// to fill in the following three variables, which define the
			// simulation to run.

			try {

				if (Settings.configPath.exists()) {
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
				for (Frame f : Frame.getFrames()) {
					if (!f.equals(browserWindow)) {
						f.toFront();
					}
				}

				CommonUtils.tellUserConsole(
						"This computer has an internal IP address:  "
								+ Network.getInternalIPAddress());
				log.info("All of this computer's addresses: {}",
						() -> (Arrays.toString(Network.getOwnAddresses2())));

				// port forwarding
				if (Settings.doUpnp) {
					List<PortMapping> portsToBeMapped = new LinkedList<>();
					synchronized (Browser.platforms) {
						for (Platform p : platforms) {
							Address address = p.getAddress();
							String ip = Address
									.ipString(address.getListenAddressIpv4());
							PortMapping pm = new PortMapping(ip,
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
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(), "", e);
			}

		} catch (Exception e) {
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
		Timer deadlockDetectorTimer;
		if (Settings.deadlockCheckPeriod > 0) { // if it's -1, then never check and don't create the thread
			deadlockDetectorTimer = new Timer("deadlock detector timer", true);
			deadlockDetectorTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					String err = Utilities.deadlocks();
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
			File dir = new File(Settings.threadDumpLogDir);
			if (!dir.exists()) {
				dir.mkdirs();
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
			JVMPauseDetectorThread JVMPauseDetectorThread = new JVMPauseDetectorThread((pauseTimeMs) -> {
				if (pauseTimeMs > Settings.JVMPauseReportMs) {
					log.info(EXCEPTION.getMarker(), "JVMPauseDetectorThread detected JVM paused for {} ms",
							pauseTimeMs);
				}
			}, JVMPauseDetectorSleepMs);
			JVMPauseDetectorThread.start();
			log.debug(STARTUP.getMarker(), "JVMPauseDetectorThread started");
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

		/** symbolic part of the name of the swirld, passed to its Platform */
		String swirldName = "";
		/** parameters from one line of config.txt */
		String[] lineParameters = null;
		/** parameters from the app line of config.txt */
		String[] appParameters = null;
		/** name of app jar file, such as Example.jar */
		String appJarFilename = "";
		/** name of app SwirldMain class, such as a.b.c.ExampleMain */
		String mainClassname = "";
		/** the path to the application jar file */
		File appJarPath = null;

		/** interim list of Address instances */
		List<Address> bookData = Collections
				.synchronizedList(new ArrayList<>());

		// Load config.txt file, parse application jar file name, main class name, address book, and parameters
		if (!Settings.configPath.exists()) {
			log.error(EXCEPTION.getMarker(),
					"ERROR: Browser.startPlatforms called on non-existent config.txt");
			throw new ConfigurationException("ERROR: Browser.startPlatforms called on non-existent config.txt");
		}

		try (final Scanner scanner = new Scanner(Settings.configPath, StandardCharsets.UTF_8.name())) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				int pos = line.indexOf("#");
				if (pos > -1) {
					line = line.substring(0, pos);
				}
				line = line.trim();
				if (!line.isEmpty()) {
					lineParameters = splitLine(line);
					int len = Math.max(10, lineParameters.length);
					// pars is the comma-separated parameters, trimmed, lower-cased, then padded with "" to have
					// at least 10 parameters
					String[] pars = new String[len];
					String[] parsOriginalCase = new String[len];
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
							if (appJarFilename.equals(Browser.fromAppName)) {
								// this is the virtual .jar file because running in Eclipse or IDE
								mainClassname = Browser.fromAppMain.getName();
								break;
							}
							// this is a real .jar file, so load from data/apps/
							appJarPath = canonicalFile(
									Settings.appsDirPath, appJarFilename);
							mainClassname = "";
							try {
								JarFile jarFile = new JarFile(appJarPath);
								Manifest manifest = jarFile.getManifest();
								Attributes attributes = manifest
										.getMainAttributes();
								mainClassname = attributes.getValue("Main-Class");
								jarFile.close();
							} catch (Exception e) {
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
							boolean isOwnHost = (localNodesToStart.size() == 0
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
									(SerializablePublicKey) null, // sigPublicKey
									(SerializablePublicKey) null, // encPublicKey
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
							int n = Integer.parseInt(pars[1]);
							Settings.maxOutgoingSyncs = Math.max(n, 1);
							break;
						}
						case "transactionmaxbytes": {
							// maximum number of bytes allowed per transaction
							int n = Integer.parseInt(pars[1]);
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
						default:
							CommonUtils.tellUserConsolePopup("Error", "\"" + pars[0]
									+ "\" in config.txt isn't a recognized first parameter for a line");
							break;
					}
				}
			}
		} catch (FileNotFoundException ex) { // this should never happen
			log.error(EXCEPTION.getMarker(),
					"Config.txt file was not found but File#exists() claimed the file does exist", ex);
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
		final int numLines = 600 / fontSize;

		int ownHostIndex = 0;

		for (int i = 0; i < addressBook.getSize(); i++) {
			if (addressBook.getAddress(i).isOwnHost()) {
				crypto[i].prepSocketsSSL(); // use the trustStore to prepare for creating TLS sockets
				SwirldsPlatform platform = new SwirldsPlatform(
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
						// suggested number of lines of text in a window
						numLines,
						// name of the app's SwirldMain class
						appDefinition.getMainClassName(),
						// the name of this swirld
						appDefinition.getSwirldName(),
						// the loader for the user app
						appLoader);

				// check the disk for saved states and load them
				try {
					loadedSavedState |= platform.loadSavedStateFromDisk();
				} catch (SignedStateLoadingException e) {
					log.error(EXCEPTION.getMarker(), "Saved state not loaded:", e);
					// if requireStateLoad is on, we exit. if not, we just log it
					if (Settings.requireStateLoad) {
						exitSystem(SystemExitReason.SAVED_STATE_NOT_LOADED);
						return;
					}
				}

				// give infoMember and platform a reference to each other
				InfoMember infoMember = new InfoMember(infoSwirld, i, platform);
				platform.setInfoMember(infoMember);

				platformRunThreads[ownHostIndex] = StandardThreadFactory.newThread(
						"platformRun", platform::run, platform.getSelfId(), Settings.threadPriorityNonSync
				);

				ownHostIndex++;
				synchronized (Browser.platforms) {
					platforms.add(platform);
				}
			}
		}
	}

	private static Crypto[] initNodeSecurity(final ApplicationDefinition appDefinition) {

		final AddressBook addressBook = appDefinition.getAddressBook();
		final byte[] masterKey = appDefinition.getMasterKey();
		final byte[] swirldId = appDefinition.getSwirldId();

		final int n = addressBook.getSize();
		final Crypto[] crypto = new Crypto[n];

		final ExecutorService cryptoThreadPool = Executors
				.newFixedThreadPool(Settings.numCryptoThreads, new PlatformThreadFactory("crypto-verify-"));

		final List<String> names = new ArrayList<>();
		final List<String> ownHostNames = new ArrayList<>();

		for (int i = 0; i < addressBook.getSize(); i++) {
			Address add = addressBook.getAddress(i);
			String name = nameToAlias(add.getSelfName());
			if (add.isOwnHost()) {
				ownHostNames.add(name);
			}
			names.add(name);
		}
		// try to get all the key stores and trust stores from disk

		log.debug(STARTUP.getMarker(), "About start loading keys");
		KeyStore[] stores = Crypto.loadKeys(Settings.keysDirPath,
				ownHostNames, cryptoThreadPool);

		log.debug(STARTUP.getMarker(), "Done loading keys");
		// if there are no keys on the disk, then create our own keys
		Boolean createKeys = (stores == null);

		final KeyStore sigTrustStore = Crypto.createEmptyTrustStore();
		final KeyStore encTrustStore = Crypto.createEmptyTrustStore();
		final KeyStore agrTrustStore = Crypto.createEmptyTrustStore();
		if (!createKeys) {
			for (int i = 0; i < names.size(); i++) {
				String name = names.get(i);
				try {
					Certificate sigCert = stores[0]
							.getCertificate("s-" + name);
					Certificate encCert = stores[0]
							.getCertificate("e-" + name);
					Certificate agrCert = stores[0]
							.getCertificate("a-" + name);

					sigTrustStore.setCertificateEntry("s-" + name, sigCert);
					encTrustStore.setCertificateEntry("e-" + name, encCert);
					agrTrustStore.setCertificateEntry("a-" + name, agrCert);

				} catch (KeyStoreException e) {
					log.error(EXCEPTION.getMarker(), "", e);
				}
			}
		}

		log.debug(STARTUP.getMarker(),
				"About to start creating Crypto objects");

		List<Future<Crypto>> cryptoFutures = new ArrayList<>(n);
		int ownHostIndex = 0;
		for (int i = 0; i < n; i++) {
			if (!addressBook.getAddress(i).isOwnHost() && !createKeys) {
				// in case we are not creating keys but loading them from disk, we do not need to create
				// a Crypto object for every node, just the local ones. in case we are creating keys,
				// they will be created in the Crypto object
				cryptoFutures.add(null);
				continue;
			}
			String name = nameToAlias(
					addressBook.getAddress(i).getSelfName());
			for (int j = 0; j < masterKey.length; j++) {
				masterKey[j] = (byte) (j * 157);
			}
			for (int j = 0; j < swirldId.length; j++) {
				swirldId[j] = (byte) (j * 163);
			}
			masterKey[0] = (byte) i;
			masterKey[1] = (byte) (i >> 8);

			// the stores array will only have keys for the local platforms, so the index is not i but
			// ownHostIndex. the +1 is because the first store contains all the public keys
			KeyStore privateKS = createKeys ? null
					: stores[ownHostIndex + 1];

			// Crypto objects will be created in parallel. The process of creating a Crypto object is
			// very CPU intensive even if the keys are loaded from the hard drive, so making it parallel
			// greatly reduces the time it takes to create them all.
			byte[] masterKeyClone = masterKey.clone();
			byte[] swirldIdClone = swirldId.clone();
			final int memId = i;
			cryptoFutures
					.add(cryptoThreadPool.submit(() ->
							new Crypto(memId, name, masterKeyClone,
									swirldIdClone,
									Utilities.intToBytes(memId), createKeys,
									privateKS, sigTrustStore, encTrustStore,
									agrTrustStore, cryptoThreadPool)
					));
			ownHostIndex++;

		}
		for (int i = 0; i < n; i++) {
			try {
				Future<Crypto> f = cryptoFutures.get(i);
				if (f != null) {
					// in case we have loaded the keys, we will not have all the Crypto objects
					crypto[i] = f.get();
				}
			} catch (InterruptedException | ExecutionException e) {
				log.error(EXCEPTION.getMarker(), "", e);
			}
		}
		// After the keys have been generated or loaded, they are then copied to the address book
		for (int i = 0; i < n; i++) {
			try {
				PublicKey sigKey = sigTrustStore
						.getCertificate("s-" + names.get(i)).getPublicKey();
				PublicKey encKey = encTrustStore
						.getCertificate("e-" + names.get(i)).getPublicKey();
				PublicKey agrKey = agrTrustStore
						.getCertificate("a-" + names.get(i)).getPublicKey();
				addressBook.setAddress(i,
						addressBook.getAddress(i)
								.copySetSigPublicKey(sigKey)
								.copySetEncPublicKey(encKey)
								.copySetAgreePublicKey(agrKey));
			} catch (KeyStoreException e) {
				log.error(EXCEPTION.getMarker(), "", e);
			}
		}
		log.debug(STARTUP.getMarker(), "Done creating Crypto objects");

		return crypto;
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
	private static void startPlatforms(Set<Integer> localNodesToStart)
			throws UnknownHostException, SocketException, AppLoaderException, ConstructableRegistryException {


		// Initialize the deadlock detector if enabled via settings
		startDeadlockDetector();

		// Load config.txt file, parse application jar file name, main class name, address book, and parameters
		ApplicationDefinition appDefinition;
		AddressBook addressBook;
		try {
			appDefinition = loadConfigFile(localNodesToStart);
			addressBook = appDefinition.getAddressBook();
		} catch (ConfigurationException ex) {
			return;
		}

		// the thread for each Platform.run
		// will create a new thread with a new Platform for each local address
		// general address number addIndex is local address number i
		platformRunThreads = new Thread[addressBook.getOwnHostCount()];
		appDefinition.setMasterKey(new byte[Crypto.SYM_KEY_SIZE_BYTES]);
		appDefinition.setSwirldId(new byte[Crypto.HASH_SIZE_BYTES]);


		// Create the various keys and certificates (which are saved in various Crypto objects).
		// Save the certificates in the trust stores.
		// Save the trust stores in the address book.
		//
		log.debug(STARTUP.getMarker(), "About do crypto instantiation");
		Crypto[] crypto = initNodeSecurity(appDefinition);
		log.debug(STARTUP.getMarker(), "Done with crypto instantiation");

		// the AddressBook is not changed after this point, so we calculate the hash now
		CryptoFactory.getInstance().digestSync(addressBook);

		final InfoApp infoApp = stateHierarchy.getInfoApp(appDefinition.getApplicationName());
		final InfoSwirld infoSwirld = new InfoSwirld(infoApp, appDefinition.getSwirldId());

		log.debug(STARTUP.getMarker(), "Starting platforms");

		// Invoke startInit hooks
		startInit();

		// Try to load the app
		SwirldAppLoader appLoader;
		try {
			appLoader = SwirldAppLoader.loadSwirldApp(
					appDefinition.getMainClassName(),
					appDefinition.getAppJarPath()
			);
		} catch (AppLoaderException e) {
			CommonUtils.tellUserConsolePopup("ERROR", e.getMessage());
			throw e;
		}

		// Register all RuntimeConstructable classes
		log.debug(STARTUP.getMarker(), "Scanning the classpath for RuntimeConstructable classes");
		long start = System.currentTimeMillis();
		ConstructableRegistry.registerConstructables("", appLoader.getClassLoader());
		log.debug(STARTUP.getMarker(), "Done with registerConstructables, time taken {}ms",
				System.currentTimeMillis() - start);

		// Create all instances for all nodes that should run locally
		createLocalPlatforms(appDefinition, crypto, infoSwirld, appLoader);

		// Invoke stopInit hooks
		stopInit();

		// Partially initialize the platforms before we dispatch the StateLoadedFromDiskNotification
		for (SwirldsPlatform platform : platforms) {
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
		for (Thread platformRunThread : platformRunThreads) {
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
	static String[] splitLine(String line) {
		String[] elms = line.split(",");
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
	static void showBrowserWindow(ScrollableJPanel comp) {
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

	/**
	 * Do work specific to MacOS, so choosing QUIT from the OS's menu won't cause the program to freeze
	 * forever.
	 *
	 * In order for this to work, you have to do the following in Eclipse:
	 * <ul>
	 * <li>in the package explorer, right click on PLATFORM and choose PROPERTIES</li>
	 * <li>click JAVA BUILD PATH then the LIBRARIES tab</li>
	 * <li>open the JRE SYSTEM LIBRARY item and click ACCESS RULES</li>
	 * <li>click EDIT then ADD</li>
	 * <li>set RESOLUTION to ACCESSIBLE, and RULE PATTERN to com/apple/eawt/**</li>
	 * <li>click OK</li>
	 * </ul>
	 * Now you can use classes like com.apple.eawt.AppEvent, for MacOS-specific code.
	 */
	static void macOsSpecific() {
		// if (System.getProperty("os.name").equals("Mac OS X")) {
		// if (SystemUtils.IS_OS_MAC) {
		// Application.getApplication()
		// .setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
		// }
	}

	/**
	 * Called prior to loading save state and before the {@link Platform} objects are instantiated to allow
	 * subsystems to
	 * prepare for recovery from the saved state.
	 */
	protected static void startInit() {
		if (Settings.dbConnection.isActive()) {
			BinaryObjectStore.getInstance().startInit();
		}
	}

	/**
	 * Called after the saved state is loaded and after the {@link Platform} objects are instantiated but before the
	 * platforms are started to allow subsystems to perform any additional operations after the state is recovered.
	 */
	protected static void stopInit() {
		if (Settings.dbConnection.isActive()) {
			BinaryObjectStore.getInstance().stopInit();
		}
	}

	protected static void populateSettingsCommon() {
		SettingsCommon.maxTransactionCountPerEvent = Settings.maxTransactionCountPerEvent;
		SettingsCommon.maxTransactionBytesPerEvent = Settings.maxTransactionBytesPerEvent;
		SettingsCommon.transactionMaxBytes = Settings.transactionMaxBytes;
		SettingsCommon.halfLife = Settings.halfLife;
		SettingsCommon.logStack = Settings.logStack;
		SettingsCommon.showInternalStats = Settings.showInternalStats;
		SettingsCommon.verboseStatistics = Settings.verboseStatistics;
		SettingsCommon.enableBetaMirror = Settings.enableBetaMirror;

		CryptoFactory.configure(Settings.crypto);
		ReconnectSettingsFactory.configure(Settings.reconnect);
	}
}
