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
package com.swirlds.platform.gui.internal;

import java.util.ArrayList;
import java.util.List;

/** Metadata about a swirld running on an app. */
public class InfoSwirld extends InfoEntity {
    public InfoApp app; // parent
    List<InfoMember> members = new ArrayList<InfoMember>(); // children

    Reference swirldId;

    public InfoSwirld(InfoApp app, byte[] swirldIdBytes) {
        this.app = app;
        this.swirldId = new Reference(swirldIdBytes);
        name = "Swirld " + swirldId.to62Prefix();
        app.swirlds.add(this);
    }
}
