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
package com.swirlds.platform.state.notifications;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;

/**
 * A {@link com.swirlds.common.notification.Notification Notification} that a new {@link
 * SignedState} is being tracked by the {@link com.swirlds.platform.state.signed.SignedStateManager
 * SignedStateManager}.
 */
public class NewSignedStateBeingTrackedNotification extends AbstractNotification {

    private final SignedState signedState;

    private final SourceOfSignedState sourceOfSignedState;

    // FUTURE WORK:
    //  this field can be removed once PlatformContext maintains a single notification engine per
    // platform instance
    private final NodeId selfId;

    /**
     * Create a notification for state that was signed by this node.
     *
     * @param signedState the state that was just signed by this node
     * @param sourceOfSignedState the location where the signed state was obtained
     * @param selfId the ID of this node
     */
    public NewSignedStateBeingTrackedNotification(
            final SignedState signedState,
            final SourceOfSignedState sourceOfSignedState,
            final NodeId selfId) {

        this.signedState = signedState;
        this.sourceOfSignedState = sourceOfSignedState;
        this.selfId = selfId;
    }

    /** Get the signed state that was just signed by this node. */
    public SignedState getSignedState() {
        return signedState;
    }

    /** Get the source of the signed state that is now being tracked. */
    public SourceOfSignedState getSourceOfSignedState() {
        return sourceOfSignedState;
    }

    /** The ID of this node. */
    public NodeId getSelfId() {
        return selfId;
    }
}
