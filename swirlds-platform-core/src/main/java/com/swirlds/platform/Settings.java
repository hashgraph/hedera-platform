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
import com.swirlds.platform.internal.CryptoSettings;
import com.swirlds.platform.internal.DatabaseBackupSettings;
import com.swirlds.platform.internal.DatabaseRestoreSettings;
import com.swirlds.platform.internal.DatabaseSettings;
import com.swirlds.platform.internal.FreezeSettings;
import com.swirlds.platform.internal.JsonExportSettings;
import com.swirlds.platform.internal.SubSetting;
import com.swirlds.platform.reconnect.ReconnectSettings;
import com.swirlds.platform.state.SignedStateManager;
import com.swirlds.platform.state.StateSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

/**
 * This purely-static class holds global settings that control how the Platform and sync processes operate.
 * If the file sdk/settings.txt exists, then it will read the settings from it, to override one or more of
 * the default settings (and to override settings in config.txt). The Browser should call the loadSettings()
 * method to read that file, before it instantiates any Platform objects (or anything else).
 *
 * Any field that is intended to be a "setting" should be non-final. The settings.txt file will not change
 * any of the fields. But it will change all of the final fields (except maxIncomingSyncs, which is a
 * special case which is calculated from maxOutgoingSyncs, and cannot be changed directly from
 * settings.txt).
 *
 * After the config.txt and settings.txt files have been read and the Platform objects instantiated, the
 * Browser should then call writeSettings() to write all the final settings values to settingsUsed.txt
 * (though only if settings.txt exists).
 */
class Settings {
	// The following paths are for 4 files and 2 directories, such as:

	// /FULL/PATH/sdk/config.txt
	// /FULL/PATH/sdk/settings.txt
	// /FULL/PATH/sdk/settingsUsed.txt
	// /FULL/PATH/sdk/log4j2.xml
	// /FULL/PATH/sdk/data/keys/
	// /FULL/PATH/sdk/data/apps/

	// useful run configuration arguments for debugging:
	// -XX:+HeapDumpOnOutOfMemoryError
	// -Djavax.net.debug=ssl,handshake

	/** path to config.txt (which might not exist) */
	static final File configPath = CommonUtils.canonicalFile("config.txt");
	/** path to settings.txt (which might not exist) */
	static final File settingsPath = CommonUtils.canonicalFile(".", "settings.txt");
	/** name of the settings used file */
	static final String settingsUsedFilename = "settingsUsed.txt";
	/** the directory where the settings used file will be created on startup if and only if settings.txt exists */
	static final File settingsUsedDir = CommonUtils.canonicalFile(".");
	/** path to settingsUsed.txt (which will be created if and only if settings.txt exists) */
	static final File settingsUsedPath = CommonUtils.canonicalFile(settingsUsedDir, settingsUsedFilename);
	/** path to log4j2.xml (which might not exist) */
	static File logPath = CommonUtils.canonicalFile(".", "log4j2.xml");
	/** path to data/keys/ */
	static final File keysDirPath = CommonUtils.canonicalFile("data", "keys");
	/** path to data/apps/ */
	static final File appsDirPath = CommonUtils.canonicalFile("data", "apps");
	/** path to data/saved/ that is be used for storing data from the platform */
	static final File savedDirPath = CommonUtils.canonicalFile("data", "saved");
	/** file extension used by the swirlds platform, it stands for SwirldsHashgraph */
	static final String swirldsFileExtension = ".swh";

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	///////////////////////////////////////////
	// settings from settings.txt file

	static String about = "Swirlds browser v. 0.7.4-alpha.4\n"
			+ "(c)2016-2020 Swirlds Inc\n" + "This is an early alpha version. \n"
			+ "The Swirlds™ software is covered by one or more patents \n"
			+ "(see www.swirlds.com/ip). The browser is free to download, \n"
			+ "to experiment with, and to test in building apps. To deploy \n"
			+ "or use those apps, contact sales@swirlds.com";

