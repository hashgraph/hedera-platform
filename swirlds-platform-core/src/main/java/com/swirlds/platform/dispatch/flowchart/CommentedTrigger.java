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
package com.swirlds.platform.dispatch.flowchart;

/**
 * A trigger and a comment about how the trigger is being used.
 *
 * @param trigger the trigger
 * @param comment the comment on how the trigger is being used, or null if there is no comment
 */
public record CommentedTrigger(Class<?> trigger, String comment) {

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null || obj.getClass() != CommentedTrigger.class) {
            return false;
        }
        return trigger == ((CommentedTrigger) obj).trigger;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return trigger.hashCode();
    }
}
