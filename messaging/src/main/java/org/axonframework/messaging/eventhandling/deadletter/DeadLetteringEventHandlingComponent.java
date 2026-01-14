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
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

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
    private static final EnqueuePolicy<EventMessage> DEFAULT_ENQUEUE_POLICY =
            (letter, cause) -> Decisions.enqueue(truncated(cause));

    private final SequencedDeadLetterQueue<EventMessage> queue;
    private final EnqueuePolicy<EventMessage> enqueuePolicy;
    private final boolean allowReset;

    /**
     * Instantiate a {@link DeadLetteringEventHandlingComponent} with the given {@code delegate} and {@code queue},
     * using the default {@link EnqueuePolicy} and allowing reset.
     * <p>
     * The default policy returns {@link Decisions#enqueue(Throwable)} that truncates the
     * {@link Throwable#getMessage()} size to {@code 1024} characters when invoked for any dead letter.
     *
     * @param delegate The {@link EventHandlingComponent} to delegate event handling to.
     * @param queue    The {@link SequencedDeadLetterQueue} to store dead letters in.
     */
    public DeadLetteringEventHandlingComponent(@Nonnull EventHandlingComponent delegate,
                                               @Nonnull SequencedDeadLetterQueue<EventMessage> queue) {
        this(delegate, queue, DEFAULT_ENQUEUE_POLICY, true);
    }

    /**
     * Instantiate a {@link DeadLetteringEventHandlingComponent} with the given {@code delegate}, {@code queue},
     * custom {@link EnqueuePolicy}, and reset behavior.
     *
     * @param delegate      The {@link EventHandlingComponent} to delegate event handling to.
     * @param queue         The {@link SequencedDeadLetterQueue} to store dead letters in.
     * @param enqueuePolicy The {@link EnqueuePolicy} to decide whether a failed event should be dead-lettered.
     * @param allowReset    Whether to clear the queue on reset. If {@code true},
     *                      {@link SequencedDeadLetterQueue#clear()} will be invoked upon a reset.
     */
    public DeadLetteringEventHandlingComponent(@Nonnull EventHandlingComponent delegate,
                                               @Nonnull SequencedDeadLetterQueue<EventMessage> queue,
                                               @Nonnull EnqueuePolicy<EventMessage> enqueuePolicy,
                                               boolean allowReset) {
        super(delegate);
        this.queue = queue;
        this.enqueuePolicy = enqueuePolicy;
        this.allowReset = allowReset;
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        Object sequenceIdentifier = sequenceIdentifierFor(event, context);

        CompletableFuture<MessageStream<Message>> resultFuture = queue.contains(sequenceIdentifier)
                                                                      .thenApply(isDeadLettered -> {
                                                                          if (isDeadLettered) {
                                                                              return handleAlreadyDeadLettered(event,
                                                                                                               sequenceIdentifier);
                                                                          }
                                                                          return handleNormally(event,
                                                                                                context,
                                                                                                sequenceIdentifier);
                                                                      });

        return DelayedMessageStream.create(resultFuture).ignoreEntries().cast();
    }

    /**
     * Handles an event whose sequence is already dead-lettered by enqueueing it as a follow-up.
     *
     * @param event              The event to enqueue.
     * @param sequenceIdentifier The sequence identifier.
     * @return A stream that completes after enqueueing.
     */
    private MessageStream<Message> handleAlreadyDeadLettered(EventMessage event, Object sequenceIdentifier) {
        if (logger.isInfoEnabled()) {
            logger.info("Event with id [{}] is added to the dead-letter queue "
                                + "since its sequence id [{}] is already present.",
                        event.identifier(), sequenceIdentifier);
        }

        CompletableFuture<MessageStream<Message>> enqueueFuture = queue.enqueueIfPresent(
                sequenceIdentifier,
                () -> new GenericDeadLetter<>(sequenceIdentifier, event)
        ).thenApply(enqueued -> MessageStream.empty());

        return DelayedMessageStream.create(enqueueFuture);
    }

    /**
     * Handles an event normally through the delegate, with dead-lettering on failure.
     *
     * @param event              The event to handle.
     * @param context            The processing context.
     * @param sequenceIdentifier The sequence identifier.
     * @return A stream representing the handling result with error handling.
     */
    private MessageStream<Message> handleNormally(EventMessage event,
                                                  ProcessingContext context,
                                                  Object sequenceIdentifier) {
        if (logger.isTraceEnabled()) {
            logger.trace("Event [{}] with sequence id [{}] is not present in the dead-letter queue. "
                                 + "Handle operation is delegated to the wrapped EventHandlingComponent.",
                         event.identifier(), sequenceIdentifier);
        }

        return delegate.handle(event, context)
                       .onErrorContinue(error -> handleError(event, sequenceIdentifier, error));
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
     * @return A stream that completes after the error is handled (either enqueued or evicted).
     */
    private MessageStream<Message> handleError(EventMessage event, Object sequenceIdentifier, Throwable error) {
        DeadLetter<EventMessage> letter = new GenericDeadLetter<>(sequenceIdentifier, event, error);
        EnqueueDecision<EventMessage> decision = enqueuePolicy.decide(letter, error);

        if (decision.shouldEnqueue()) {
            return enqueueDeadLetter(event, sequenceIdentifier, error, letter, decision);
        }

        if (logger.isInfoEnabled()) {
            logger.info("The enqueue policy decided not to dead letter event [{}].", event.identifier());
        }
        return MessageStream.empty();
    }

    private MessageStream<Message> enqueueDeadLetter(EventMessage event,
                                                     Object sequenceIdentifier,
                                                     Throwable error,
                                                     DeadLetter<EventMessage> letter,
                                                     EnqueueDecision<EventMessage> decision) {
        Throwable cause = decision.enqueueCause().orElse(null);
        DeadLetter<? extends EventMessage> letterWithCause = cause != null ? letter.withCause(cause) : letter;
        DeadLetter<? extends EventMessage> letterToEnqueue = decision.withDiagnostics(letterWithCause);

        if (logger.isInfoEnabled()) {
            logger.info("Event with id [{}] is being dead-lettered due to error: {}",
                        event.identifier(), error.getMessage());
        }

        CompletableFuture<MessageStream<Message>> enqueueFuture = queue.enqueue(
                sequenceIdentifier, letterToEnqueue
        ).thenApply(v -> MessageStream.empty());

        return DelayedMessageStream.create(enqueueFuture);
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
}