	/** use RSA-3072 instead of ECDSA-p384 for signatures? */
	static boolean useRSA = true;
	/** verify event signatures (rather than just trusting they are correct)? */
	static boolean verifyEventSigs = true;
	/** number of threads used to verify signatures and generate keys, in parallel */
	static int numCryptoThreads = 32;

	/** show the user all statistics, including those with category "internal"? */
	static boolean showInternalStats = false;
	static boolean showDBStats = false;
	/** show expand statistics values, inlcude mean, min, max, stdDev */
	static boolean verboseStatistics = false;
	/** log any LoggingReentrantLock held at least this many milliseconds (0=all, -1=none) */
	static int lockLogTimeout = 2000;
	/** log LoggingReentrantLock requests blocking for at least this many milliseconds (0=all, -1=none) */
	static int lockLogBlockTimeout = 2000;
	/** should LoggingReentrantLock only do logging for the Platform where selfId is 0? */
	static boolean lockLogMemberZeroOnly = false;
	/** should each lock log also create a file on disk describe all current threads? */
	static boolean lockLogThreadDump = false;

	// make thread sleep every this many milliseconds
	static int THREAD_SLEEP_PERIOD_MS = 100;

	/** settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors */
	static StateSettings state = new StateSettings();

	/** settings controlling the diagnostic export of state objects as JSON */
	static JsonExportSettings jsonExport = new JsonExportSettings();

	/** if set to true, the platform will fail to start if it fails to load a state from disk */
	static boolean requireStateLoad = false;
	/**
	 * hash and sign a state every signedStateFreq rounds. 1 means that a state will be signed every round, 2 means
	 * every other round, and so on. If the value is 0 or less, no states will be signed
	 */
	static int signedStateFreq = 1;
	/** max events that can be put in the forCons queue in EventFlow (0 for infinity) */
	static int maxEventQueueForCons = 500;
	/**
	 * Stop accepting new non-system transactions into the 4 transaction queues if any of them have more
	 * than this many.
	 */
	static int throttleTransactionQueueSize = 100_000;

	/**
	 * on startup, only Alice can create an event with no otherParent, and all other members will refrain
	 * from creating an event until they have received at least one event
	 */
	static boolean waitAtStartup = true;
	/**
	 * should we slow down when not behind? One of N members is "falling behind" when it receives at least
	 * (N + throttle7threshold) events during a sync.
	 */
	static boolean throttle7 = true;
	/**
	 * "falling behind" if received at least N * throttle7threshold events in a sync. A good choice for this
	 * constant might be 1+2*d if a fraction d of received events are duplicates.
	 */
	static double throttle7threshold = 1.5;
	/** if a sync has neither party falling behind, increase the bytes sent by this fraction */
	static double throttle7extra = 0.05;
	/** the maximum number of slowdown bytes to be sent during a sync */
	static int throttle7maxBytes = 100 * 1024 * 1024;

	/** number of connections maintained by each member (syncs happen on random connections from that set */
	static int numConnections = 40; // probably 40 is a good number
	/** maximum number of simultaneous outgoing syncs initiated by me */
	static int maxOutgoingSyncs = 2;
	/**
	 * the number of random callers out of the total number of callers defined by maxOutgoingSyncs. The
	 * callers that are not random callers will be priority callers. If maxOutgoingRandomSyncs >=
	 * maxOutgoingSyncs, then all the callers will be random, and none will be prioritized.
	 */
	static int maxOutgoingRandomSyncs = 1000;
	/**
	 * maximum number of simultaneous incoming syncs initiated by others, minus maxOutgoingSyncs. If there
	 * is a moment where each member has maxOutgoingSyncs outgoing syncs in progress, then a fraction of at
	 * least:
	 *
	 * <pre>
	 * (1 / (maxOutgoingSyncs + maxIncomingSyncsInc))
	 * </pre>
	 *
	 * members will be willing to accept another incoming sync. So even in the worst case, it should be
	 * possible to find a partner to sync with in about (maxOutgoingSyncs + maxIncomingSyncsInc) tries, on
	 * average.
	 */
	static int maxIncomingSyncsInc = 1;

