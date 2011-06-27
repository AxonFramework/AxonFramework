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

import org.axonframework.eventhandling.AsynchronousExecutionWrapper;
import org.axonframework.eventhandling.SequentialPolicy;
import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.eventhandling.TransactionStatus;

import java.util.concurrent.Executor;

/**
 * Executor that processes saga lookups and saga event handling using an {@link Executor}. This allows processing of
 * events by sagas to happen fully asynchronously from the event dispatching mechanism.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class AsynchronousSagaExecutor
        extends AsynchronousExecutionWrapper<Runnable> implements SagaHandlerExecutor {


    /**
     * Initializes an AsynchronousSagaExecutor using the given <code>executor</code> and
     * <code>transactionManager</code>. Note that the transaction manager is invoked for both the saga resolution
     * process as well as the saga invocation process.
     *
     * @param transactionManager The transaction manager that will manage underlying transactions
     * @param executor           The executor that processes the tasks
     */
    public AsynchronousSagaExecutor(Executor executor, TransactionManager transactionManager) {
        super(executor, new OneEventPerTransaction(transactionManager), new SequentialPolicy());
    }

    @Override
    public void scheduleLookupTask(Runnable task) {
        schedule(task);
    }

    @Override
    public void scheduleEventProcessingTask(Saga saga, Runnable task) {
        // it is unsafe to schedule Sagas completely concurrently, as association values might not be processed while
        // new events are arriving. To provide necessary ordering guarantees, all activity runs in a single thread.
        task.run();
    }

    @Override
    protected void doHandle(Runnable task) {
        task.run();
    }

    private static class OneEventPerTransaction implements TransactionManager {
        private final TransactionManager delegate;

        public OneEventPerTransaction(
                TransactionManager transactionManager) {
            delegate = transactionManager;
        }

        @Override
        public void beforeTransaction(TransactionStatus transactionStatus) {
            delegate.beforeTransaction(transactionStatus);
            transactionStatus.setMaxTransactionSize(1);
        }

        @Override
        public void afterTransaction(TransactionStatus transactionStatus) {
            delegate.afterTransaction(transactionStatus);
        }
    }
}
