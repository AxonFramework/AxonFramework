/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.DelayedMessageStream;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.messaging.deadletter.ThrowableCause.truncated;

/**
 * An {@link EventHandlingComponent} decorator that uses a {@link SequencedDeadLetterQueue} to enqueue
 * {@link EventMessage events} for which handling failed.
 * <p>
 * Uses an {@link EnqueuePolicy} to decide whether a failed event should be
 * {@link SequencedDeadLetterQueue#enqueue(Object, DeadLetter) enqueued}. Subsequent events belonging to an already
 * enqueued "sequence identifier" are also enqueued to maintain event ordering in the face of failures.
 * <p>
 * This component provides operations to {@link #processAny(ProcessingContext)} {@link DeadLetter dead letters} it has
 * enqueued through the {@link SequencedDeadLetterProcessor} contract. It ensures the same delegate
 * {@link EventHandlingComponent} is used when processing dead letters as with regular event handling.
 * <p>
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @see SequencedDeadLetterQueue
 * @see SequencedDeadLetterProcessor
 * @see EnqueuePolicy
 * @since 5.0.0
 */
public class DeadLetteringEventHandlingComponent extends DelegatingEventHandlingComponent
        implements SequencedDeadLetterProcessor<EventMessage> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final SequencedDeadLetterQueue<EventMessage> queue;
    private final EnqueuePolicy<EventMessage> enqueuePolicy;
    private final boolean allowReset;

    /**
     * Instantiate a {@link DeadLetteringEventHandlingComponent} based on the given {@link Builder builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetteringEventHandlingComponent} instance.
     */
    protected DeadLetteringEventHandlingComponent(Builder builder) {
        super(builder.delegate);
        this.queue = builder.queue;
        this.enqueuePolicy = builder.enqueuePolicy;
        this.allowReset = builder.allowReset;
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlingComponent}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} that truncates the
     * {@link Throwable#getMessage()} size to {@code 1024} characters when invoked for any dead letter. The
     * {@code allowReset} defaults to {@code false}.
     * <p>
     * Providing a {@code delegate} {@link EventHandlingComponent} and a {@link SequencedDeadLetterQueue} are
     * <b>hard requirements</b> and must be provided.
     *
     * @return A builder that can construct a {@link DeadLetteringEventHandlingComponent}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        Object sequenceIdentifier = sequenceIdentifierFor(event, context);

        // Check if the sequence is already dead-lettered (async operation)
        CompletableFuture<MessageStream<Message>> resultFuture = queue.contains(sequenceIdentifier)
                                                                      .thenCompose(isDeadLettered -> {
                                                                          if (isDeadLettered) {
                                                                              return handleAlreadyDeadLettered(event,
                                                                                                               sequenceIdentifier);
                                                                          } else {
                                                                              return handleNormally(event,
                                                                                                    context,
                                                                                                    sequenceIdentifier);
                                                                          }
                                                                      });

        return DelayedMessageStream.create(resultFuture).ignoreEntries().cast();
    }

    /**
     * Handles an event whose sequence is already dead-lettered by enqueueing it as a follow-up.
     *
     * @param event              The event to enqueue.
     * @param sequenceIdentifier The sequence identifier.
     * @return A future that completes with an empty stream when the enqueue operation is done.
     */
    private CompletableFuture<MessageStream<Message>> handleAlreadyDeadLettered(
            EventMessage event, Object sequenceIdentifier) {
        if (logger.isInfoEnabled()) {
            logger.info("Event with id [{}] is added to the dead-letter queue "
                                + "since its sequence id [{}] is already present.",
                        event.identifier(), sequenceIdentifier);
        }

        return queue.enqueueIfPresent(
                sequenceIdentifier,
                () -> new GenericDeadLetter<>(sequenceIdentifier, event)
        ).thenApply(enqueued -> MessageStream.empty().cast());
    }

    /**
     * Handles an event normally through the delegate, with dead-lettering on failure.
     *
     * @param event              The event to handle.
     * @param context            The processing context.
     * @param sequenceIdentifier The sequence identifier.
     * @return A future that completes with the handling result stream.
     */
    private CompletableFuture<MessageStream<Message>> handleNormally(
            EventMessage event, ProcessingContext context, Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Event [{}] with sequence id [{}] is not present in the dead-letter queue. "
                                 + "Handle operation is delegated to the wrapped EventHandlingComponent.",
                         event.identifier(), sequenceIdentifier);
        }

        // Delegate to the wrapped component and handle errors
        MessageStream.Empty<Message> result = delegate.handle(event, context);

        return CompletableFuture.completedFuture(
                result.onErrorContinue(error -> handleError(event, sequenceIdentifier, error))
        );
    }

    /**
     * Handles an error during event processing by applying the enqueue policy.
     * <p>
     * After the dead-letter decision is made and executed (enqueue or evict), processing continues normally. The error
     * is considered "handled" by being dead-lettered or evicted, and is not propagated to the processor. This allows
     * the processor to continue with the next event rather than aborting the work package.
     *
     * @param event              The event that failed.
     * @param sequenceIdentifier The sequence identifier.
     * @param error              The error that occurred.
     * @return An empty stream indicating the error was handled (either enqueued or evicted).
     */
    private MessageStream<Message> handleError(EventMessage event, Object sequenceIdentifier, Throwable error) {
        DeadLetter<EventMessage> letter = new GenericDeadLetter<>(sequenceIdentifier, event, error);
        EnqueueDecision<EventMessage> decision = enqueuePolicy.decide(letter, error);

        if (decision.shouldEnqueue()) {
            Throwable cause = decision.enqueueCause().orElse(null);
            DeadLetter<? extends EventMessage> letterWithCause = cause != null ? letter.withCause(cause) : letter;
            DeadLetter<? extends EventMessage> letterToEnqueue = decision.withDiagnostics(letterWithCause);

            if (logger.isInfoEnabled()) {
                logger.info("Event with id [{}] is being dead-lettered due to error: {}",
                            event.identifier(), error.getMessage());
            }

            // Enqueue the dead letter - the error is handled by being dead-lettered
            queue.enqueue(sequenceIdentifier, letterToEnqueue)
                 .whenComplete((v, enqueueError) -> {
                     if (enqueueError != null) {
                         logger.warn("Failed to enqueue dead letter for event [{}]: {}",
                                     event.identifier(), enqueueError.getMessage());
                     }
                 });

            // Return empty stream - error is handled, processor should continue
            return MessageStream.empty();
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("The enqueue policy decided not to dead letter event [{}].", event.identifier());
            }
            // Return empty stream - error is evicted/ignored, processor should continue
            return MessageStream.empty();
        }
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull ResetContext resetContext, @Nonnull ProcessingContext context) {
        if (allowReset) {
            CompletableFuture<MessageStream<Message>> resultFuture = queue.clear()
                                                                          .thenApply(v -> delegate.handle(resetContext,
                                                                                                          context));
            return DelayedMessageStream.create(resultFuture).ignoreEntries().cast();
        }
        return delegate.handle(resetContext, context);
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> process(@Nonnull Predicate<DeadLetter<? extends EventMessage>> sequenceFilter,
                                              @Nonnull ProcessingContext context) {
        DeadLetteredEventProcessingTask processingTask = new DeadLetteredEventProcessingTask(
                delegate, enqueuePolicy
        );
        return queue.process(sequenceFilter, letter -> processingTask.process(letter, context));
    }

    /**
     * Returns the {@link SequencedDeadLetterQueue} used by this component.
     *
     * @return The dead letter queue.
     */
    public SequencedDeadLetterQueue<EventMessage> getQueue() {
        return queue;
    }

    /**
     * Builder class to instantiate a {@link DeadLetteringEventHandlingComponent}.
     * <p>
     * The {@link EnqueuePolicy} defaults to returning {@link Decisions#enqueue(Throwable)} that truncates the
     * {@link Throwable#getMessage()} size to {@code 1024} characters when invoked for any dead letter. The
     * {@code allowReset} defaults to {@code false}.
     * <p>
     * Providing a {@code delegate} {@link EventHandlingComponent} and a {@link SequencedDeadLetterQueue} are
     * <b>hard requirements</b> and must be provided.
     */
    public static class Builder {

        private EventHandlingComponent delegate;
        private SequencedDeadLetterQueue<EventMessage> queue;
        private EnqueuePolicy<EventMessage> enqueuePolicy = (letter, cause) -> Decisions.enqueue(truncated(cause));
        private boolean allowReset = false;

        /**
         * Sets the {@link EventHandlingComponent} this decorator wraps.
         *
         * @param delegate The {@link EventHandlingComponent} to delegate event handling to.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder delegate(@Nonnull EventHandlingComponent delegate) {
            assertNonNull(delegate, "The delegate EventHandlingComponent may not be null");
            this.delegate = delegate;
            return this;
        }

        /**
         * Sets the {@link SequencedDeadLetterQueue} this component maintains dead letters with.
         *
         * @param queue The {@link SequencedDeadLetterQueue} to store dead letters in.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder queue(@Nonnull SequencedDeadLetterQueue<EventMessage> queue) {
            assertNonNull(queue, "The SequencedDeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the {@link EnqueuePolicy} used to decide whether a {@link DeadLetter dead letter} should be added to the
         * {@link SequencedDeadLetterQueue}.
         * <p>
         * Defaults to returning {@link Decisions#enqueue(Throwable)} that truncates the {@link Throwable#getMessage()}
         * size to {@code 1024} characters when invoked for any dead letter.
         *
         * @param enqueuePolicy The {@link EnqueuePolicy} to use for enqueue decisions.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder enqueuePolicy(@Nonnull EnqueuePolicy<EventMessage> enqueuePolicy) {
            assertNonNull(enqueuePolicy, "The EnqueuePolicy may not be null");
            this.enqueuePolicy = enqueuePolicy;
            return this;
        }

        /**
         * Sets whether this component supports resets of the {@link SequencedDeadLetterQueue}.
         * <p>
         * If set to {@code true}, {@link SequencedDeadLetterQueue#clear()} will be invoked upon a
         * {@link EventHandlingComponent#handle(ResetContext, ProcessingContext)} invocation.
         * <p>
         * Defaults to {@code false}.
         *
         * @param allowReset Whether to clear the queue on reset.
         * @return The current Builder instance for fluent interfacing.
         */
        public Builder allowReset(boolean allowReset) {
            this.allowReset = allowReset;
            return this;
        }

        /**
         * Initializes a {@link DeadLetteringEventHandlingComponent} as specified through this Builder.
         *
         * @return A {@link DeadLetteringEventHandlingComponent} as specified through this Builder.
         */
        public DeadLetteringEventHandlingComponent build() {
            validate();
            return new DeadLetteringEventHandlingComponent(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(delegate, "The delegate EventHandlingComponent is a hard requirement and should be provided");
            assertNonNull(queue, "The SequencedDeadLetterQueue is a hard requirement and should be provided");
        }
    }
}