	/** for BufferedInputStream and BufferedOutputStream for syncing */
	static int bufferSize = 8 * 1024;

	/**
	 * The IP_TOS to set for a socket, from 0 to 255, or -1 to not set one. This number (if not -1) will be
	 * part of every TCP/IP packet, and is normally ignored by internet routers, but it is possible to make
	 * routers change their handling of packets based on this number, such as for providing different
	 * Quality of Service (QoS).
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/Type_of_service">Type of Service</a>
	 */
	static int socketIpTos = -1;

	/** half life of some of the various statistics (give half the weight to the last halfLife seconds) */
	static double halfLife = 10;
	/** a coin round happens every coinFreq rounds during an election (every other one is all true) */
	static int coinFreq = 12;
	/** when converting an exception to a string for logging, should it include the stack trace? */
	static boolean logStack = true;
	/** should TLS be turned on, rather than making all sockets unencrypted? */
	static boolean useTLS = true;
	/** should this set up uPnP port forwarding on the router once every 60 seconds? */
	static boolean doUpnp = true;
	/** should be set to true when using the internet simulator */
	static boolean useLoopbackIp = true;

	/** if true, then Nagel's algorithm is disabled, which helps latency, hurts bandwidth usage */
	static boolean tcpNoDelay = true;
	/** timeout when waiting for data */
	static int timeoutSyncClientSocket = 5_000;
	/** timeout when establishing a connection */
	static int timeoutSyncClientConnect = 5_000;
	/** timeout when server is waiting for another member to create a connection */
	static int timeoutServerAcceptConnect = 5_000;
	/** check for deadlocks every this many milliseconds (-1 for never) */
	static int deadlockCheckPeriod = 1000;
	/** update some statistics every this many milliseconds (-1 for never) */
	static int statUpdatePeriod = 1000;

	/** send a heartbeat byte on each comm channel to keep it open, every this many milliseconds */
	static long sleepHeartbeat = 500;

	/** sleep this many milliseconds, once, at the start of syncListener */
	static long sleepSyncListenerStart = 0;
	/** after binding to a new socket */
	static long sleepSyncServerSocketBind = 100;
	/**
	 * the working state (stateWork) resets to a copy of the consensus state (stateCons) (which is called a
	 * shuffle) when its queue is empty and the two are equal, but never twice within this many milliseconds
	 */
	static long delayShuffle = 200;

	/** sleep sleepCallerSkips ms after the caller fails this many times to call a random member */
	static long callerSkipsBeforeSleep = 30;
	/** caller sleeps this many milliseconds if it failed to connect to callerSkipsBeforeSleep in a row */
	static long sleepCallerSkips = 50;
	/** listener sleeps this many milliseconds if there isn't a valid connection */
	static long sleepListenerDisconnected = 100;
	/** wake up (while blocked on a socket) every this many milliseconds to check if it's still valid */
	static int waitListenerRead = 500;

	/** number of bins to store for the history (in StatsBuffer etc.) */
	static int statsBufferSize = 100;
	/** number of seconds covered by "recent" history (in StatsBuffer etc.) */
	static double statsRecentSeconds = 63;
	/** number of seconds that the "all" history window skips at the start */
	static double statsSkipSeconds = 60;
	/** priority for threads that sync (in SyncCaller, SyncListener, SyncServer) */
	static int threadPrioritySync = Thread.NORM_PRIORITY;// Thread.MAX_PRIORITY;
	/** priority for threads that don't sync (all but SyncCaller, SyncListener,SyncServer */
	static int threadPriorityNonSync = Thread.NORM_PRIORITY;
	/** maximum number of bytes allowed in a transaction */
	static int transactionMaxBytes = 6144;

