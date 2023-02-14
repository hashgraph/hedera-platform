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
package com.swirlds.cli.utility;

import java.util.concurrent.Callable;

/** Contains boilerplate for commands. */
public abstract class AbstractCommand extends ParameterizedClass implements Callable<Integer> {

    /** A default call method. Only needs to be overridden by commands with no subcommands. */
    @Override
    public Integer call() throws Exception {
        throw buildParameterException("no subcommand provided");
    }
}
