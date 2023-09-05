/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorSupport;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.messaging.deadletter.ThrowableCause.truncated;

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
        implements SequencedDeadLetterProcessor<EventMessage<?>>, MessageHandlerInterceptorSupport<EventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final SequencedDeadLetterQueue<EventMessage<?>> queue;
    private final EnqueuePolicy<EventMessage<?>> enqueuePolicy;
    private final TransactionManager transactionManager;
    private final boolean allowReset;
    private final boolean sequenceIdentifierCacheEnabled;
    private final int sequenceIdentifierCacheSize;
    private final Map<Segment, SequenceIdentifierCache> sequenceIdentifierCache;
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();

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
        this.sequenceIdentifierCacheEnabled = builder.sequenceIdentifierCacheEnabled;
        this.sequenceIdentifierCacheSize = builder.sequenceIdentifierCacheSize;
        if (sequenceIdentifierCacheEnabled) {
            sequenceIdentifierCache = new ConcurrentHashMap<>();
        } else {
            sequenceIdentifierCache = null;
        }
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} that truncates the
     * {@link Throwable#getMessage()} size to {@code 1024} characters when invoked for any dead letter, the
     * {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, {@code allowReset} defaults to {@code false},
     * {@code sequenceIdentifierCacheEnabled} defaults to {@code false} and  {@code sequenceIdentifierCacheSize}
     * defaults to {@code 1024}. Providing at least one Event Handler, a {@link SequencedDeadLetterQueue}, and a
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
            logger.trace("Ignoring event with id [{}] as it is not assigned to segment [{}].",
                         message.getIdentifier(), segment);
            return;
        }
        Object sequenceIdentifier = super.sequenceIdentifier(message);
        boolean mightBePresent = mightBePresent(sequenceIdentifier, segment);
        if (isPresent(mightBePresent, sequenceIdentifier, message)) {
            if (logger.isInfoEnabled()) {
                logger.info("Event with id [{}] is added to the dead-letter queue "
                                    + "since its queue id [{}] is already present.",
                            message.getIdentifier(), sequenceIdentifier);
            }
            markEnqueued(sequenceIdentifier, segment);
        } else {
            if (mightBePresent) {
                markNotEnqueued(sequenceIdentifier, segment);
            }
            invokeHandlers(message, segment, sequenceIdentifier);
        }
    }

    /**
     * If {@code mightBePresent} is {@code true} will check the {@link SequencedDeadLetterQueue} if the sequence is
     * already present. It will enqueue the message if the sequence is already present.
     *
     * @param mightBePresent     this can be used as optimization, when {@code false} it will not check with the queue.
     * @param sequenceIdentifier the sequence identifier of the {@code message}.
     * @param message            the message, which will be enqueued if the sequence identifier is already present.
     * @return {@code true} if the identifier was already present, and the message was enqueued as consequence, or
     * {@code false} otherwise.
     */
    private boolean isPresent(boolean mightBePresent, Object sequenceIdentifier, EventMessage<?> message) {
        return mightBePresent && queue.enqueueIfPresent(
                sequenceIdentifier,
                () -> new GenericDeadLetter<>(sequenceIdentifier, message)
        );
    }

    private void invokeHandlers(@Nonnull EventMessage<?> message, @Nonnull Segment segment, Object sequenceIdentifier) {
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
                Throwable cause = decision.enqueueCause().orElse(null);
                markEnqueued(sequenceIdentifier, segment);
                queue.enqueue(sequenceIdentifier, decision.withDiagnostics(letter.withCause(cause)));
            } else if (logger.isInfoEnabled()) {
                logger.info("The enqueue policy decided not to dead letter event [{}].", message.getIdentifier());
            }
        }
    }

    private boolean mightBePresent(
            @Nonnull Object sequenceIdentifier,
            @Nonnull Segment segment
    ) {
        if (!sequenceIdentifierCacheEnabled) {
            return true;
        }
        return sequenceIdentifierCache.computeIfAbsent(segment,
                                                       k -> new SequenceIdentifierCache(segment.getSegmentId(),
                                                                                        sequenceIdentifierCacheSize,
                                                                                        queue))
                                      .mightBePresent(sequenceIdentifier);
    }

    private void markEnqueued(@Nonnull Object sequenceIdentifier, @Nonnull Segment segment) {
        if (sequenceIdentifierCacheEnabled) {
            sequenceIdentifierCache.computeIfPresent(segment, (k, v) -> v.markEnqueued(sequenceIdentifier));
        }
    }

    private void markNotEnqueued(@Nonnull Object sequenceIdentifier, @Nonnull Segment segment) {
        if (sequenceIdentifierCacheEnabled) {
            sequenceIdentifierCache.computeIfPresent(segment, (k, v) -> v.markNotEnqueued(sequenceIdentifier));
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
                new DeadLetteredEventProcessingTask(super.eventHandlers(),
                                                    interceptors,
                                                    enqueuePolicy,
                                                    transactionManager);
        UnitOfWork<?> uow = new DefaultUnitOfWork<>(null);
        uow.attachTransaction(transactionManager);
        return uow.executeWithResult(() -> queue.process(sequenceFilter, processingTask::process)).getPayload();
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public void segmentReleased(Segment segment) {
        if (sequenceIdentifierCacheEnabled) {
            if (logger.isTraceEnabled()) {
                logger.trace("Clearing the cache for segment [{}].", segment.getSegmentId());
            }
            sequenceIdentifierCache.remove(segment);
        }
        super.segmentReleased(segment);
    }

    /**
     * Builder class to instantiate a {@link DeadLetteringEventHandlerInvoker}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} that truncates the
     * {@link Throwable#getMessage()} size to {@code 1024} characters when invoked for any dead letter, the
     * {@link ListenerInvocationErrorHandler} is defaulted to a {@link PropagatingErrorHandler}, the
     * {@link SequencingPolicy} to a {@link SequentialPerAggregatePolicy}, {@code allowReset} defaults to {@code false},
     * {@code sequenceIdentifierCacheEnabled} defaults to {@code false} and {@code sequenceIdentifierCacheSize} used
     * defaults to 1024. Providing at least one Event Handler, a {@link SequencedDeadLetterQueue}, and a
     * {@link TransactionManager} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder extends SimpleEventHandlerInvoker.Builder<Builder> {

        private SequencedDeadLetterQueue<EventMessage<?>> queue;
        private EnqueuePolicy<EventMessage<?>> enqueuePolicy = (letter, cause) -> Decisions.enqueue(truncated(cause));
        private TransactionManager transactionManager;
        private boolean allowReset = false;
        private boolean sequenceIdentifierCacheEnabled = false;
        private int sequenceIdentifierCacheSize = 1024;

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
         * {@link Decisions#enqueue(Throwable)} that truncates the {@link Throwable#getMessage()} size to {@code 1024}
         * characters when invoked for any dead letter.
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
         * Enabled this {@link DeadLetteringEventHandlerInvoker} to cache sequence identifiers. If enabled it will
         * create a {@link SequenceIdentifierCache} per segment to prevent calling
         * {@link SequencedDeadLetterQueue#enqueueIfPresent(Object, Supplier)} when we can be sure the sequence
         * identifier is not present. This can happen in two cases, either we started with an empty
         * {@link SequencedDeadLetterQueue} and we haven't enqueued this identifier yet. Or it was not empty at the
         * start, in which case we can skip if we checked if we needed to enqueue the identifier before, and it wasn't
         * the case. If the identifier might be present we always call the
         * SequencedDeadLetterQueue#enqueueIfPresent(Object, Supplier)} as the sequence might have been cleaned up in
         * the meantime.
         *
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder enableSequenceIdentifierCache() {
            this.sequenceIdentifierCacheEnabled = true;
            return this;
        }

        /**
         * Sets the size of the cache. When there are already sequences stored in a dead letter queue, we need to check
         * for each sequence identifier if it's already included. This result is stored in order to we can skip the
         * check the second time we encounter the same sequence identifier. To limit memory use, a limit is set, by
         * default at {@code 1024}. If you have a lot of long living aggregates, it might improve performance setting
         * this higher at the cost of more memory use. If you only have aggregates that are short-lived, setting it
         * lower frees up memory, while it might not affect performance. This setting is applied per {@link Segment}.
         * Note that this setting will only be used in combination with {@link #enableSequenceIdentifierCache()}, and
         * the {@link SequencedDeadLetterQueue} not being empty.
         *
         * @param sequenceIdentifierCacheSize The size to keep track of sequence identifiers which are not present.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder sequenceIdentifierCacheSize(int sequenceIdentifierCacheSize) {
            this.sequenceIdentifierCacheSize = sequenceIdentifierCacheSize;
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
