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

package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.crypto.Message;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.crypto.internal.AbstractCryptography;
import com.swirlds.common.crypto.internal.CryptographySettings;
import com.swirlds.common.futures.WaitingFuture;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.logging.LogMarker;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.hash.FutureMerkleHash;
import com.swirlds.common.merkle.hash.MerkleHashBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_TYPE;
import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_DEVICE_VENDOR;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseDevice;
import static org.jocl.CL.setExceptionsEnabled;

public class CryptoEngine extends AbstractCryptography implements Cryptography {

	/**
	 * logs exceptions thrown by the OpenCL API
	 */
	public static final Marker LOGM_OPENCL_INIT_EXCEPTIONS = MarkerManager.getMarker(
			LogMarker.OPENCL_INIT_EXCEPTIONS.name());

	/**
	 * log all exceptions, and serious problems. These should never happen unless we are either receiving packets from
	 * an attacker, or there is a bug in the code. In most cases, this should include a full stack trace of the
	 * exception.
	 */
	public static final Marker LOGM_EXCEPTION = MarkerManager.getMarker(LogMarker.EXCEPTION.name());

	/**
	 * exceptions that shouldn't happen during testing, but can happen in production if there is a malicious
	 * node. This should be turned off in production so that a malicious node cannot clutter the logs
	 */
	public static final Marker LOGM_TESTING_EXCEPTIONS = MarkerManager
			.getMarker(LogMarker.TESTING_EXCEPTIONS.name());

	/**
	 * logs events related to the startup of the application
	 */
	public static final Marker LOGM_STARTUP = MarkerManager.getMarker(LogMarker.STARTUP.name());

	/**
	 * logs events related to the startup of the application
	 */
	public static final Marker LOGM_ADV_CRYPTO_SYSTEM = MarkerManager.getMarker(LogMarker.ADV_CRYPTO_SYSTEM.name());

	/**
	 * use this for all logging, as controlled by the optional data/log4j2.xml file
	 */
	private static final Logger log = LogManager.getLogger(CryptoEngine.class);

	/**
	 * the intake dispatcher instance that handles asynchronous signature verification
	 */
	private volatile IntakeDispatcher<TransactionSignature, VerificationProvider, AsyncVerificationHandler> verificationDispatcher;

	/**
	 * the intake dispatcher instance that handles asynchronous message digests
	 */
	private volatile IntakeDispatcher<Message, DigestProvider, AsyncDigestHandler> digestDispatcher;

	/**
	 * the {@link ConcurrentLinkedQueue} instance of {@link TransactionSignature} waiting for verification
	 */
	private volatile BlockingQueue<List<TransactionSignature>> verificationQueue;

	/**
	 * the {@link ConcurrentLinkedQueue} instance of {@link Message} pending message digest computation
	 */
	private volatile BlockingQueue<List<Message>> digestQueue;

	/**
	 * The digest provider instance that is used to generate hashes of SelfSerializable objects.
	 */
	private final SerializationDigestProvider serializationDigestProvider;

	/**
	 * The digest provider instance that is used to generate hashes of MerkleInternal objects.
	 */
	private final MerkleInternalDigestProvider merkleInternalDigestProvider;

	/** The digest provider instance that is used to generate running Hash.
	 */
	private final RunningHashProvider runningHashProvider;

	private final MerkleHashBuilder merkleHashBuilder;

	/**
	 * the total number of available physical processors and physical processor cores
	 */
	private final int availableCpuCount;

	/**
	 * the current configuration settings
	 */
	private volatile CryptographySettings settings;

	/**
	 * indicator if a support OpenCL framework is installed and available
	 */
	private volatile boolean openCLAvailable = false;

	/**
	 * indicator if a supported GPU is installed and available
	 */
	private volatile boolean gpuAvailable = false;

	private Map<DigestType, Hash> nullHashes;

	/**
	 * Constructs a new {@link CryptoEngine} using default settings.
	 */
	public CryptoEngine() {
		this(CryptographySettings.getDefaultSettings());
	}

