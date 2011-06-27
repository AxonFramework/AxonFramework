/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga;

/**
 * Interface describing the tasks needed to handle events by sagas. Implementations may choose to perform these tasks
 * synchronously or asynchronously.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public interface SagaHandlerExecutor {

    /**
     * Schedules the given <code>task</code> for execution. Lookup tasks are always executed fully sequential on a
     * first-in-first-out basis.
     *
     * @param task The task to schedule
     */
    void scheduleLookupTask(Runnable task);

    /**
     * Schedules the given <code>task</code> for execution. Lookup tasks are always executed sequentially per saga.
     * Hence, two tasks provided (in the same thread) with the same saga identifier are guaranteed to be executed
     * sequentially.
     * <p/>
     * If the tasks are provided by different threads, implementation is <em>recommended</em> to execute them
     * sequentially.
     *
     * @param saga The saga to execute this task for
     * @param task The task to execute
     */
    void scheduleEventProcessingTask(Saga saga, Runnable task);
}
