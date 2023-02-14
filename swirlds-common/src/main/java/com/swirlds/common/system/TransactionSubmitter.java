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
package com.swirlds.common.system;

import java.time.Instant;

/** An object that can be used to submit transactions. */
public interface TransactionSubmitter {

    /**
     * The SwirldMain object calls this method when it wants to create a new transaction. The
     * newly-created transaction is then embedded inside a newly-created event, and sent to all the
     * other members of the community during syncs. It is also sent to the swirldState object.
     *
     * <p>If transactions are being created faster than they can be handled, then eventually a large
     * backlog will build up. At that point, a call to createTransaction will return false, and will
     * not actually create a transaction.
     *
     * <p>A transaction can be at most 1024 bytes. If trans.length &gt; 1024, then this will return
     * false, and will not actually create a transaction.
     *
     * @param trans the transaction to handle, encoded any way the swirld author chooses
     * @return true if successful
     */
    boolean createTransaction(byte[] trans);

    /**
     * Find a rough estimate of what consensus timestamp a transaction would eventually have, if it
     * were created right now through a call to createTransaction().
     *
     * <p>A real-time app, such as a game, will typically redraw the screen by first calling
     * estTime(), then rendering everything to the screen reflecting the predicted state as it will
     * be at this time.
     *
     * @return the estimated time
     */
    Instant estimateTime();
}
