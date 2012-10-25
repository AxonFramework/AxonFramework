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

package org.axonframework.eventhandling.async;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.AbstractCluster;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.unitofwork.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Cluster implementation that publishes events to the subscribed Event Listeners asynchronously from the publishing
 * thread. This implementation can be configured to retry event when processing fails. Furthermore, a SequencingPolicy
 * will tell the cluster which Events need to be processed sequentially, and which may be processed in parallel from
 * others.
 *
 * @author Allard Buijze
 * @see SequencingPolicy
 * @see org.axonframework.unitofwork.TransactionManager
 * @since 2.0
 */
public class AsynchronousCluster extends AbstractCluster {

    private static final Logger logger = LoggerFactory.getLogger(AsynchronousCluster.class);
    private final AsynchronousHandler asynchronousHandler;

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failed events are retried if they are not
     * explicitly non-transient with an interval of 2000 millis. Batch size is 50 events.
     *
     * @param identifier         The unique identifier of this cluster
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     */
    public AsynchronousCluster(String identifier, Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this(identifier, executor, transactionManager, sequencingPolicy, 50, RetryPolicy.RETRY_LAST_EVENT, 2000);
    }

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failure is processed according to the given
     * <code>retryPolicy</code> and <code>retryInterval</code>. Processors will process at most <code>batchSize</code>
     * events in a single batch.
     *
     * @param identifier         The unique identifier of this cluster
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     * @param batchSize          The number of events to process in a single batch (and transaction)
     * @param retryPolicy        The policy to apply when event handling fails
     * @param retryInterval      The time (in milliseconds) to wait between retries
     */
    public AsynchronousCluster(String identifier, Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                               int batchSize, RetryPolicy retryPolicy, int retryInterval) {
        super(identifier);
        asynchronousHandler = new AsynchronousHandler(executor, transactionManager, sequencingPolicy,
                                                      retryPolicy, batchSize, retryInterval);
    }

    @Override
    public void publish(EventMessage... events) {
        for (EventMessage event : events) {
            asynchronousHandler.schedule(event);
        }
    }

    /**
     * Performs the actual publication of the message to each of the event handlers. Exceptions thrown by event
     * listeners are caught and are rethrown only after each of the handlers has handled it. In case multiple event
     * listeners throw an exception, the first exception caught is rethrown.
     *
     * @param message The message to publish to each of the handlers
     */
    protected void doPublish(EventMessage<?> message) {
        RuntimeException firstException = null;
        for (EventListener member : getMembers()) {
            try {
                member.handle(message);
            } catch (RuntimeException e) {
                if (firstException == null) {
                    firstException = e;
                } else if (!firstException.getClass().equals(e.getClass())) {
                    logger.warn("Exception thrown by an Event Listener is suppressed, "
                                        + "as it is not the first event thrown.", e);
                } else {
                    logger.info("An exception thrown by an Event Listener is suppressed, as it is not the first event "
                                        + "thrown, but is of the same type.");
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    private final class AsynchronousHandler extends AsynchronousExecutionWrapper<EventMessage<?>> {

        public AsynchronousHandler(Executor executor, TransactionManager transactionManager,
                                   SequencingPolicy<? super EventMessage<?>> sequencingPolicy, RetryPolicy retryPolicy,
                                   int batchSize, int retryInterval) {
            super(executor, transactionManager, sequencingPolicy, retryPolicy, batchSize, retryInterval);
        }

        @Override
        protected void doHandle(EventMessage<?> message) {
            doPublish(message);
        }
    }
}
