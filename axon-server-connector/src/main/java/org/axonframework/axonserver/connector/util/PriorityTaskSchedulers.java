/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.util;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Responsible for creating {@link Scheduler} implementations relevant to the Axon Server connector.
 *
 * @author Mitchell Herrijgers
 * @author Milan Savic
 * @since 4.6.0
 */
public interface PriorityTaskSchedulers {

    /**
     * Creates a {@link Scheduler} that is compatible with an {@link ExecutorService} that needs tasks that are
     * submitted to be a {@link org.axonframework.axonserver.connector.PriorityTask} so that they can be prioritized.
     *
     * @param delegate     The delegate {@link ExecutorService} to use when submitting tasks.
     * @param priority     The priority that any tasks submitted to the delegate will have.
     * @param taskSequence The task sequence used for ordering items with the same priority.
     * @return The {@link Scheduler} object that is compatible with
     * {@link org.axonframework.axonserver.connector.PriorityTask}.
     * @see PriorityExecutorService
     */
    static Scheduler forPriority(ExecutorService delegate, long priority, AtomicLong taskSequence) {
        return Schedulers.fromExecutor(new PriorityExecutorService(delegate, priority, taskSequence));
    }
}