	/**
	 * Constructs a new {@link CryptoEngine} using the provided settings.
	 *
	 * @param settings
	 * 		the initial settings to be used
	 */
	public CryptoEngine(final CryptographySettings settings) {
		this.settings = settings;
		this.availableCpuCount = Runtime.getRuntime().availableProcessors();
		this.serializationDigestProvider = new SerializationDigestProvider();
		this.merkleInternalDigestProvider = new MerkleInternalDigestProvider();
		this.runningHashProvider = new RunningHashProvider();
		this.merkleHashBuilder = new MerkleHashBuilder(this, settings.computeCpuDigestThreadCount());

		if (!settings.forceCpu()) {
			detectSystemFeatures();
		}
		applySettings();
		buildNullHashes();
	}

	/**
	 * Indicates whether a supported OpenCL framework is installed and available on this system.
	 *
	 * @return true if OpenCL is available; false otherwise
	 */
	public boolean isOpenCLAvailable() {
		return openCLAvailable;
	}

	/**
	 * Indicates whether a support GPU is available on this system.
	 *
	 * @return true if a support GPU is available; false otherwise
	 */
	public boolean isGpuAvailable() {
		return gpuAvailable;
	}

	/**
	 * Getter for the current configuration settings used by the {@link CryptoEngine}.
	 *
	 * @return the current configuration settings
	 */
	public CryptographySettings getSettings() {
		return settings;
	}

	/**
	 * Setter to allow the configuration settings to be updated at runtime.
	 *
	 * @param settings
	 * 		the configuration settings
	 */
	public synchronized void setSettings(final CryptographySettings settings) {
		this.settings = settings;
		applySettings();
	}

