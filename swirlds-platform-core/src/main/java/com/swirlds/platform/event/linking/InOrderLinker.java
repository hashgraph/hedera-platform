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

package com.swirlds.platform.event.linking;

import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * An {@link EventLinker} which expects events to be provided in topological order. If an out-of-order event is provided,
 * it is logged and discarded.
 */
public class InOrderLinker extends AbstractEventLinker {
	private static final Logger LOG = LogManager.getLogger();
	private final ParentFinder parentFinder;
	/** Provides the most recent event by the supplied creator ID */
	private final Function<Long, EventImpl> mostRecentEvent;
	private EventImpl linkedEvent = null;

	public InOrderLinker(final ParentFinder parentFinder, final Function<Long, EventImpl> mostRecentEvent) {
		this.parentFinder = parentFinder;
		this.mostRecentEvent = mostRecentEvent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void linkEvent(final GossipEvent event) {
		final ChildEvent childEvent = parentFinder.findParents(
				event,
				getMinGenerationNonAncient()
		);
		if (childEvent.isOrphan()) {
			logMissingParents(childEvent);
			childEvent.orphanForever();
			return;
		}
		if (linkedEvent != null) {
			LOG.error(LogMarker.EXCEPTION.getMarker(),
					"Unprocessed linked event: {}",
					() -> EventStrings.toMediumString(linkedEvent));
			linkedEvent.clear();
		}
		linkedEvent = childEvent.getChild();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasLinkedEvents() {
		return linkedEvent != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventImpl pollLinkedEvent() {
		final EventImpl tmp = linkedEvent;
		linkedEvent = null;
		return tmp;
	}

	private void logMissingParents(final ChildEvent event) {
		final GossipEvent e = event.getChild().getBaseEvent();
		LOG.error(INVALID_EVENT_ERROR.getMarker(),
				"""
						Invalid event! {} missing for {} min gen:{}
						most recent event by missing self parent creator:{}
						most recent event by missing self parent creator:{}""",
				event::missingParentsString,
				() -> EventStrings.toMediumString(e),
				this::getMinGenerationNonAncient,
				() -> EventStrings.toShortString(mostRecentEvent.apply(e.getHashedData().getCreatorId())),
				() -> EventStrings.toShortString(mostRecentEvent.apply(e.getUnhashedData().getOtherId()))
		);
	}
}
