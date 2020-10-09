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

/**
 * Each instance of this class can be used to throttle some kind of flow, to allow only a certain number of
 * transactions per secondm or bytes per second or events per second etc.
 *
 * For throttling transactions per second, the instance remembers how many transactions have occurred recently, and
 * will then answer whether a new transaction is allowed, or should be blocked.  This also works for sets of
 * transactions, where the entire set is either accepted or rejected.  So, to limit a flow of bytes per second, each
 * byte can be treated as a "transaction", and a block of 1kB can be considered a set of 1024 transactions.
 *
 * Given the number of transactions per second (tps) and the max number of seconds worth of transactions that could
 * ever come in a single burst (burstPeriod), this uses a leaky bucket model to throttle it. Each allowed transaction
 * increases the contents of the bucket by 1. Each nanosecond decreases the contents of the bucket by a billionth of
 * tps.  If the next transaction or block of transactions would fill the bucket to more than tps * burstPeriod, then
 * that transaction or block is disallowed.
 *
 * For example, to throttle smart contract calls to 1.5 per second on this computer, it would be instantiated as:
 *
 * <pre>{@code
 * Throttle contractThrottle = new Throttle(1.5);   //throttle to 1.5 tps for this node
 * }</pre>
 *
 * and then when a transaction is received, do this:
 *
 * <pre>{@code
 * if (contractThrottle.allow()) {
 *    //accept the transaction
 * } else {
 *     //reject the transaction because BUSY
 * }
 * }</pre>
 */
public class Throttle {
	//allow a max of tps transactions per second, on average
	private double tps;
	//after a long time of no transactions, allow a burst of tps * burstPeriod transactions all at once. This is how
	//long it takes the bucket to leak empty.
	private double burstPeriod;
	//the size of the bucket
	private double capacity;
	//amount of transaction traffic we have had recently. This is always in the range [0, capacity].
	private double traffic;
	//the last time a transaction was received
	private long lastTime;

	/** get max transactions per second allowed, on average */
	public double getTps() {
		return tps;
	}

	/**
	 * Set max transactions per second allowed, on average.
	 *
	 * @param tps
	 * 		max transactions per second (negative values will be treated as 0)
	 */
	public void setTps(double tps) {
		this.tps = Math.max(0, tps);
		capacity = this.tps * burstPeriod;
	}

	/** get the number of seconds worth of traffic that can occur in a single burst */
	public double getBurstPeriod() {
		return burstPeriod;
	}

	/**
	 * set the number of seconds worth of traffic that can occur in a single burst
	 *
	 * @param burstPeriod
	 * 		bursts can allow at most this many seconds' worth of transactions at once (negative values treated as 0)
	 */
	public void setBurstPeriod(double burstPeriod) {
		this.burstPeriod = Math.max(0, burstPeriod);
		capacity = tps * burstPeriod;
	}

	/**
	 * Start throttling a new flow, allowing tps transactions per second, and bursts of at most tps transactions at
	 * once.
	 *
	 * @param tps
	 * 		the max transactions per second (on average) that is allowed (negative values treated as 0)
	 */
	public Throttle(double tps) {
		this(tps, 1);
	}

	/**
	 * Start throttling a new flow, allowing tps transactions per second, and bursts of at most tps * burstPeriod
	 * transactions at once.
	 *
	 * @param tps
	 * 		the max transactions per second (on average) that is allowed (negative values treated as 0)
	 * @param burstPeriod
	 * 		bursts can allow at most this many seconds' worth of transactions at once (negative values treated as 0)
	 */
	public Throttle(double tps, double burstPeriod) {
		this.tps = Math.max(0, tps);
		this.burstPeriod = Math.max(0, burstPeriod);
		capacity = tps * burstPeriod;
		traffic = 0;
		lastTime = System.nanoTime();
	}

	/**
	 * Can one more transaction be allowed right now?  If so, return true, and record that it was allowed
	 * (which will reduce the number allowed in the near future)
	 *
	 * @return can this number of transactions be allowed right now?
	 */
	public boolean allow() {
		return allow(1);
	}

	/**
	 * Can the given number of transactions be allowed right now?  If so, return true, and record that they were allowed
	 * (which will reduce the number allowed in the near future)
	 *
	 * @param amount
	 * 		the number of transactions in the block (must be nonnegative)
	 * @return can this number of transactions be allowed right now?
	 */
	public boolean allow(double amount) {
		//when a new transaction comes in, do this:
		long t = System.nanoTime();
		traffic = Math.min(capacity, Math.max(0, traffic - (t - lastTime) * tps / 1_000_000_000)); //make the bucket
		// leak
		lastTime = t;
		if (amount < 0 || traffic + amount > capacity) {
			return false;
		}
		traffic += amount;
		return true;
	}
}
