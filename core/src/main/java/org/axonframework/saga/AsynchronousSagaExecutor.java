/*
 * Copyright (c) 2011. Axon Framework
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

import org.axonframework.eventhandling.AsynchronousExecutionWrapper;
import org.axonframework.eventhandling.SequencingPolicy;
import org.axonframework.eventhandling.TransactionManager;

import java.util.concurrent.Executor;

/**
 * Executor that processes saga lookups and saga event handling using an {@link Executor}. This allows processing of
 * events by sagas to happen fully asynchronously from the event dispatching mechanism.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class AsynchronousSagaExecutor
        extends AsynchronousExecutionWrapper<AsynchronousSagaExecutor.Task> implements SagaHandlerExecutor {

    private static final Object SEQUENCE_ID = new Object();

    /**
     * Initializes an AsynchronousSagaExecutor using the given <code>executor</code> and
     * <code>transactionManager</code>. Note that the transaction manager is invoked for both the saga resolution
     * process as well as the saga invocation process.
     *
     * @param transactionManager The transaction manager that will manage underlying transactions
     * @param executor           The executor that processes the tasks
     */
    public AsynchronousSagaExecutor(Executor executor, TransactionManager transactionManager) {
        super(executor, transactionManager, new SagaSequencingPolicy());
    }

    @Override
    public void scheduleLookupTask(Runnable task) {
        schedule(new Task(task, SEQUENCE_ID));
    }

    @Override
    public void scheduleEventProcessingTask(String sagaIdentifier, Runnable task) {
        schedule(new Task(task, sagaIdentifier));
    }

    @Override
    protected void doHandle(Task task) {
        task.execute();
    }

    private static class SagaSequencingPolicy implements SequencingPolicy<Task> {
        @Override
        public Object getSequenceIdentifierFor(Task task) {
            return task.getSequenceId();
        }
    }

    /**
     * Object used internally to represent a task that needs to be scheduled sequentially to some other tasks.
     */
    public static class Task {
        private final Runnable runnable;
        private final Object sequenceId;

        /**
         * Initializes a task defined by the given <code>runnable</code> and sequenced using the given
         * <code>sequenceId</code>.
         *
         * @param runnable   The runnable defining the task
         * @param sequenceId The sequence identifier
         */
        public Task(Runnable runnable, Object sequenceId) {
            this.runnable = runnable;
            this.sequenceId = sequenceId;
        }

        /**
         * Executes the given runnable.
         */
        public void execute() {
            runnable.run();
        }

        /**
         * Returns the sequence identifier of this task.
         *
         * @return the sequence identifier of this task
         */
        public Object getSequenceId() {
            return sequenceId;
        }
    }
}
