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
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequenceIdentifier;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of an {@link EventHandlerInvoker} utilizing a {@link SequencedDeadLetterQueue} to enqueue
 * {@link EventMessage events} for which handling failed.
 * <p>
 * Will use an {@link EnqueuePolicy} to deduce whether failed event handling should result in an
 * {@link SequencedDeadLetterQueue#enqueue(DeadLetter) enqueue operation}. Subsequent events belonging to an already
 * contained {@link SequenceIdentifier}, according to the
 * {@link org.axonframework.eventhandling.async.SequencingPolicy}, are also enqueued. This ensures event ordering is
 * maintained in face of failures.
 * <p>
 * This dead lettering invoker provides several operations to {@link #processAny()} process}
 * {@link DeadLetter dead-letters} it has enqueued. It will ensure the same set of Event Handling Components is invoked
 * as with regular event handling when processing a dead-letter. These methods will try to process an entire sequence of
 * dead-letters. Furthermore, these are exposed through the {@link SequencedDeadLetterProcessor} contract.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetteringEventHandlerInvoker
        extends SimpleEventHandlerInvoker
        implements SequencedDeadLetterProcessor<EventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String processingGroup;
    private final SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> queue;
    private final EnqueuePolicy<DeadLetter<EventMessage<?>>> enqueuePolicy;
    private final TransactionManager transactionManager;
    private final boolean allowReset;
    private final ListenerInvocationErrorHandler listenerInvocationErrorHandler;

    /**
     * Instantiate a dead-lettering {@link EventHandlerInvoker} based on the given {@link Builder builder}. Uses a
     * {@link SequencedDeadLetterQueue} to maintain and retrieve dead-letters from.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetteringEventHandlerInvoker} instance.
     */
    protected DeadLetteringEventHandlerInvoker(Builder builder) {
        super(builder);
        this.queue = builder.queue;
        this.enqueuePolicy = builder.enqueuePolicy;
        this.processingGroup = builder.processingGroup;
        this.transactionManager = builder.transactionManager;
        this.allowReset = builder.allowReset;
        this.listenerInvocationErrorHandler = builder.listenerInvocationErrorHandler;
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} when invoked, the
     * {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, and {@code allowReset} defaults to
     * {@code false}. Providing at least one Event Handler, a {@link SequencedDeadLetterQueue}, a
     * {@code processingGroup} name, and a {@link TransactionManager} are <b>hard requirements</b> and as such should be
     * provided.
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

        EventSequenceIdentifier id = new EventSequenceIdentifier(super.sequenceIdentifier(message), processingGroup);
        boolean enqueued = transactionManager.fetchInTransaction(
                () -> queue.enqueueIfPresent(id, queueId -> new GenericDeadLetter<>(queueId, message))
        );
        if (enqueued) {
            if (logger.isInfoEnabled()) {
                logger.info("Event [{}] is added to the dead-letter queue since its queue id [{}] is already present.",
                            message, id.combinedIdentifier());
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Event [{}] with queue id [{}] is not present in the dead-letter queue."
                                     + "Handle operation is delegated to the wrapped EventHandlerInvoker.",
                             message, id.combinedIdentifier());
            }
            try {
                super.invokeHandlers(message);
            } catch (Exception e) {
                DeadLetter<EventMessage<?>> letter = new GenericDeadLetter<>(id, message, e);
                EnqueueDecision<DeadLetter<EventMessage<?>>> decision = enqueuePolicy.decide(letter, e);
                if (decision.shouldEnqueue()) {
                    transactionManager.executeInTransaction(() -> queue.enqueue(decision.addDiagnostics(letter)));
                } else if (logger.isInfoEnabled()) {
                    logger.info("The enqueue policy decided not to dead-letter event [{}].", message.getIdentifier());
                }
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
    public String group() {
        return processingGroup;
    }

    @Override
    public boolean process(Predicate<SequenceIdentifier> sequenceFilter,
                           Predicate<DeadLetter<EventMessage<?>>> letterFilter) {
        AtomicReference<SequenceIdentifier> processedSequence = new AtomicReference<>();
        DeadLetteredEventProcessingTask processingTask = new DeadLetteredEventProcessingTask(
                super.eventHandlers(), enqueuePolicy, transactionManager, listenerInvocationErrorHandler
        );

        boolean firstInvocation = process(sequenceFilter, letterFilter, letter -> {
            processedSequence.set(letter.sequenceIdentifier());
            return processingTask.process(letter);
        });

        if (firstInvocation) {
            boolean sequencedInvocation;
            Predicate<SequenceIdentifier> sequencePredicate = id -> Objects.equals(id, processedSequence.get());
            do {
                sequencedInvocation = process(sequencePredicate, letter -> true, processingTask::process);
            } while (sequencedInvocation);
        }
        return firstInvocation;
    }

    private boolean process(Predicate<SequenceIdentifier> sequenceFilter,
                            Predicate<DeadLetter<EventMessage<?>>> letterFilter,
                            Function<DeadLetter<EventMessage<?>>, EnqueueDecision<DeadLetter<EventMessage<?>>>> processingTask) {
        UnitOfWork<?> uow = new DefaultUnitOfWork<>(null);
        uow.attachTransaction(transactionManager);
        return uow.executeWithResult(() -> queue.process(sequenceFilter, letterFilter, processingTask)).getPayload();
    }

    /**
     * Builder class to instantiate a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} when invoked, the
     * {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, and {@code allowReset} defaults to
     * {@code false}. Providing at least one Event Handler, a {@link SequencedDeadLetterQueue}, a
     * {@code processingGroup} name, and a {@link TransactionManager} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder extends SimpleEventHandlerInvoker.Builder<Builder> {

        private String processingGroup;
        private SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> queue;
        private EnqueuePolicy<DeadLetter<EventMessage<?>>> enqueuePolicy = (letter, cause) -> Decisions.enqueue(cause);
        private TransactionManager transactionManager;
        private boolean allowReset = false;
        private ListenerInvocationErrorHandler listenerInvocationErrorHandler = PropagatingErrorHandler.instance();

        private Builder() {
            // The parent's error handler defaults to propagating the error.
            // Otherwise, faulty events would not be dead-lettered.
            super.listenerInvocationErrorHandler(PropagatingErrorHandler.instance());
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
         * Sets the {@link SequencedDeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         *
         * @param queue The {@link SequencedDeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters
         *              with.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder queue(@Nonnull SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> queue) {
            assertNonNull(queue, "The DeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the {@link EnqueuePolicy} this {@link EventHandlerInvoker} uses to decide whether a
         * {@link DeadLetter dead-letter} should be added to the {@link SequencedDeadLetterQueue}. Defaults to returning
         * {@link Decisions#enqueue(Throwable)} when invoked for any dead-letter.
         *
         * @param enqueuePolicy The {@link EnqueuePolicy} this {@link EventHandlerInvoker} uses to decide whether a
         *                      {@link DeadLetter dead-letter} should be added to the {@link SequencedDeadLetterQueue}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder enqueuePolicy(EnqueuePolicy<DeadLetter<EventMessage<?>>> enqueuePolicy) {
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
         * {@link SequencedDeadLetterQueue}. If set to {@code true}, {@link SequencedDeadLetterQueue#clear(String)} will
         * be invoked upon a {@link #performReset()}/{@link #performReset(Object)} invocation. Defaults to
         * {@code false}.
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
         * Sets the {@link ListenerInvocationErrorHandler} dealing with {@link Exception exceptions} thrown by the
         * configured {@link EventMessageHandler event handlers} during <emn>dead-letter evaluation</emn>.
         * {@code Exceptions} thrown during regular event handling are
         * {@link SequencedDeadLetterQueue#enqueue(DeadLetter) enqueued} at all times. Defaults to a
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
            assertNonEmpty(processingGroup, "The processing group is a hard requirement and should be provided");
            assertNonNull(queue, "The DeadLetterQueue is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
