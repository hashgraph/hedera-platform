/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform.test.consensus;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.event.IndexedEvent;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This class enables methods inside ConsensusImpl to be "wrapped", allowing the arguments and
 * result of various methods to be logged and analyzed.
 *
 * <p>This class is specifically designed to allow debugging of consensus without committing any
 * changes to the production class.
 *
 * <p>Note: in order for this to be used, you must change all of the overridden methods in EventImpl
 * to protected or public. The methods in this class that require this change are left in a
 * commented state. Be sure to undo this change to EventImpl before committing.
 */
public class DebuggableConsensus extends ConsensusImpl {

    private Map<List<Long>, List<Long>> results;

    public DebuggableConsensus(
            final ConsensusMetrics consensusMetrics,
            final BiConsumer<Long, Long> minGenConsumer,
            final AddressBook addressBook) {
        super(consensusMetrics, minGenConsumer, addressBook);
        results = new HashMap<>();
    }

    public DebuggableConsensus(
            final ConsensusMetrics consensusMetrics,
            final BiConsumer<Long, Long> minGenConsumer,
            final AddressBook addressBook,
            final SignedState signedState)
            throws SignedStateLoadingException {
        super(consensusMetrics, minGenConsumer, addressBook, signedState);
        results = new HashMap<>();
    }

    private static String argString(final List<Long> args) {

        // Function enum value is stored as the last entry in the array
        String functionName =
                functions.values()[Math.toIntExact(args.get(args.size() - 1))].toString();

        StringBuilder sb = new StringBuilder().append(functionName).append("(");

        String prefix = "";
        for (int index = 0; index < args.size() - 1; index++) {
            sb.append(prefix).append(args.get(index));
            prefix = ", ";
        }

        sb.append(")");

        return sb.toString();
    }

    private static String resultString(final List<Long> results) {
        StringBuilder sb = new StringBuilder().append("[");

        String prefix = "";
        for (int index = 0; index < results.size(); index++) {
            sb.append(prefix).append(results.get(index));
            prefix = ", ";
        }

        sb.append("]");

        return sb.toString();
    }

    /** Compare all memoized results to another DebuggableConsensus object. */
    public void compareMemoizedData(final DebuggableConsensus that) {
        for (Map.Entry<List<Long>, List<Long>> entry : this.results.entrySet()) {
            List<Long> key = entry.getKey();
            List<Long> value = entry.getValue();

            if (that.results.containsKey(key)) {
                List<Long> thatValue = that.results.get(key);
                if (!value.equals(thatValue)) {
                    System.out.println(
                            argString(key)
                                    + ": "
                                    + resultString(value)
                                    + " vs "
                                    + resultString(thatValue));
                }
            }
        }
    }

    /** Shortcut for constructing a list of long values. */
    private static List<Long> longList(final long... list) {
        List<Long> ret = new LinkedList<>();
        for (long value : list) {
            ret.add(value);
        }
        return ret;
    }

    /** Utility to extract generator index from an event */
    private static long getGeneratorIndex(final EventImpl event) {
        return event == null ? -1 : ((IndexedEvent) event).getGeneratorIndex();
    }

    /** Utility enum containing memoizable functions. */
    private enum functions {
        parentRound,
        lastSee,
        seeThru,
        stronglySeeP,
        round,
        firstSelfWitnessS,
        firstWitnessS,
        stronglySeeS1,
        firstSee,
        coin,
        setFamous
    }

    /**
     * Store the result from a memoizable method call.
     *
     * @param function an enum corresponding to the function that was called
     * @param args an array of long values representing the arguments of the function
     * @param result an array of long values representing the results of the function
     */
    /*            // UNCOMMENT THIS LINE TO ENABLE DEBUGGING CODE -- REQUIRES METHODS TO BE MADE PUBLIC
    private void record(final functions function, final List<Long> args, final List<Long> result) {

    	// Add the function name to the argument list
    	args.add((long) function.ordinal());

    	if (results.containsKey(args)) {
    		// Sanity check -- the same arguments should always result in the same result(s)
    		assertEquals(results.get(args), result);
    	} else {
    		results.put(args, result);
    	}
    }

    @Override
    protected long parentRound(EventImpl x) {
    	long ret = super.parentRound(x);

    	record(functions.parentRound,
    			longList(getGeneratorIndex(x)),
    			longList(ret));

    	return ret;
    }

    @Override
    protected EventImpl lastSee(EventImpl x, long m) {
    	EventImpl ret = super.lastSee(x, m);

    	record(functions.lastSee,
    			longList(getGeneratorIndex(x), m),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected EventImpl seeThru(EventImpl x, long m, long m2) {
    	EventImpl ret = super.seeThru(x, m, m2);

    	record(functions.seeThru,
    			longList(getGeneratorIndex(x), m, m2),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected EventImpl stronglySeeP(EventImpl x, long m) {
    	EventImpl ret = super.stronglySeeP(x, m);

    	record(functions.stronglySeeP,
    			longList(getGeneratorIndex(x), m),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected long round(EventImpl x) {
    	long ret = super.round(x);

    	record(functions.round,
    			longList(getGeneratorIndex(x)),
    			longList(ret));

    	return ret;
    }

    @Override
    protected EventImpl firstSelfWitnessS(EventImpl x) {
    	EventImpl ret = super.firstSelfWitnessS(x);

    	record(functions.firstSelfWitnessS,
    			longList(getGeneratorIndex(x)),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected EventImpl firstWitnessS(EventImpl x) {
    	EventImpl ret = super.firstWitnessS(x);

    	record(functions.firstWitnessS,
    			longList(getGeneratorIndex(x)),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected EventImpl stronglySeeS1(EventImpl x, long m) {
    	EventImpl ret = super.stronglySeeS1(x, m);

    	record(functions.stronglySeeS1,
    			longList(getGeneratorIndex(x), m),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected EventImpl firstSee(EventImpl x, long m) {
    	EventImpl ret = super.firstSee(x, m);

    	record(functions.firstSee,
    			longList(getGeneratorIndex(x), m),
    			longList(getGeneratorIndex(ret)));

    	return ret;
    }

    @Override
    protected boolean coin(EventImpl event) {
    	boolean ret = super.coin(event);

    	record(functions.coin,
    			longList(getGeneratorIndex(event)),
    			longList(ret ? 0 : 1));

    	return ret;
    }

    @Override
    protected List<EventImpl> setFamous(EventImpl event, RoundInfo roundInfo, boolean isFamous,
    		RoundInfo.ElectionRound TEMP, int TEMP_voter_id) {
    	List<EventImpl> ret = super.setFamous(event, roundInfo, isFamous, TEMP, TEMP_voter_id);

    	List<Long> results = new LinkedList<>();
    	results.add((long) (event.isFamous() ? 1 : 0));             // 1
    	results.add(roundInfo.getRound());                          // 2
    	results.add(TEMP.getAge());                                 // 3


    	record(functions.setFamous,
    			longList(getGeneratorIndex(event)),
    			results);

    	return ret;
    }
    // */
}