	/** the settings that are related to event creation freezing */
	static volatile FreezeSettings freezeSettings = new FreezeSettings();

	/**
	 * do not create events for this many seconds after the platform has started (0 or less to not freeze at
	 * startup)
	 */
	static int freezeSecondsAfterStartup = 10;

	/** settings related to the {@link com.swirlds.common.crypto.Cryptography} implementation */
	static CryptoSettings crypto = new CryptoSettings();

	/**
	 * the maximum number of bytes that a single event may contain not including the event headers
	 * if a single transaction exceeds this limit then the event will contain the single transaction only
	 */
	static int maxTransactionBytesPerEvent = 245760;

	/** the maximum number of transactions that a single event may contain */
	static int maxTransactionCountPerEvent = 245760;

	/**
	 * if true, both nodes send and wait for a commSyncDone byte after finishing reading and writing all events of a
	 * sync
	 */
	static boolean sendSyncDoneByte = false;

	/**
	 * settings controlling the reconnect feature, ie. enabled/disabled, fallen behind, etc
	 */
	static ReconnectSettings reconnect = new ReconnectSettings();

	/** settings related to database connectivity */
	static DatabaseSettings dbConnection = new DatabaseSettings();

	/** settings related to database backup */
	static DatabaseBackupSettings dbBackup = new DatabaseBackupSettings();

	/** settings related to database restore */
	static DatabaseRestoreSettings dbRestore = new DatabaseRestoreSettings();

	/**
	 * if on, transThrottle will stop initiating syncs and thus stop generating events if the are no non consensus user
	 * transactions. If states are being saved to disk, it will only stop after all user transactions have been handled
	 * by a state that has been saved to disk.
	 */
	static boolean transThrottle = true;

	/**
	 * The prefix of the name of the CSV file that the platform will write statistics to. If this value is null or an
	 * empty string, the platform will not write any statistics.
	 */
	static String csvFileName = "";

	/** Indicates whether statistics should be appended to the CSV file */
	static boolean csvAppend = false;

	/** The value for the event intake queue at which the node should stop syncing */
	static int eventIntakeQueueThrottleSize = 1;

	/**
	 * The number of intake threads as a fraction of the number of CPU cores. The number of threads will be:
	 * [number of CPU cores] * [eventIntakeThreadMultiplier]
	 */
	static int eventIntakeThreadMultiplier = 1;

	/**
	 * If true, the platform will recalculate the hash of the signed state and check it against the written hash. It
	 * will also verify that the signatures are valid.
	 */
	static boolean checkSignedStateFromDisk = false;

	/**
	 * The probability that after a sync, a node will create an event with a random other parent. The probability is
	 * is 1 in X, where X is the value of randomEventProbability. A value of 0 means that a node will not create any
	 * random events.
	 *
	 * This feature is used to get consensus on events with no descendants which are created by nodes who go offline.
	 */
	static int randomEventProbability = 0;

	/**
	 * A setting used to prevent a node from generating events that will probably become stale. This value is
	 * multiplied by the address book size and compared to the number of events received in a sync.
	 * If ( numEventsReceived > staleEventPreventionThreshold * addressBookSize ) then we will not create an event for
	 * that sync, to reduce the probability of creating an event that will become stale.
	 */
	static int staleEventPreventionThreshold = 5;

	/**
	 * If a childless event exists with a generation this much older than the maximum generation, we should
	 * potentially create an event with it as the other parent.
	 */
	static int rescueChildlessGeneration = 30;
	/**
	 * The probability that we will create a child for a childless event that qualifies the above stated criteria.
	 * The probability is is 1 in X, where X is the value of rescueChildlessProbability. A value of 0 means that a node
	 * will not create any children for childless events.
	 */
	static int rescueChildlessProbability = 30;

