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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.axonframework.messaging.deadletter.QueueIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link SimpleEventHandlerInvoker} utilizing a {@link DeadLetterQueue} to enqueue
 * {@link EventMessage events} for which handling failed.
 * <p>
 * This dead-lettering {@link EventHandlerInvoker} takes into account that events part of the same sequence (as
 * according to the {@link org.axonframework.eventhandling.async.SequencingPolicy}) should be enqueued in order.
 * <p>
 * Upon starting this invoker, it registers a dead-letter evaluation task with the queue's
 * {@link DeadLetterQueue#onAvailable(String, Runnable)}. Doing so ensures that whenever letters are ready to be
 * retried, they are provided to the {@link EventMessageHandler event handlers} this invoker is in charge of.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetteringEventHandlerInvoker extends SimpleEventHandlerInvoker implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DeadLetterQueue<EventMessage<?>> queue;
    private final String processingGroup;
    private final TransactionManager transactionManager;
    private final boolean allowReset;
    private final ListenerInvocationErrorHandler listenerInvocationErrorHandler;

    /**
     * Instantiate a dead-lettering {@link EventHandlerInvoker} based on the given {@link Builder builder}. Uses a
     * {@link DeadLetterQueue} to maintain and retrieve dead-letters from.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetteringEventHandlerInvoker} instance.
     */
    protected DeadLetteringEventHandlerInvoker(Builder builder) {
        super(builder);
        this.queue = builder.queue;
        this.processingGroup = builder.processingGroup;
        this.transactionManager = builder.transactionManager;
        this.allowReset = builder.allowReset;
        this.listenerInvocationErrorHandler = builder.listenerInvocationErrorHandler;
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, and {@code allowReset} defaults to
     * {@code false}. Providing at least one Event Handler, a {@link DeadLetterQueue}, a {@code processingGroup} name,
     * and a {@link TransactionManager} are <b>hard requirements</b> and as such should be provided.
     *
     * @return A builder that can construct a {@link DeadLetteringEventHandlerInvoker}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void handle(@Nonnull EventMessage<?> message, @Nonnull Segment segment) throws Exception {
        if (!super.sequencingPolicyMatchesSegment(message, segment)) {
            logger.trace("Ignoring event [{}] as it is not meant for segment [{}].", message, segment);
            return;
        }

        EventHandlingQueueIdentifier identifier =
                new EventHandlingQueueIdentifier(super.sequenceIdentifier(message), processingGroup);
        Optional<DeadLetterEntry<EventMessage<?>>> optionalLetter =
                transactionManager.fetchInTransaction(() -> queue.enqueueIfPresent(identifier, message));
        if (optionalLetter.isPresent()) {
            logger.info("Event [{}] is added to the dead-letter queue since its queue id [{}] is already present.",
                        message, identifier.combinedIdentifier());
        } else {
            logger.trace("Event [{}] with queue id [{}] is not present in the dead-letter queue present."
                                 + "Handle operation is delegated to the wrapped EventHandlerInvoker.",
                         message, identifier.combinedIdentifier());
            try {
                super.invokeHandlers(message);
            } catch (Exception e) {
                transactionManager.executeInTransaction(() -> queue.enqueue(identifier, message, e));
            }
        }
    }

    @Override
    public void performReset() {
        if (allowReset) {
            transactionManager.executeInTransaction(() -> queue.clear(processingGroup));
        }
        super.performReset(null);
    }

    @Override
    public <R> void performReset(R resetContext) {
        if (allowReset) {
            transactionManager.executeInTransaction(() -> queue.clear(processingGroup));
        }
        super.performReset(resetContext);
    }

    @Override
    public void registerLifecycleHandlers(LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INBOUND_EVENT_CONNECTORS + 1, this::start);
    }

    /**
     * Registers a dead-letter evaluation task with the {@link DeadLetterQueue queue's}
     * {@link DeadLetterQueue#onAvailable(String, Runnable)} operation. After registration the letters within the queue
     * are {@link DeadLetterQueue#release(Predicate) released} for this invoker's processing group right away to
     * evaluate remaining entries. This lifecycle handler is registered right after the
     * {@link Phase#INBOUND_EVENT_CONNECTORS} phase.
     */
    public void start() {
        EvaluationTask evaluationTask = new EvaluationTask(super.eventHandlers(),
                                                           queue,
                                                           processingGroup,
                                                           transactionManager,
                                                           listenerInvocationErrorHandler);
        queue.onAvailable(processingGroup, evaluationTask);
        queue.release(entry -> Objects.equals(entry.queueIdentifier().group(), processingGroup));
    }

    /**
     * Builder class to instantiate a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, and {@code allowReset} defaults to
     * {@code false}. Providing at least one Event Handler, a {@link DeadLetterQueue}, a {@code processingGroup} name,
     * and a {@link TransactionManager} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends SimpleEventHandlerInvoker.Builder<Builder> {

        private DeadLetterQueue<EventMessage<?>> queue;
        private String processingGroup;
        private TransactionManager transactionManager;
        private boolean allowReset = false;
        private ListenerInvocationErrorHandler listenerInvocationErrorHandler = PropagatingErrorHandler.instance();

        private Builder() {
            // The parent's error handler defaults to propagating the error.
            // Otherwise, faulty events would not be dead-lettered.
            super.listenerInvocationErrorHandler(PropagatingErrorHandler.instance());
        }

        /**
         * Sets the {@link DeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         *
         * @param queue The {@link DeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder queue(@Nonnull DeadLetterQueue<EventMessage<?>> queue) {
            assertNonNull(queue, "The DeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the processing group name of this invoker.
         *
         * @param processingGroup The processing group name of this {@link EventHandlerInvoker}.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder processingGroup(@Nonnull String processingGroup) {
            assertNonEmpty(processingGroup, "The processing group may not be null or empty");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used by this invoker for <b>all</b> operations involving the configured
         * {@link DeadLetterQueue}.
         *
         * @param transactionManager The {@link TransactionManager} used by this invoker for <b>all</b> operations
         *                           involving the configured {@link DeadLetterQueue}
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder transactionManager(@Nonnull TransactionManager transactionManager) {
            assertNonNull(transactionManager, "The TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets whether this {@link DeadLetteringEventHandlerInvoker} supports resets of the provided
         * {@link DeadLetterQueue}. If set to {@code true}, {@link DeadLetterQueue#clear(String)} will be invoked upon a
         * {@link #performReset()}/{@link #performReset(Object)} invocation. Defaults to {@code false}.
         *
         * @param allowReset A toggle dictating whether this {@link DeadLetteringEventHandlerInvoker} supports resets of
         *                   the provided {@link DeadLetterQueue}.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder allowReset(boolean allowReset) {
            this.allowReset = allowReset;
            return this;
        }

        /**
         * Sets the {@link ListenerInvocationErrorHandler} dealing with {@link Exception exceptions} thrown by the
         * configured {@link EventMessageHandler event handlers} during <emn>dead-letter evaluation</emn>.
         * {@code Exceptions} thrown during regular event handling are
         * {@link DeadLetterQueue#enqueue(QueueIdentifier, Message, Throwable) enqueued} at all times. Defaults to a
         * {@link PropagatingErrorHandler} to ensure failed evaluation requeue a dead-letter.
         *
         * @param listenerInvocationErrorHandler The error handler which deals with any {@link Exception exceptions}
         *                                       thrown by the configured {@link EventMessageHandler event handlers}
         *                                       during dead-letter evaluation.
         * @return The current Builder instance for fluent interfacing.
         */
        @Override
        public Builder listenerInvocationErrorHandler(
                @Nonnull ListenerInvocationErrorHandler listenerInvocationErrorHandler
        ) {
            assertNonNull(listenerInvocationErrorHandler, "The ListenerInvocationErrorHandler may not be null");
            this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
            return this;
        }

        /**
         * Initializes a {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         *
         * @return A {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         */
        public DeadLetteringEventHandlerInvoker build() {
            return new DeadLetteringEventHandlerInvoker(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(queue, "The DeadLetterQueue is a hard requirement and should be provided");
            assertNonEmpty(processingGroup, "The processing group is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
