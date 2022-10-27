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

package com.swirlds.platform.state.notifications;

import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchModel;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;

/**
 * A method that listens for when a new {@link com.swirlds.platform.state.signed.SignedState} is being tracked by
 * the {@link com.swirlds.platform.state.signed.SignedStateManager SignedStateManager}. State is guaranteed to hold
 * a strong reservation until the end of the callback.
 */
@DispatchModel(mode = DispatchMode.SYNC, order = DispatchOrder.UNORDERED)
public interface NewSignedStateBeingTrackedListener extends Listener<NewSignedStateBeingTrackedNotification> {

}