	/** Run a thread that checks if the JVM pauses for a long time */
	static boolean runPauseCheckTimer = false;

	/**
	 * When a hash is calculated for a signed state, it will only calculate the hashes of nodes that do not have a hash
	 * set in order to avoid hashing the whole tree every time. If a merkle node changes its data and does not
	 * invalidate its hash, the root hash calculated will be wrong. This setting is used for debugging nodes that have
	 * changed their data but have not invalidated their hash. It will rehash the whole tree for every signed state and
	 * check if the hashes are correct. For any incorrect hash it will log an error.
	 *
	 * NOTE: This should only be used for debugging, it will have a major impact on performance.
	 */
	static boolean checkSignedStateHashes = false;

	///////////////////////////////////////////
	// Stale Event During Gossip Threshold

	/**
	 * threshold for the allowable number of missing self events that we will compensate for during a sync by
	 * artificially increasing our sequence number to match the sync peer
	 */
	static long syncStaleEventCompThreshold = 1_000;

	///////////////////////////////////////////
	// Beta Mirror Nodes

	/** enables or disables beta mirror node support including zero stake support */
	static boolean enableBetaMirror = false;

	///////////////////////////////////////////
	// Setting for stream event

	/** enable stream event to server */
	static boolean enableEventStreaming = false;

	/** capacity of the blockingQueue from which we take events and write to EventStream files */
	static int eventStreamQueueCapacity = 500;

	/** period of generating eventStream file */
	static long eventsLogPeriod = 60;

	/** eventStream files will be generated in this directory */
	static String eventsLogDir = "./eventstreams";

	/** new marker for stream event start after which a version number is expected */
	final static byte STREAM_EVENT_START_WITH_VERSION = 0x5a;

	/**
	 * new marker for stream event start with no transaction embedded in event,
	 * after which a version number is expected
	 */
	final static byte STREAM_EVENT_START_NO_TRANS_WITH_VERSION = 0x5b;

	/** The current version number for streamed events */
	final static int STREAM_EVENT_VERSION = 3;

	/** The current version number for EventStream file */
	final static int EVENT_STREAM_FILE_VERSION = 3;

	/** save disk space by not saving transaction */
	static boolean stripTransactions = false;

	///////////////////////////////////////////
	// Setting for thread dump
	/** period of generating thread dump file in the unit of milliseconds */
	static long threadDumpPeriodMs = 0;

	/** thread dump files will be generated in this directory */
	static String threadDumpLogDir = "data/threadDump";

	///////////////////////////////////////////
	// Setting for JVMPauseDetectorThread
	/** period of JVMPauseDetectorThread sleeping in the unit of milliseconds */
	static int JVMPauseDetectorSleepMs = 1000;

	/** log an error when JVMPauseDetectorThread detect a pause greater than this many milliseconds */
	static int JVMPauseReportMs = 1000;

	///////////////////////////////////////////
	// Setting for state recover
	static boolean enableStateRecovery = false;
	/** directory where event stream files are stored */
	static String playbackStreamFileDirectory = "";
	/** last time stamp (inclusive) to stop the playback, format is "2019-10-02T19:46:30.037063163Z" */
	static String playbackEndTimeStamp = "";

	static final Settings INSTANCE = new Settings();

	public static Settings getInstance() {
		return INSTANCE;
	}

	private Settings() {
	}

	static void writeSettingsUsed() {
		writeSettingsUsed(settingsUsedDir);
	}

	/**
	 * Write all the settings to the file settingsUsed.txt, some of which might have been changed by settings.txt.
	 *
	 * @param directory
	 * 		the directory to write to
	 */
	static void writeSettingsUsed(File directory) {
		String[][] settings = Settings.currSettings();
		try (BufferedWriter writer = Files.newBufferedWriter(
				Paths.get(CommonUtils.canonicalFile(directory, settingsUsedFilename).getCanonicalPath()))) {
			writer.write(
					"The following are all the settings, as modified by settings.txt, but not reflecting any changes " +
							"made by config.txt.\n\n");
			for (String[] pair : settings) {
				writer.write(String.format("%15s = %s\n", pair[1], pair[0]));
			}
		} catch (IOException e) {
			log.error(EXCEPTION.getMarker(), "Error in writing to settingsUsed.txt", e);
		}
	}

