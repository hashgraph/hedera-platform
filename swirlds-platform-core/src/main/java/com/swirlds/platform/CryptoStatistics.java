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

/**
 *
 */
package com.swirlds.platform;

import com.swirlds.common.internal.AbstractStatistics;
import com.swirlds.common.StatEntry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * This class collects and reports various statistics about advanced cryptography module operation.
 */
public class CryptoStatistics extends AbstractStatistics {

	private StatsRunningAverage avgDigestQueueDepth;
	private StatsRunningAverage avgDigestBatchSize;
	private StatsSpeedometer digWorkPulsePerSecond;
	private StatsRunningAverage avgDigestTime;
	private StatsRunningAverage avgDigestWorkItemSubmitTime;
	private StatsSpeedometer digLockUpgradesPerSecond;
	private StatsSpeedometer digSpansPerSecond;
	private StatsSpeedometer digBatchesPerSecond;
	private StatsRunningAverage avgDigestSliceSize;
	private StatsSpeedometer digPerSec;
	private AtomicLong totalDigests = new AtomicLong(0);
	private AtomicLong minDigestBatchSize = new AtomicLong(Long.MAX_VALUE);
	private AtomicLong maxDigestBatchSize = new AtomicLong(Long.MIN_VALUE);

	private StatsRunningAverage avgSigQueueDepth;
	private StatsRunningAverage avgSigBatchSize;
	private StatsSpeedometer sigWorkPulsePerSecond;
	private StatsRunningAverage avgSigTime;
	private StatsRunningAverage avgSigWorkItemSubmitTime;
	private StatsSpeedometer sigLockUpgradesPerSecond;
	private StatsSpeedometer sigSpansPerSecond;
	private StatsSpeedometer sigBatchesPerSecond;
	private StatsRunningAverage avgSigSliceSize;
	private StatsSpeedometer sigPerSec;
	private StatsSpeedometer sigValidPerSec;
	private StatsSpeedometer sigInvalidPerSec;

	private StatsRunningAverage avgSigIntakeQueueDepth;
	private StatsSpeedometer sigIntakePulsePerSecond;
	private StatsRunningAverage avgSigIntakePulseTime;
	private StatsRunningAverage avgSigIntakeEnqueueTime;
	private StatsRunningAverage avgSigIntakeListSize;
	private StatsRunningAverage avgPlatformEnqueueTime;
	private StatsRunningAverage avgPlatformExpandTime;

	private AtomicLong totalSig = new AtomicLong(0);
	private AtomicLong totalSigValid = new AtomicLong(0);
	private AtomicLong totalSigInvalid = new AtomicLong(0);

	private AtomicLong minSigBatchSize = new AtomicLong(Long.MAX_VALUE);
	private AtomicLong maxSigBatchSize = new AtomicLong(Long.MIN_VALUE);

	// private instance, so that it can be
	// accessed by only by getInstance() method
	private static volatile CryptoStatistics instance;
	private static boolean isRecording = false;

	static private String CATEGORY = "crypto";

	public static CryptoStatistics getInstance() {
		// Double-Checked Locking works if the field is volatile from JDK5 onwards
		if (instance == null) {
			// synchronized block to remove overhead
			synchronized (CryptoStatistics.class) {
				if (instance == null) {
					// if instance is null, initialize
					instance = new CryptoStatistics();
				}
			}
		}
		return instance;
	}

	private CryptoStatistics() {
		super();
	}

	static void startRecording() {
		if (!isRecording) {
			isRecording = true;
		}
	}

	public boolean recordingStatus() {
		return isRecording;
	}

	@Override
	public void updateOthers() {
		try {

		} catch (Exception e) {
			// ignore exceptions
		}
	}

