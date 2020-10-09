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

package com.swirlds.common;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A description of a single statistic that is monitored here.
 */
public class StatEntry {
	/** the kind of statistic (stats are grouped or filtered by this) */
	public String category;
	/** a short name for the statistic */
	public String name;
	/** a one-sentence description of the statistic */
	public String desc;
	/** a string that can be passed to String.format() to format the statistic */
	public String format;
	/** the statistics object (if it implements StatsBuffered), else null */
	public StatsBuffered buffered;
	/** a lambda that instantiates and initializes, using the given half life */
	public Function<Double, StatsBuffered> init;
	/** a lambda that resets the statistic, using the given half life */
	public Consumer<Double> reset;
	/** a lambda that returns the statistic string */
	public Supplier<Object> supplier;

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
	 * @param supplier
	 * 		a lambda that returns the statistic string
	 */
	public StatEntry(String category, String name, String desc, String format,
			StatsBuffered buffered, Function<Double, StatsBuffered> init,
			Consumer<Double> reset, Supplier<Object> supplier) {
		this.category = category;
		this.name = name;
		this.desc = desc;
		this.format = format;
		this.init = init;
		this.reset = reset;
		this.supplier = supplier;
		this.buffered = buffered;
	}

}
