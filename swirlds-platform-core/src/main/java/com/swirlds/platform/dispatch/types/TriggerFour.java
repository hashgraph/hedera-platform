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
package com.swirlds.platform.dispatch.types;

import com.swirlds.platform.dispatch.Trigger;

/**
 * A trigger that accepts four arguments.
 *
 * @param <A> the type of the first argument
 * @param <B> the type of the second argument
 * @param <C> the type of the third argument
 * @param <D> the type of the fourth argument
 */
@FunctionalInterface
public non-sealed interface TriggerFour<A, B, C, D> extends Trigger<TriggerFour<A, B, C, D>> {

    /**
     * Dispatch a trigger event.
     *
     * @param a the first argument
     * @param b the second argument
     * @param c the third argument
     * @param d the fourth argument
     */
    void dispatch(A a, B b, C c, D d);
}