	/**
	 * Returns the total number of physical processors and physical processor cores available.
	 *
	 * @return the total number of physical processors and physical cores
	 */
	public int getAvailableCpuCount() {
		return availableCpuCount;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void digestAsync(final Message message) {
		try {
			digestQueue.put(Collections.singletonList(message));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void digestAsync(final List<Message> messages) {
		try {
			digestQueue.put(messages);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Future<byte[]> digestAsync(final byte[] message, final DigestType digestType) {
		final Message wrappedMessage = new Message(message, digestType);
		try {
			digestQueue.put(Collections.singletonList(wrappedMessage));

			return new WrappingLambdaFuture<>(() -> {
				try {
					return wrappedMessage.waitForFuture();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return null;
				}
			}, wrappedMessage::getHash);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CryptographyException(ex, LogMarker.TESTING_EXCEPTIONS);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] digestSync(final Message message) {
		final DigestProvider provider = new DigestProvider();
		final WaitingFuture<Void> future = new WaitingFuture<>();
		future.done(null);

		return digestSyncInternal(message, provider, future);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void digestSync(final List<Message> messages) {
		final DigestProvider provider = new DigestProvider();
		final WaitingFuture<Void> future = new WaitingFuture<>();
		future.done(null);

		for (Message message : messages) {
			digestSyncInternal(message, provider, future);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] digestSync(final byte[] message, final DigestType digestType) {
		final Message wrappedMessage = new Message(message, digestType);
		return digestSync(wrappedMessage);
	}

	@Override
	public Hash digestSync(SelfSerializable serializable, DigestType digestType) {
		try {
			return serializationDigestProvider.compute(serializable, digestType);
		} catch (NoSuchAlgorithmException ex) {
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void digestSync(SerializableHashable serializableHashable, DigestType digestType) {
		try {
			serializableHashable.setHash(serializationDigestProvider.compute(serializableHashable, digestType));
		} catch (NoSuchAlgorithmException ex) {
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}
	}

	@Override
	public Hash digestTreeSync(MerkleNode root, DigestType digestType) {
		return merkleHashBuilder.digestTreeSync(root);
	}

	@Override
	public FutureMerkleHash digestTreeAsync(MerkleNode root, DigestType digestType) {
		return merkleHashBuilder.digestTreeAsync(root);
	}

	/**
	 * Compute and store hash for null using different digest types.
	 */
	private void buildNullHashes() {
		nullHashes = new HashMap<>();
		for (DigestType digestType : DigestType.values()) {
			HashBuilder hb = new HashBuilder(digestType);
			nullHashes.put(digestType, hb.build());
		}
	}

	@Override
	public Hash getNullHash(DigestType digestType) {
		return nullHashes.get(digestType);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void digestSync(MerkleInternal node, List<Hash> childHashes) {
		try {
			node.setHash(merkleInternalDigestProvider.compute(node, childHashes, MERKLE_DIGEST_TYPE));
		} catch (NoSuchAlgorithmException e) {
			throw new CryptographyException(e, LogMarker.EXCEPTION);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void verifyAsync(final TransactionSignature signature) {
		try {
			verificationQueue.put(Collections.singletonList(signature));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void verifyAsync(final List<TransactionSignature> signatures) {
		try {
			verificationQueue.put(signatures);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Future<Boolean> verifyAsync(final byte[] data, final byte[] signature, final byte[] publicKey,
			final SignatureType signatureType) {

		final TransactionSignature wrappedSignature = wrap(data, signature, publicKey, signatureType);

		try {
			verificationQueue.put(Collections.singletonList(wrappedSignature));

			return new WrappingLambdaFuture<>(() -> {
				try {
					return wrappedSignature.waitForFuture();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return null;
				}
			}, () -> wrappedSignature.getSignatureStatus() == VerificationStatus.VALID);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CryptographyException(ex, LogMarker.TESTING_EXCEPTIONS);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean verifySync(final TransactionSignature signature) {
		final VerificationProvider provider = new VerificationProvider();
		final WaitingFuture<Void> future = new WaitingFuture<>();
		future.done(null);

		return verifySyncInternal(signature, provider, future);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean verifySync(final List<TransactionSignature> signatures) {
		final VerificationProvider provider = new VerificationProvider();
		final WaitingFuture<Void> future = new WaitingFuture<>();
		future.done(null);

		boolean finalOutcome = true;

		for (TransactionSignature signature : signatures) {
			if (!verifySyncInternal(signature, provider, future)) {
				finalOutcome = false;
			}
		}

		return finalOutcome;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean verifySync(final byte[] data, final byte[] signature, final byte[] publicKey,
			final SignatureType signatureType) {
		final TransactionSignature wrappedSignature = wrap(data, signature, publicKey, signatureType);
		return verifySync(wrappedSignature);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcRunningHash(final Hashable hashable, final Hash newHashToAdd,
			final DigestType digestType) {
		try {
			hashable.setHash(runningHashProvider.compute(hashable, newHashToAdd, digestType));
		} catch (NoSuchAlgorithmException e) {
			throw new CryptographyException(e, LogMarker.EXCEPTION);
		}
	}

	/**
	 * Applies any changes in the {@link CryptoEngine} settings by stopping the {@link IntakeDispatcher} threads,
	 * applying the changes, and relaunching the {@link IntakeDispatcher} threads.
	 */
	protected synchronized void applySettings() {

		// Cleanup existing (if applicable) background threads
		if (this.verificationDispatcher != null) {
			this.verificationDispatcher.shutdown();
			this.verificationDispatcher = null;
		}

		if (this.digestDispatcher != null) {
			this.digestDispatcher.shutdown();
			this.digestDispatcher = null;
		}

		// Resize the dispatcher queues
		final Queue<List<TransactionSignature>> oldVerifierQueue = this.verificationQueue;
		this.verificationQueue = new LinkedBlockingQueue<>(settings.getCpuVerifierQueueSize());

		final Queue<List<Message>> oldDigestQueue = this.digestQueue;
		this.digestQueue = new LinkedBlockingQueue<>(settings.getCpuDigestQueueSize());

		if (oldVerifierQueue != null && oldVerifierQueue.size() > 0) {
			this.verificationQueue.addAll(oldVerifierQueue);
		}

		if (oldDigestQueue != null && oldDigestQueue.size() > 0) {
			this.digestQueue.addAll(oldDigestQueue);
		}


		// Launch new background threads with the new settings
		this.verificationDispatcher = new IntakeDispatcher<>(this, TransactionSignature.class, this.verificationQueue,
				new VerificationProvider(), settings.computeCpuVerifierThreadCount(), this::verificationHandler);
		this.digestDispatcher = new IntakeDispatcher<>(this, Message.class, this.digestQueue, new DigestProvider(),
				settings.computeCpuDigestThreadCount(), this::digestHandler);
	}

	/**
	 * Supplier implementation for {@link AsyncDigestHandler} used by the {@link #CryptoEngine(CryptographySettings)}
	 * constructor.
	 *
	 * @param provider
	 * 		the required {@link OperationProvider} to be used while performing the cryptographic transformations
	 * @param workItems
	 * 		the {@link List} of items to be processed by the created {@link AsyncOperationHandler} implementation
	 * @return an {@link AsyncOperationHandler} implementation
	 */
	private AsyncDigestHandler digestHandler(final DigestProvider provider, final List<Message> workItems) {
		return new AsyncDigestHandler(workItems, provider);
	}

	/**
	 * Supplier implementation for {@link AsyncVerificationHandler} used by the {@link #CryptoEngine()} constructor.
	 *
	 * @param provider
	 * 		the required {@link OperationProvider} to be used while performing the cryptographic transformations
	 * @param workItems
	 * 		the {@link List} of items to be processed by the created {@link AsyncOperationHandler} implementation
	 * @return an {@link AsyncOperationHandler} implementation
	 */
	private AsyncVerificationHandler verificationHandler(final VerificationProvider provider,
			final List<TransactionSignature> workItems) {
		return new AsyncVerificationHandler(workItems, provider);
	}

	/**
	 * Enumerates all available OpenCL devices and sets the {@link #gpuAvailable} and {@link #openCLAvailable} fields
	 * appropriately based on the available hardware.
	 */
	private void detectSystemFeatures() {
		try {
			setExceptionsEnabled(true);
			openCLAvailable = false;
			gpuAvailable = false;

			final int[] count = new int[1];

			// Request the number of available OpenCL platforms
			clGetPlatformIDs(0, null, count);

			final int platformCount = count[0];
			count[0] = 0;

			if (platformCount < 1) {
				return;
			}

			// There is at least one OpenCL platform available
			openCLAvailable = true;

			// Retrieve array of platforms
			final cl_platform_id[] platforms = new cl_platform_id[platformCount];
			clGetPlatformIDs(platforms.length, platforms, null);

			// Enumerate devices for each platform
			for (cl_platform_id plat : platforms) {
				// Get number of devices available in this platform
				try {
					clGetDeviceIDs(plat, CL_DEVICE_TYPE_ALL, 0, null, count);

					final int deviceCount = count[0];
					count[0] = 0;

					if (deviceCount < 1) {
						continue;
					}

					// Retrieve array of devices
					final cl_device_id[] devices = new cl_device_id[deviceCount];
					clGetDeviceIDs(plat, CL_DEVICE_TYPE_ALL, devices.length, devices, null);

					// Enumerate the devices & detect existence of supported CPU or GPU
					for (cl_device_id dev : devices) {
						if (isSupportedDevice(dev)) {
							log.debug(LOGM_ADV_CRYPTO_SYSTEM,
									"Adv Crypto Subsystem: Located an acceptable GPU acceleration device ({})",
									resolveDeviceName(dev));
							gpuAvailable = true;
						}

						clReleaseDevice(dev);
					}

					if (gpuAvailable) {
						break;
					}
				} catch (CLException ex) {
					// Suppress Exception
				}
			}

		} catch (CLException | UnsatisfiedLinkError | NoClassDefFoundError ex) {
			log.info(LOGM_ADV_CRYPTO_SYSTEM, "Adv Crypto Subsystem: No supported GPU acceleration device found");

			log.debug(LOGM_OPENCL_INIT_EXCEPTIONS,
					"Adv Crypto Subsystem: Caught an unhandled OpenCL exception during device detection", ex);

			openCLAvailable = false;
			gpuAvailable = false;
		}
	}

	/**
	 * Determines if the given device handle represents a supported OpenCL device.
	 *
	 * @param device
	 * 		the OpenCL device handle
	 * @return true if this is a supported device; false otherwise
	 */
	private boolean isSupportedDevice(final cl_device_id device) {
		// Retrieve the device type
		final long[] longRef = new long[1];
		clGetDeviceInfo(device, CL_DEVICE_TYPE, Sizeof.cl_long, Pointer.to(longRef), null);

		final long deviceType = longRef[0];
		longRef[0] = 0;

		// Exit early if the device is not a CPU or GPU
		if (deviceType != CL_DEVICE_TYPE_GPU) {
			return false;
		}

		// Retrieve the vendor name
		clGetDeviceInfo(device, CL_DEVICE_VENDOR, 0, null, longRef);

		final int vendorLength = (int) longRef[0];
		final byte[] vendorRef = new byte[vendorLength + 1];
		clGetDeviceInfo(device, CL_DEVICE_VENDOR, Sizeof.cl_char * (vendorLength + 1), Pointer.to(vendorRef), null);

		final String vendorName = new String(vendorRef, 0, vendorLength);
		final String deviceName = resolveDeviceName(device);

		final DeviceVendor vendor = DeviceVendor.resolve(vendorName);
		final DeviceName name = DeviceName.resolve(deviceName);

		return (DeviceVendor.NVIDIA.equals(vendor) && DeviceName.NVIDIA_TESLA.equals(name)) || DeviceVendor.AMD.equals(
				vendor);
	}

	/**
	 * Uses OpenCL to request the device name from the underlying hardware device.
	 *
	 * @param device
	 * 		the OpenCL device handle
	 * @return the device name reported by the underlying hardware
	 */
	private String resolveDeviceName(final cl_device_id device) {
		// Retrieve the device type
		final long[] longRef = new long[1];
		clGetDeviceInfo(device, CL_DEVICE_NAME, 0, null, longRef);

		final int nameLength = (int) longRef[0];
		final byte[] nameRef = new byte[nameLength];

		clGetDeviceInfo(device, CL_DEVICE_NAME, Sizeof.cl_char * nameLength, Pointer.to(nameRef), null);
		return new String(nameRef, 0, nameRef.length - 1);
	}

	/**
	 * Efficiently builds a {@link TransactionSignature} instance from the supplied components.
	 *
	 * @param data
	 * 		the original contents that the signature should be verified against
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key required to validate the signature
	 * @param signatureType
	 * 		the type of signature to be verified
	 * @return a {@link TransactionSignature} containing the provided components
	 */
	private TransactionSignature wrap(final byte[] data, final byte[] signature, final byte[] publicKey,
			final SignatureType signatureType) {
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("data");
		}

		if (signature == null || signature.length == 0) {
			throw new IllegalArgumentException("signature");
		}

		if (publicKey == null || publicKey.length == 0) {
			throw new IllegalArgumentException("publicKey");
		}

		final ByteBuffer buffer = ByteBuffer.allocate(data.length + signature.length + publicKey.length);
		final int sigOffset = data.length;
		final int pkOffset = sigOffset + signature.length;

		buffer.put(data).put(signature).put(publicKey);

		return new TransactionSignature(buffer.array(), sigOffset, signature.length, pkOffset,
				publicKey.length, 0, data.length, signatureType);
	}

	/**
	 * Common private utility method for performing synchronous digest computations.
	 *
	 * @param message
	 * 		the message contents to be hashed
	 * @param provider
	 * 		the underlying provider to be used
	 * @param future
	 * 		the {@link Future} to be associated with the {@link Message}
	 * @return the cryptographic hash for the given message contents
	 */
	private byte[] digestSyncInternal(final Message message, final DigestProvider provider,
			final WaitingFuture<Void> future) {
		byte[] hash = null;
		try {
			hash = provider.compute(message, message.getDigestType());
			message.setHash(hash);
		} catch (NoSuchAlgorithmException ex) {
			message.setFuture(future);
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}

		message.setFuture(future);

		return hash;
	}

	/**
	 * Common private utility method for performing synchronous signature verification.
	 *
	 * @param signature
	 * 		the signature to be verified
	 * @param provider
	 * 		the underlying provider to be used
	 * @param future
	 * 		the {@link Future} to be associated with the {@link TransactionSignature}
	 * @return true if the signature is valid; otherwise false
	 */
	private boolean verifySyncInternal(final TransactionSignature signature, final VerificationProvider provider,
			final WaitingFuture<Void> future) {
		boolean isValid = false;

		try {
			isValid = provider.compute(signature, signature.getSignatureType());

			signature.setSignatureStatus(((isValid) ? VerificationStatus.VALID : VerificationStatus.INVALID));
			signature.setFuture(future);
		} catch (NoSuchAlgorithmException ex) {
			signature.setFuture(future);
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}


		return isValid;
	}
}
