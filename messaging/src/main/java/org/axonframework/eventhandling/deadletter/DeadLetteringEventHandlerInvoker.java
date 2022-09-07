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
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of an {@link EventHandlerInvoker} utilizing a {@link SequencedDeadLetterQueue} to enqueue
 * {@link EventMessage events} for which handling failed.
 * <p>
 * Will use an {@link EnqueuePolicy} to deduce whether failed event handling should result in an
 * {@link SequencedDeadLetterQueue#enqueue(Object, DeadLetter) enqueue operation}. Subsequent events belonging to an
 * already contained "sequence identifier", according to the {@link SequencingPolicy}, are also enqueued. This ensures
 * event ordering is maintained in face of failures.
 * <p>
 * This dead lettering invoker provides several operations to {@link #processAny() process}
 * {@link DeadLetter dead letters} it has enqueued. It will ensure the same set of Event Handling Components is invoked
 * as with regular event handling when processing a dead letter. These methods will try to process an entire sequence of
 * dead letters. Furthermore, these are exposed through the {@link SequencedDeadLetterProcessor} contract.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetteringEventHandlerInvoker
        extends SimpleEventHandlerInvoker
        implements SequencedDeadLetterProcessor<EventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final SequencedDeadLetterQueue<EventMessage<?>> queue;
    private final EnqueuePolicy<EventMessage<?>> enqueuePolicy;
    private final TransactionManager transactionManager;
    private final boolean allowReset;

    /**
     * Instantiate a dead-lettering {@link EventHandlerInvoker} based on the given {@link Builder builder}. Uses a
     * {@link SequencedDeadLetterQueue} to maintain and retrieve dead letters from.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetteringEventHandlerInvoker} instance.
     */
    protected DeadLetteringEventHandlerInvoker(Builder builder) {
        super(builder);
        this.queue = builder.queue;
        this.enqueuePolicy = builder.enqueuePolicy;
        this.transactionManager = builder.transactionManager;
        this.allowReset = builder.allowReset;
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} when invoked, the
     * {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, and {@code allowReset} defaults to
     * {@code false}. Providing at least one Event Handler, a {@link SequencedDeadLetterQueue}, and a
     * {@link TransactionManager} are <b>hard requirements</b> and as such should be provided.
     *
     * @return A builder that can construct a {@link DeadLetteringEventHandlerInvoker}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void handle(@Nonnull EventMessage<?> message, @Nonnull Segment segment) throws Exception {
        if (!super.sequencingPolicyMatchesSegment(message, segment)) {
            logger.trace("Ignoring event [{}] as it is not assigned to segment [{}].", message, segment);
            return;
        }

        Object sequenceIdentifier = super.sequenceIdentifier(message);
        if (queue.enqueueIfPresent(sequenceIdentifier, () -> new GenericDeadLetter<>(sequenceIdentifier, message))) {
            if (logger.isInfoEnabled()) {
                logger.info("Event [{}] is added to the dead-letter queue since its queue id [{}] is already present.",
                            message, sequenceIdentifier);
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Event [{}] with queue id [{}] is not present in the dead-letter queue."
                                     + "Handle operation is delegated to the wrapped EventHandlerInvoker.",
                             message, sequenceIdentifier);
            }
            try {
                super.invokeHandlers(message);
            } catch (Exception e) {
                DeadLetter<EventMessage<?>> letter = new GenericDeadLetter<>(sequenceIdentifier, message, e);
                EnqueueDecision<EventMessage<?>> decision = enqueuePolicy.decide(letter, e);
                if (decision.shouldEnqueue()) {
                    queue.enqueue(sequenceIdentifier, decision.withDiagnostics(letter));
                } else if (logger.isInfoEnabled()) {
                    logger.info("The enqueue policy decided not to dead letter event [{}].", message.getIdentifier());
                }
            }
        }
    }

    @Override
    public void performReset() {
        if (allowReset) {
            transactionManager.executeInTransaction(queue::clear);
        }
        super.performReset(null);
    }

    @Override
    public <R> void performReset(R resetContext) {
        if (allowReset) {
            transactionManager.executeInTransaction(queue::clear);
        }
        super.performReset(resetContext);
    }

    @Override
    public boolean process(Predicate<DeadLetter<? extends EventMessage<?>>> sequenceFilter) {
        DeadLetteredEventProcessingTask processingTask =
                new DeadLetteredEventProcessingTask(super.eventHandlers(), enqueuePolicy, transactionManager);

        UnitOfWork<?> uow = new DefaultUnitOfWork<>(null);
        uow.attachTransaction(transactionManager);
        return uow.executeWithResult(() -> queue.process(sequenceFilter, processingTask::process)).getPayload();
    }

    /**
     * Builder class to instantiate a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} when invoked, the
     * {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, and {@code allowReset} defaults to
     * {@code false}. Providing at least one Event Handler, a {@link SequencedDeadLetterQueue}, and a
     * {@link TransactionManager} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends SimpleEventHandlerInvoker.Builder<Builder> {

        private SequencedDeadLetterQueue<EventMessage<?>> queue;
        private EnqueuePolicy<EventMessage<?>> enqueuePolicy = (letter, cause) -> Decisions.enqueue(cause);
        private TransactionManager transactionManager;
        private boolean allowReset = false;

        private Builder() {
            // The parent's error handler defaults to propagating the error.
            // Otherwise, faulty events would not be dead lettered.
            super.listenerInvocationErrorHandler(PropagatingErrorHandler.instance());
        }

        /**
         * Sets the {@link SequencedDeadLetterQueue} this {@link EventHandlerInvoker} maintains dead letters with.
         *
         * @param queue The {@link SequencedDeadLetterQueue} this {@link EventHandlerInvoker} maintains dead letters
         *              with.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder queue(@Nonnull SequencedDeadLetterQueue<EventMessage<?>> queue) {
            assertNonNull(queue, "The DeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the {@link EnqueuePolicy} this {@link EventHandlerInvoker} uses to decide whether a
         * {@link DeadLetter dead letter} should be added to the {@link SequencedDeadLetterQueue}. Defaults to returning
         * {@link Decisions#enqueue(Throwable)} when invoked for any dead letter.
         *
         * @param enqueuePolicy The {@link EnqueuePolicy} this {@link EventHandlerInvoker} uses to decide whether a
         *                      {@link DeadLetter dead letter} should be added to the {@link SequencedDeadLetterQueue}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder enqueuePolicy(EnqueuePolicy<EventMessage<?>> enqueuePolicy) {
            assertNonNull(enqueuePolicy, "The EnqueuePolicy should be non null");
            this.enqueuePolicy = enqueuePolicy;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used by this invoker for <b>all</b> operations involving the configured
         * {@link SequencedDeadLetterQueue}.
         *
         * @param transactionManager The {@link TransactionManager} used by this invoker for <b>all</b> operations
         *                           involving the configured {@link SequencedDeadLetterQueue}
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder transactionManager(@Nonnull TransactionManager transactionManager) {
            assertNonNull(transactionManager, "The TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets whether this {@link DeadLetteringEventHandlerInvoker} supports resets of the provided
         * {@link SequencedDeadLetterQueue}. If set to {@code true}, {@link SequencedDeadLetterQueue#clear()} will be
         * invoked upon a {@link #performReset()}/{@link #performReset(Object)} invocation. Defaults to {@code false}.
         *
         * @param allowReset A toggle dictating whether this {@link DeadLetteringEventHandlerInvoker} supports resets of
         *                   the provided {@link SequencedDeadLetterQueue}.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder allowReset(boolean allowReset) {
            this.allowReset = allowReset;
            return this;
        }

        /**
         * Initializes a {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         *
         * @return A {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         */
        @Override
        public DeadLetteringEventHandlerInvoker build() {
            return new DeadLetteringEventHandlerInvoker(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        @Override
        protected void validate() {
            assertNonNull(queue, "The DeadLetterQueue is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
