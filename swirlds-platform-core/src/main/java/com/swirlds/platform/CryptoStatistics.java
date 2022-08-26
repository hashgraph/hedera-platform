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

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.internal.AbstractStatistics;

import java.util.concurrent.atomic.AtomicLong;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_3;

/**
 * This class collects and reports various statistics about advanced cryptography module operation.
 */
public class CryptoStatistics extends AbstractStatistics {

	private RunningAverageMetric avgDigestQueueDepth;

	private RunningAverageMetric avgDigestBatchSize;

	private SpeedometerMetric digWorkPulsePerSecond;

	private RunningAverageMetric avgDigestTime;

	private RunningAverageMetric avgDigestWorkItemSubmitTime;

	private SpeedometerMetric digLockUpgradesPerSecond;

	private SpeedometerMetric digSpansPerSecond;

	private SpeedometerMetric digBatchesPerSecond;

	private RunningAverageMetric avgDigestSliceSize;

	private SpeedometerMetric digPerSec;

	private Counter totalDigests;

	private AtomicLong minDigestBatchSize = new AtomicLong(Long.MAX_VALUE);
	private AtomicLong maxDigestBatchSize = new AtomicLong(Long.MIN_VALUE);

	private RunningAverageMetric avgSigQueueDepth;

	private RunningAverageMetric avgSigBatchSize;

	private SpeedometerMetric sigWorkPulsePerSecond;

	private RunningAverageMetric avgSigTime;
	private RunningAverageMetric avgSigWorkItemSubmitTime;

	private SpeedometerMetric sigLockUpgradesPerSecond;

	private SpeedometerMetric sigSpansPerSecond;

	private SpeedometerMetric sigBatchesPerSecond;

	private RunningAverageMetric avgSigSliceSize;

	private SpeedometerMetric sigPerSec;

	private SpeedometerMetric sigValidPerSec;

	private SpeedometerMetric sigInvalidPerSec;

	private RunningAverageMetric avgSigIntakeQueueDepth;

	private SpeedometerMetric sigIntakePulsePerSecond;

	private RunningAverageMetric avgSigIntakePulseTime;

	private RunningAverageMetric avgSigIntakeEnqueueTime;

	private RunningAverageMetric avgSigIntakeListSize;

	private RunningAverageMetric avgPlatformEnqueueTime;

	private RunningAverageMetric avgPlatformExpandTime;

	private Counter totalSig;

	private Counter totalSigValid;

