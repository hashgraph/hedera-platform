/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A description of a single statistic that is monitored here.
 */
public class StatEntry {
	/** the kind of statistic (stats are grouped or filtered by this) */
	public final String category;
	/** a short name for the statistic */
	public final String name;
	/** a one-sentence description of the statistic */
	public final String desc;
	/** a string that can be passed to String.format() to format the statistic */
	public final String format;
	/** the statistics object (if it implements StatsBuffered), else null */
	public StatsBuffered buffered;
	/** a lambda that instantiates and initializes, using the given half life */
	public final Function<Double, StatsBuffered> init;
	/** a lambda that resets the statistic, using the given half life */
	public final Consumer<Double> reset;
	/** a lambda that returns the statistic string */
	public final Supplier<Object> statsStringSupplier;
	/** a lambda that returns the statistic string and resets it at the same time */
	public final Supplier<Object> resetStatsStringSupplier;

	/**
	 * stores all the parameters, which can be accessed directly
	 *
	 * @param category
	 * 		the kind of statistic (stats are grouped or filtered by this)
	 * @param name
	 * 		a short name for the statistic
	 * @param desc
	 * 		a one-sentence description of the statistic
	 * @param format
	 * 		a string that can be passed to String.format() to format the statistic
	 * @param buffered
	 * 		the statistic object (if it implements StatsBuffered), else null
	 * @param init
	 * 		a lambda that instantiates and initializes, using the given half life
	 * @param reset
	 * 		a lambda that resets the statistic, using the given half life
	 * @param statsStringSupplier
	 * 		a lambda that returns the statistic string
	 * @param resetStatsStringSupplier
	 * 		a lambda that returns the statistic string and resets the value at the same time
	 */
	public StatEntry(final String category,
			final String name,
			final String desc,
			final String format,
			final StatsBuffered buffered,
			final Function<Double, StatsBuffered> init,
			final Consumer<Double> reset,
			final Supplier<Object> statsStringSupplier,
			final Supplier<Object> resetStatsStringSupplier) {
		this.category = category;
		this.name = name;
		this.desc = desc;
		this.format = format;
		this.init = init;
		this.reset = reset;
		this.statsStringSupplier = statsStringSupplier;
		this.buffered = buffered;
		this.resetStatsStringSupplier = resetStatsStringSupplier;
	}

	/**
	 * Same as
	 * {@link StatEntry#StatEntry(String, String, String, String, StatsBuffered, Function, Consumer, Supplier, Supplier)}
	 * with the resetSupplier being set to supplier
	 */
	public StatEntry(final String category,
			final String name,
			final String desc,
			final String format,
			final StatsBuffered buffered,
			final Function<Double, StatsBuffered> init,
			final Consumer<Double> reset,
			final Supplier<Object> statsStringSupplier) {
		this(category, name, desc, format, buffered, init, reset, statsStringSupplier, statsStringSupplier);
	}

}