	/**
	 * Return true if the settings.txt file exists
	 *
	 * @return
	 */
	static boolean settingsTxtExists() {
		return Settings.settingsPath.exists();
	}

	/**
	 * If the sdk/data/settings.txt file exists, then load settings from it. If it doesn't exist, keep the
	 * existing settings. If it exists but a setting is missing, keep the default value for it. If a setting
	 * is given multiple times, use the last one. If the file contains a setting name that doesn't exist,
	 * complain to the command line.
	 *
	 * It is intended that this file will not normally exist. Most settings should be controlled by the
	 * defaults set in this source file. The settings.txt file is only used for testing and debugging.
	 */
	static void loadSettings() {
		Scanner scanner = null;
		if (!settingsTxtExists()) {
			return; // normally, the file won't exist, so the defaults are used.
		}

		try {
			scanner = new Scanner(Settings.settingsPath, "utf-8");
		} catch (FileNotFoundException e) { // this should never happen
			CommonUtils.tellUserConsole("The file " + Settings.settingsPath
					+ " exists, but can't be opened. " + e);
			return;
		}

		CommonUtils.tellUserConsole("Reading the settings from the file:        "
				+ Settings.settingsPath);

		int count = 0;
		while (scanner.hasNextLine()) {
			String originalLine = scanner.nextLine();
			String line = originalLine;
			int pos = line.indexOf("#");
			if (pos > -1) {
				line = line.substring(0, pos);
			}
			line = line.trim();
			count++;
			if (!line.isEmpty()) {
				String[] pars = Browser.splitLine(line);
				if (pars.length == 0) { // ignore empty lines
				} else {
					try {
						if (!handleSetting(pars)) {
							CommonUtils.tellUserConsole(
									"bad name of setting in settings.txt line "
											+ count + ": " + originalLine);
						}
					} catch (Exception e) {
						CommonUtils.tellUserConsole(
								"syntax error in settings.txt on line " + count
										+ ":    " + originalLine);
						scanner.close();
						return;
					}
				}
			}
		}
		scanner.close();

		validateSettings();
	}

	/**
	 * validate the settings read in from the settings.txt file
	 */
	static void validateSettings() {
		// if the settings allow a transaction larger than the maximum event size
		if (maxTransactionBytesPerEvent < transactionMaxBytes) {
			log.error(STARTUP.getMarker(), "Settings Mismatch: transactionMaxBytes ({}) is larger than " +
							"maxTransactionBytesPerEvent ({}), truncating transactionMaxBytes to {}.",
					transactionMaxBytes, maxTransactionBytesPerEvent, maxTransactionBytesPerEvent);

			transactionMaxBytes = maxTransactionBytesPerEvent;
		}
	}