	private Counter totalSigInvalid;

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
	public Metric[] getStatEntriesArray() {
		avgDigestQueueDepth = new RunningAverageMetric(
				CATEGORY,
				"DigQuDepth",
				"average digest queue depth",
				FORMAT_11_3
		);
		avgSigQueueDepth = new RunningAverageMetric(
				CATEGORY,
				"SigQuDepth",
				"average signature queue depth",
				FORMAT_11_3
		);
		avgDigestBatchSize = new RunningAverageMetric(
				CATEGORY,
				"DigBatchSz",
				"average digest batch size",
				FORMAT_11_3
		);
		avgSigBatchSize = new RunningAverageMetric(
				CATEGORY,
				"SigBatchSz",
				"average signature batch size",
				FORMAT_11_3
		);
		digWorkPulsePerSecond = new SpeedometerMetric(
				CATEGORY,
				"DigPulse/sec",
				"average digest worker pulses per second",
				FORMAT_11_3
		);
		digLockUpgradesPerSecond = new SpeedometerMetric(
				CATEGORY,
				"DigLockUp/sec",
				"average digest lock upgrades per second",
				FORMAT_11_3
		);
		digSpansPerSecond = new SpeedometerMetric(
				CATEGORY,
				"DigSpans/sec",
				"average: digest batch spans per second",
				FORMAT_11_3
		);
		digBatchesPerSecond = new SpeedometerMetric(
				CATEGORY,
				"DigBatches/sec",
				"average: digest batches created per second",
				FORMAT_11_3
		);
		digPerSec = new SpeedometerMetric(
				CATEGORY,
				"Dig/sec",
				"number of digests per second (complete)",
				FORMAT_11_3
		);
		sigWorkPulsePerSecond = new SpeedometerMetric(
				CATEGORY,
				"SigPulse/sec",
				"average Signature worker pulses per second",
				FORMAT_11_3
		);
		avgDigestTime = new RunningAverageMetric(
				CATEGORY,
				"DigWrkTime",
				"average: time spent (in millis) in digest worker pulses",
				FORMAT_11_3
		);
		avgSigTime = new RunningAverageMetric(
				CATEGORY,
				"SigWrkTime",
				"average: time spent (in millis) in signature worker pulses",
				FORMAT_11_3
		);
		avgDigestWorkItemSubmitTime = new RunningAverageMetric(
				CATEGORY,
				"DigSubWrkItmTime",
				"average: time spent (in millis) in digest submission",
				FORMAT_11_3
		);
		avgSigWorkItemSubmitTime = new RunningAverageMetric(
				CATEGORY,
				"SigSubWrkItmTime",
				"average: time spent (in millis) in signature verification submission",
				FORMAT_11_3
		);
		sigLockUpgradesPerSecond = new SpeedometerMetric(
				CATEGORY,
				"SigLockUp/sec",
				"average Signature lock upgrades per second",
				FORMAT_11_3
		);
		sigSpansPerSecond = new SpeedometerMetric(
				CATEGORY,
				"SigSpans/sec",
				"average: signature verification batch spans per second",
				FORMAT_11_3
		);
		sigBatchesPerSecond = new SpeedometerMetric(
				CATEGORY,
				"SigBatches/sec",
				"average: signature verification batches created per second",
				FORMAT_11_3
		);
		avgDigestSliceSize = new RunningAverageMetric(
				CATEGORY,
				"DigSliceSz",
				"average digest slice size",
				FORMAT_11_3
		);
		avgSigSliceSize = new RunningAverageMetric(
				CATEGORY,
				"SigSliceSz",
				"average signature slice size",
				FORMAT_11_3
		);
		sigPerSec = new SpeedometerMetric(
				CATEGORY,
				"Sig/sec",
				"number of signature verifications per second (complete)",
				FORMAT_11_3
		);
		sigValidPerSec = new SpeedometerMetric(
				CATEGORY,
				"SigVal/sec",
				"number of valid signatures per second",
				FORMAT_11_3
		);
		sigInvalidPerSec = new SpeedometerMetric(
				CATEGORY,
				"SigInval/sec",
				"number of invalid signatures per second",
				FORMAT_11_3
		);
		avgSigIntakeQueueDepth = new RunningAverageMetric(
				CATEGORY,
				"SigIntakeQueueDepth",
				"depth of the signature intake queue",
				FORMAT_11_3
		);
		sigIntakePulsePerSecond = new SpeedometerMetric(
				CATEGORY,
				"SigIntakePulse/sec",
				"number of times the signature intake worker thread is executed per second",
				FORMAT_11_3
		);
		avgSigIntakePulseTime = new RunningAverageMetric(
				CATEGORY,
				"SigIntakePulseTime",
				"average time spent (in millis) of each signature intake execution",
				FORMAT_11_3
		);
		avgSigIntakeEnqueueTime = new RunningAverageMetric(
				CATEGORY,
				"SigIntakeEnqueueTime",
				"average time spent (in millis) of each intake enqueue call",
				FORMAT_11_3
		);
		avgSigIntakeListSize = new RunningAverageMetric(
				CATEGORY,
				"SigIntakeListSize",
				"average size of each list sent to the intake worker",
				FORMAT_11_3
		);
		avgPlatformEnqueueTime = new RunningAverageMetric(
				CATEGORY,
				"PlatSigEnqueueTime",
				"average time spent (in millis) by the platform enqueuing signatures",
				FORMAT_11_3
		);
		avgPlatformExpandTime = new RunningAverageMetric(
				CATEGORY,
				"PlatSigExpandTime",
				"average time spent (in millis) by the platform calling the expandSignatures method",
				FORMAT_11_3
		);
		totalDigests = new Counter(
				CATEGORY,
				"TtlDig",
				"running total: digests computed"
		);
		totalSig = new Counter(
				CATEGORY,
				"TtlSig",
				"running total: Signatures Verified"
		);
		totalSigValid = new Counter(
				CATEGORY,
				"TtlSigVal",
				"running total: valid signatures verified"
		);
		totalSigInvalid = new Counter(
				CATEGORY,
				"TtlSigInval",
				"running total: invalid signatures verified"
		);

		return new Metric[] {
				avgDigestQueueDepth,
				avgSigQueueDepth,
				avgDigestBatchSize,
				new StatEntry(
						CATEGORY,
						"MinDigBatchSz",
						"minimum digest batch size",
						"%,d",
						null,
						null,
						null,
						() -> minDigestBatchSize.longValue()),
				new StatEntry(
						CATEGORY,
						"MaxDigBatchSz",
						"maximum digest batch size",
						"%,d",
						null,
						null,
						null,
						() -> maxDigestBatchSize.longValue()),
				avgSigBatchSize,
				new StatEntry(
						CATEGORY,
						"MinSigBatchSz",
						"minimum signature batch size",
						"%,d",
						null,
						null,
						null,
						() -> minSigBatchSize.longValue()),
				new StatEntry(
						CATEGORY,
						"MaxSigBatchSz",
						"maximum signature batch size",
						"%,d",
						null,
						null,
						null,
						() -> maxSigBatchSize.longValue()),
				digWorkPulsePerSecond,
				sigWorkPulsePerSecond,
				avgDigestTime,
				avgSigTime,
				avgDigestWorkItemSubmitTime,
				avgSigWorkItemSubmitTime,
				digLockUpgradesPerSecond,
				sigLockUpgradesPerSecond,
				digSpansPerSecond,
				sigSpansPerSecond,
				digBatchesPerSecond,
				sigBatchesPerSecond,
				avgDigestSliceSize,
				avgSigSliceSize,
				digPerSec,
				sigPerSec,
				sigValidPerSec,
				sigInvalidPerSec,
				avgSigIntakeQueueDepth,
				sigIntakePulsePerSecond,
				avgSigIntakePulseTime,
				avgSigIntakeEnqueueTime,
				avgSigIntakeListSize,
				avgPlatformEnqueueTime,
				avgPlatformExpandTime,
				totalDigests,
				totalSig,
				totalSigValid,
				totalSigInvalid
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
		totalDigests.increment();
	}

	public void setSigHandleExecution(double sliceSize, boolean isValid) {
		avgSigSliceSize.recordValue(sliceSize);
		sigPerSec.cycle();
		totalSig.increment();
		if (isValid) {
			sigValidPerSec.cycle();
			totalSigValid.increment();
		} else {
			sigInvalidPerSec.cycle();
			totalSigInvalid.increment();
		}
	}
}
