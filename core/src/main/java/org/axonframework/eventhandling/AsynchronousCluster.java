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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Cluster implementation that publishes events to the subscribed Event Listeners asynchronously from the publishing
 * thread. This implementation can be configured to retry event when processing fails. Furthermore, a SequencingPolicy
 * will tell the cluster which Events need to be processed sequentially, and which may be processed in parallel from
 * others.
 *
 * @author Allard Buijze
 * @see SequencingPolicy
 * @see TransactionManager
 * @since 2.0
 */
public class AsynchronousCluster implements Cluster {

    private final Cluster delegate;
    private final AsynchronousHandler asynchronousHandler;

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failed events are retried if they are not
     * explicitly non-transient with an interval of 2000 millis. Batch size is 50 events.
     *
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     */
    public AsynchronousCluster(Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
        this(executor, transactionManager, sequencingPolicy, 50, RetryPolicy.RETRY_LAST_EVENT, 2000);
    }

    /**
     * Creates an AsynchronousCluster implementation using the given <code>executor</code>,
     * <code>transactionManager</code> and <code>sequencingPolicy</code>. Failure is processed according to the given
     * <code>retryPolicy</code> and <code>retryInterval</code>. Processors will process at most <code>batchSize</code>
     * events in a single batch.
     *
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     * @param batchSize          The number of events to process in a single batch (and transaction)
     * @param retryPolicy        The policy to apply when event handling fails
     * @param retryInterval      The time (in milliseconds) to wait between retries
     */
    public AsynchronousCluster(Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                               int batchSize, RetryPolicy retryPolicy, int retryInterval) {
        this(executor, transactionManager, sequencingPolicy, new SimpleCluster(),
             retryPolicy, batchSize, retryInterval);
    }

    /**
     * Creates an AsynchronousCluster implementation that delegates handling to the given <code>delegate</code>,
     * using the given <code>executor</code>, <code>transactionManager</code> and <code>sequencingPolicy</code>.
     * Failure is processed according to the given <code>retryPolicy</code> and <code>retryInterval</code>. Processors
     * will process at most <code>batchSize</code> events in a single batch.
     *
     * @param executor           The executor to process event batches with
     * @param transactionManager The TransactionManager that manages transactions around event processing batches
     * @param sequencingPolicy   The policy indicating which events must be processed sequentially, and which may be
     *                           executed in parallel.
     * @param batchSize          The number of events to process in a single batch (and transaction)
     * @param delegate           The cluster implementation to delegate processing to
     * @param retryPolicy        The policy to apply when event handling fails
     * @param retryInterval      The time (in milliseconds) to wait between retries
     */
    public AsynchronousCluster(Executor executor, TransactionManager transactionManager,
                               SequencingPolicy<? super EventMessage<?>> sequencingPolicy, Cluster delegate,
                               RetryPolicy retryPolicy, int batchSize, int retryInterval) {
        asynchronousHandler = new AsynchronousHandler(delegate, executor, transactionManager, sequencingPolicy,
                                                      retryPolicy, batchSize, retryInterval);
        this.delegate = delegate;
    }

    @Override
    public void publish(EventMessage... events) {
        for (EventMessage event : events) {
            asynchronousHandler.schedule(event);
        }
    }

    @Override
    public void subscribe(EventListener eventListener) {
        delegate.subscribe(eventListener);
    }

    @Override
    public void unsubscribe(EventListener eventListener) {
        delegate.unsubscribe(eventListener);
    }

    @Override
    public Set<EventListener> getMembers() {
        return delegate.getMembers();
    }

    @Override
    public ClusterMetaData getMetaData() {
        return delegate.getMetaData();
    }

    private static final class AsynchronousHandler extends AsynchronousExecutionWrapper<EventMessage<?>> {

        private final Cluster delegate;

        public AsynchronousHandler(Cluster delegate, Executor executor, TransactionManager transactionManager,
                                   SequencingPolicy<? super EventMessage<?>> sequencingPolicy, RetryPolicy retryPolicy,
                                   int batchSize, int retryInterval) {
            super(executor, transactionManager, sequencingPolicy, retryPolicy, batchSize, retryInterval);
            this.delegate = delegate;
        }

        @Override
        protected void doHandle(EventMessage<?> message) {
            delegate.publish(message);
        }
    }
}
