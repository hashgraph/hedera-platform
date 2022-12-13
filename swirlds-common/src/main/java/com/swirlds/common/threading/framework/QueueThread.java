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
package com.swirlds.common.threading.framework;

import com.swirlds.common.utility.Clearable;
import java.util.concurrent.BlockingQueue;

/**
 * A thread that continuously takes elements from a queue and handles them.
 *
 * @param <T> the type of the item in the queue
 */
public interface QueueThread<T> extends StoppableThread, BlockingQueue<T>, Clearable {}