	/**
	 * handle a single line from the settings.txt file. The line is split by commas, so none of the
	 * individual strings or values should have commas in them. The first token on the line is intended to
	 * state what setting is being changed, and the rest is the value for that setting.
	 *
	 * @param pars
	 * 		the parameters on that line, split by commas
	 * @return true if the line is a valid setting assignment
	 */
	static boolean handleSetting(String[] pars) {
		String name = pars[0];
		String subName = null;
		if (name.contains(".")) {
			// if the name contains a dot (.), then we need to set a variable that is inside an object
			String split[] = name.split("\\.");
			name = split[0];
			subName = split[1];
		}
		String val = pars.length > 1 ? pars[1].trim() : ""; // the first parameter passed in, or "" if none
		boolean good = false; // is name a valid name of a non-final static field in Settings?
		Field field = getFieldByName(Settings.class.getDeclaredFields(), name);
		if (field != null && !Modifier.isFinal(field.getModifiers())) {
			try {
				if (subName == null) {
					good = setValue(field, null, val);
				} else {
					Field subField = getFieldByName(field.getType().getDeclaredFields(), subName);
					if (subField != null) {
						good = setValue(subField, field.get(Settings.class), val);
					}
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				log.error(EXCEPTION.getMarker(),
						"illegal line in settings.txt: {}, {}  {}", pars[0],
						pars[1], e);
			}
		}

		if (!good) {
			String err = "ERROR: " + pars[0] + " is not a valid setting name.";
			// this only happens if settings.txt exist, so it's internal, not users, so print it
			CommonUtils.tellUserConsole(err);
			log.error(EXCEPTION.getMarker(), err);
			return false;
		}
		return true;
	}

	/**
	 * Finds a field from the array with the given name
	 *
	 * @param fields
	 * 		the fields to search in
	 * @param name
	 * 		the name of the field to look for
	 * @return the field with the name supplied, or null if such a field cannot be found
	 */
	static Field getFieldByName(Field[] fields, String name) {
		for (Field f : fields) {
			if (f.getName().toLowerCase().equals(name.toLowerCase())) {
				return f;
			}
		}
		return null;
	}

	/**
	 * Sets the value via reflection, converting the string value into the appropriate type
	 *
	 * @param field
	 * 		the field to set
	 * @param object
	 * 		the object in which to set the field, should be null if the field is static
	 * @param value
	 * 		the value to set it to
	 * @return true if the field was set, false otherwise
	 * @throws IllegalAccessException
	 * 		if this Field object is enforcing Java language access control and the
	 * 		underlying field is either inaccessible or final.
	 */
	static boolean setValue(Field field, Object object, String value) throws IllegalAccessException {
		Class<?> t = field.getType();
		if (t == String.class) {
			field.set(object, value);
			return true;
		} else if (t == char.class) {
			field.set(object, (Character) (value.charAt(0)));
			return true;
		} else if (t == byte.class) {
			field.set(object, Byte.parseByte(value));
			return true;
		} else if (t == short.class) {
			field.set(object, Short.parseShort(value));
			return true;
		} else if (t == int.class) {
			field.set(object, Integer.parseInt(value));
			return true;
		} else if (t == long.class) {
			field.set(object, Long.parseLong(value));
			return true;
		} else if (t == boolean.class) {
			field.set(object, Utilities.parseBoolean(value));
			return true;
		} else if (t == float.class) {
			field.set(object, Float.parseFloat(value));
			return true;
		} else if (t == double.class) {
			field.set(object, Double.parseDouble(value));
			return true;
		}
		return false;
	}

	/**
	 * Return all the current settings, as a 2D array of strings, where the first column is the name of the
	 * setting, and the second column is the value.
	 *
	 * @return the current settings
	 */
	static String[][] currSettings() {
		Field[] fields = Settings.class.getDeclaredFields();
		List<String[]> list = new ArrayList<>();
		for (Field f : fields) {
			// every non-setting field should be final, so the following deals with the correct fields
			if (!Modifier.isFinal(f.getModifiers())) {
				try {
					if (SubSetting.class.isAssignableFrom(f.getType())) {
						Field[] subFields = f.getType().getDeclaredFields();
						for (Field subField : subFields) {
							list.add(new String[] {
									f.getName() + "." + subField.getName(),
									subField.get(f.get(Settings.class)).toString()
							});
						}
					} else {
						list.add(new String[] { f.getName(), f.get(null).toString() });
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					log.error(EXCEPTION.getMarker(),
							"error while reading settings.txt", e);
				}
			}
		}
		return list.toArray(new String[0][0]);
	}

	public static void main(String[] args) {
		loadSettings();
		writeSettingsUsed();
	}

}
