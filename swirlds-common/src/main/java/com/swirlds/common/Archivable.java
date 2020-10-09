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

/**
 * Some data structures such as FCMap maintain special internal data structures that improve
 * performance but are optional and not required for the object to hold its data. These special
 * data structures may have large memory footprints.
 *
 * An object that is archivable can prune away these optional data structures. After being archived,
 * such an object will continue to hold the same data and behave in the same way but it may be
 * slower.
 */
public interface Archivable {

	/**
	 * Archive this object. Will not change object behavior but may reduce performance.
	 */
	void archive();

}