	@Override
	public StatEntry[] getStatEntriesArray() {
		return new StatEntry[] {//
				new StatEntry(//
						CATEGORY,//
						"DigQuDepth",//
						"average digest queue depth",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgDigestQueueDepth = new StatsRunningAverage(h);
							return avgDigestQueueDepth;
						},//
						null,//
						() -> avgDigestQueueDepth.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"SigQuDepth",//
						"average signature queue depth",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgSigQueueDepth = new StatsRunningAverage(h);
							return avgSigQueueDepth;
						},//
						null,//
						() -> avgSigQueueDepth.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"DigBatchSz",//
						"average digest batch size",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgDigestBatchSize = new StatsRunningAverage(h);
							return avgDigestBatchSize;
						},//
						null,//
						() -> avgDigestBatchSize.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"MinDigBatchSz",//
						"minimum digest batch size",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> minDigestBatchSize.longValue()),
				new StatEntry(//
						CATEGORY,//
						"MaxDigBatchSz",//
						"maximum digest batch size",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> maxDigestBatchSize.longValue()),
				new StatEntry(//
						CATEGORY,//
						"SigBatchSz",//
						"average signature batch size",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgSigBatchSize = new StatsRunningAverage(h);
							return avgSigBatchSize;
						},//
						null,//
						() -> avgSigBatchSize.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"MinSigBatchSz",//
						"minimum signature batch size",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> minSigBatchSize.longValue()),
				new StatEntry(//
						CATEGORY,//
						"MaxSigBatchSz",//
						"maximum signature batch size",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> maxSigBatchSize.longValue()),
				new StatEntry(//
						CATEGORY,//
						"DigPulse/sec",//
						"average digest worker pulses per second",//
						"%,11.3f",//
						digWorkPulsePerSecond,//
						(h) -> {
							digWorkPulsePerSecond = new StatsSpeedometer(h);
							return digWorkPulsePerSecond;
						},//
						null,//
						() -> digWorkPulsePerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigPulse/sec",//
						"average Signature worker pulses per second",//
						"%,11.3f",//
						sigWorkPulsePerSecond,//
						(h) -> {
							sigWorkPulsePerSecond = new StatsSpeedometer(h);
							return sigWorkPulsePerSecond;
						},//
						null,//
						() -> sigWorkPulsePerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"DigWrkTime",//
						"average: time spent (in millis) in digest worker pulses",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgDigestTime = new StatsRunningAverage(h);
							return avgDigestTime;
						},//
						null,//
						() -> avgDigestTime.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"SigWrkTime",//
						"average: time spent (in millis) in signature worker pulses",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgSigTime = new StatsRunningAverage(h);
							return avgSigTime;
						},//
						null,//
						() -> avgSigTime.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"DigSubWrkItmTime",//
						"average: time spent (in millis) in digest submission",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgDigestWorkItemSubmitTime = new StatsRunningAverage(h);
							return avgDigestWorkItemSubmitTime;
						},//
						null,//
						() -> avgDigestWorkItemSubmitTime.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"SigSubWrkItmTime",//
						"average: time spent (in millis) in signature verification submission",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgSigWorkItemSubmitTime = new StatsRunningAverage(h);
							return avgSigWorkItemSubmitTime;
						},//
						null,//
						() -> avgSigWorkItemSubmitTime.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"DigLockUp/sec",//
						"average digest lock upgrades per second",//
						"%,11.3f",//
						digLockUpgradesPerSecond,//
						(h) -> {
							digLockUpgradesPerSecond = new StatsSpeedometer(h);
							return digLockUpgradesPerSecond;
						},//
						null,//
						() -> digLockUpgradesPerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigLockUp/sec",//
						"average Signature lock upgrades per second",//
						"%,11.3f",//
						sigLockUpgradesPerSecond,//
						(h) -> {
							sigLockUpgradesPerSecond = new StatsSpeedometer(h);
							return sigLockUpgradesPerSecond;
						},//
						null,//
						() -> sigLockUpgradesPerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"DigSpans/sec",//
						"average: digest batch spans per second",//
						"%,11.3f",//
						digSpansPerSecond,//
						(h) -> {
							digSpansPerSecond = new StatsSpeedometer(h);
							return digSpansPerSecond;
						},//
						null,//
						() -> digSpansPerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigSpans/sec",//
						"average: signature verification batch spans per second",//
						"%,11.3f",//
						sigSpansPerSecond,//
						(h) -> {
							sigSpansPerSecond = new StatsSpeedometer(h);
							return sigSpansPerSecond;
						},//
						null,//
						() -> sigSpansPerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"DigBatches/sec",//
						"average: digest batches created per second",//
						"%,11.3f",//
						digBatchesPerSecond,//
						(h) -> {
							digBatchesPerSecond = new StatsSpeedometer(h);
							return digBatchesPerSecond;
						},//
						null,//
						() -> digBatchesPerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigBatches/sec",//
						"average: signature verification batches created per second",//
						"%,11.3f",//
						sigBatchesPerSecond,//
						(h) -> {
							sigBatchesPerSecond = new StatsSpeedometer(h);
							return sigBatchesPerSecond;
						},//
						null,//
						() -> sigBatchesPerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"DigSliceSz",//
						"average digest slice size",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgDigestSliceSize = new StatsRunningAverage(h);
							return avgDigestSliceSize;
						},//
						null,//
						() -> avgDigestSliceSize.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"SigSliceSz",//
						"average signature slice size",//
						"%,11.3f",//
						null,//
						(h) -> {
							avgSigSliceSize = new StatsRunningAverage(h);
							return avgSigSliceSize;
						},//
						null,//
						() -> avgSigSliceSize.getWeightedMean()),
				new StatEntry(//
						CATEGORY,//
						"Dig/sec",//
						"number of digests per second (complete)",//
						"%,11.3f",//
						digPerSec,//
						(h) -> {
							digPerSec = new StatsSpeedometer(h);
							return digPerSec;
						},//
						null,//
						() -> digPerSec.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"Sig/sec",//
						"number of signature verifications per second (complete)",//
						"%,11.3f",//
						sigPerSec,//
						(h) -> {
							sigPerSec = new StatsSpeedometer(h);
							return sigPerSec;
						},//
						null,//
						() -> sigPerSec.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigVal/sec",//
						"number of valid signatures per second",//
						"%,11.3f",//
						sigValidPerSec,//
						(h) -> {
							sigValidPerSec = new StatsSpeedometer(h);
							return sigValidPerSec;
						},//
						null,//
						() -> sigValidPerSec.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigInval/sec",//
						"number of invalid signatures per second",//
						"%,11.3f",//
						sigInvalidPerSec,//
						(h) -> {
							sigInvalidPerSec = new StatsSpeedometer(h);
							return sigInvalidPerSec;
						},//
						null,//
						() -> sigInvalidPerSec.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigIntakeQueueDepth",//
						"depth of the signature intake queue",//
						"%,11.3f",//
						avgSigIntakeQueueDepth,//
						(h) -> {
							avgSigIntakeQueueDepth = new StatsRunningAverage(h);
							return avgSigIntakeQueueDepth;
						},//
						null,//
						() -> avgSigIntakeQueueDepth.getWeightedMean()),//
				new StatEntry(//
						CATEGORY,//
						"SigIntakePulse/sec",//
						"number of times the signature intake worker thread is executed per second",//
						"%,11.3f",//
						sigIntakePulsePerSecond,//
						(h) -> {
							sigIntakePulsePerSecond = new StatsSpeedometer(h);
							return sigIntakePulsePerSecond;
						},//
						null,//
						() -> sigIntakePulsePerSecond.getCyclesPerSecond()),//
				new StatEntry(//
						CATEGORY,//
						"SigIntakePulseTime",//
						"average time spent (in millis) of each signature intake execution",//
						"%,11.3f",//
						avgSigIntakePulseTime,//
						(h) -> {
							avgSigIntakePulseTime = new StatsRunningAverage(h);
							return avgSigIntakePulseTime;
						},//
						null,//
						() -> avgSigIntakePulseTime.getWeightedMean()),//
				new StatEntry(//
						CATEGORY,//
						"SigIntakeEnqueueTime",//
						"average time spent (in millis) of each intake enqueue call",//
						"%,11.3f",//
						avgSigIntakeEnqueueTime,//
						(h) -> {
							avgSigIntakeEnqueueTime = new StatsRunningAverage(h);
							return avgSigIntakeEnqueueTime;
						},//
						null,//
						() -> avgSigIntakeEnqueueTime.getWeightedMean()),//
				new StatEntry(//
						CATEGORY,//
						"SigIntakeListSize",//
						"average size of each list sent to the intake worker",//
						"%,11.3f",//
						avgSigIntakeListSize,//
						(h) -> {
							avgSigIntakeListSize = new StatsRunningAverage(h);
							return avgSigIntakeListSize;
						},//
						null,//
						() -> avgSigIntakeListSize.getWeightedMean()),//
				new StatEntry(//
						CATEGORY,//
						"PlatSigEnqueueTime",//
						"average time spent (in millis) by the platform enqueuing signatures",//
						"%,11.3f",//
						avgPlatformEnqueueTime,//
						(h) -> {
							avgPlatformEnqueueTime = new StatsRunningAverage(h);
							return avgPlatformEnqueueTime;
						},//
						null,//
						() -> avgPlatformEnqueueTime.getWeightedMean()),//
				new StatEntry(//
						CATEGORY,//
						"PlatSigExpandTime",//
						"average time spent (in millis) by the platform calling the expandSignatures method",//
						"%,11.3f",//
						avgPlatformExpandTime,//
						(h) -> {
							avgPlatformExpandTime = new StatsRunningAverage(h);
							return avgPlatformExpandTime;
						},//
						null,//
						() -> avgPlatformExpandTime.getWeightedMean()),//
				new StatEntry(//
						CATEGORY,//
						"TtlDig",//
						"running total: digests computed",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> totalDigests.longValue()),//
				new StatEntry(//
						CATEGORY,//
						"TtlSig",//
						"running total: Signatures Verified",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> totalSig.longValue()),//
				new StatEntry(//
						CATEGORY,//
						"TtlSigVal",//
						"running total: valid signatures verified",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> totalSigValid.longValue()),//
				new StatEntry(//
						CATEGORY,//
						"TtlSigInval",//
						"running total: invalid signatures verified",//
						"%,d",//
						null,//
						null,//
						null,//
						() -> totalSigInvalid.longValue()),//
		};
	}

	public void setSigIntakeWorkerValues(final int queueDepth, final double workerTime, final int listSize) {
		sigIntakePulsePerSecond.cycle();
		avgSigIntakeQueueDepth.recordValue(queueDepth);
		avgSigIntakePulseTime.recordValue(workerTime);
		avgSigIntakeListSize.recordValue(listSize);
	}

	public void setSigIntakeEnqueueValues(final double enqueueTime) {
		avgSigIntakeEnqueueTime.recordValue(enqueueTime);
	}

	public void setPlatformSigIntakeValues(final double enqueueTime, final double expandTime) {
		avgPlatformEnqueueTime.recordValue(enqueueTime);
		avgPlatformExpandTime.recordValue(expandTime);
	}


	private void setDigestWorkerValues(double digestQueueDepth, double digestBatchSize, double time) {
		avgDigestQueueDepth.recordValue(digestQueueDepth);
		avgDigestBatchSize.recordValue(digestBatchSize);
		digWorkPulsePerSecond.cycle();
		avgDigestTime.recordValue(time);
		minDigestBatchSize.getAndUpdate((v) -> Math.min(v, (long) digestBatchSize));
		maxDigestBatchSize.getAndUpdate((v) -> Math.max(v, (long) digestBatchSize));
	}

	private void setSigWorkerValues(double sigQueueDepth, double sigBatchSize, double time) {
		avgSigQueueDepth.recordValue(sigQueueDepth);
		avgSigBatchSize.recordValue(sigBatchSize);
		sigWorkPulsePerSecond.cycle();
		avgSigTime.recordValue(time);
		minSigBatchSize.getAndUpdate((v) -> Math.min(v, (long) sigBatchSize));
		maxSigBatchSize.getAndUpdate((v) -> Math.max(v, (long) sigBatchSize));
	}


	private void setSigSubmitWorkItem(double time, boolean lockUpgraded) {
		avgSigWorkItemSubmitTime.recordValue(time);
		if (lockUpgraded) {
			sigLockUpgradesPerSecond.cycle();
		}

	}

	private void setDigestSubmitWorkItem(double time, boolean lockUpgraded) {
		avgDigestWorkItemSubmitTime.recordValue(time);
		if (lockUpgraded) {
			digLockUpgradesPerSecond.cycle();
		}
	}


	public void setDigestHandleExecution(double sliceSize) {
		avgDigestSliceSize.recordValue(sliceSize);
		digPerSec.cycle();
		totalDigests.incrementAndGet();
	}

	public void setSigHandleExecution(double sliceSize, boolean isValid) {
		avgSigSliceSize.recordValue(sliceSize);
		sigPerSec.cycle();
		totalSig.incrementAndGet();
		if (isValid) {
			sigValidPerSec.cycle();
			totalSigValid.incrementAndGet();
		} else {
			sigInvalidPerSec.cycle();
			totalSigInvalid.incrementAndGet();
		}
	}
}
