/*
 * Copyright (c) 2010-2012. Axon Framework
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
 * Execution wrapper that executes saga related tasks (lookup and invocation) in the thread that schedules these tasks.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class SynchronousSagaExecutionWrapper implements SagaHandlerExecutor {

    @Override
    public void scheduleLookupTask(Runnable task) {
        task.run();
    }

    @Override
    public void scheduleEventProcessingTask(Saga saga, Runnable task) {
        task.run();
    }
}
