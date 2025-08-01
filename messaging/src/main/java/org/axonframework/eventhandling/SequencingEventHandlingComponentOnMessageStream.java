/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of an {@link EventHandlingComponent} that ensures sequential processing of events with the same
 * sequence identifier. This component wraps a delegate {@code EventHandlingComponent} and coordinates event handling to
 * maintain ordering guarantees.
 * <p>
 * Events are processed sequentially when they share the same sequence identifier (as determined by
 * {@link #sequenceIdentifierFor(EventMessage, ProcessingContext)}). Events with different sequence identifiers can be
 * processed concurrently. This is particularly useful for maintaining consistency within aggregates or other logical
 * groupings where event ordering matters.
 * <p>
 * The sequencing is achieved by maintaining a map of ongoing invocations per sequence identifier in the
 * {@link ProcessingContext}. When an event arrives:
 * <ul>
 *     <li>If no previous invocation exists for its sequence identifier, the event is processed immediately</li>
 *     <li>If a previous invocation exists, the new event is chained to execute after the previous one completes</li>
 * </ul>
 * <p>
 * This implementation is thread-safe and uses atomic operations to prevent race conditions when multiple threads
 * attempt to process events with the same sequence identifier concurrently.
 * <p>
 * <b>Note:</b> The sequencing only applies within a single {@code ProcessingContext}. Events processed in different
 * contexts (e.g., different batches) are not coordinated with each other.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SequencingEventHandlingComponentOnMessageStream extends DelegatingEventHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(SequencingEventHandlingComponentOnMessageStream.class);

    /**
     * The {@link Context.ResourceKey} used to store the map of sequence identifiers to their ongoing invocations in the
     * {@link ProcessingContext}.
     */
    private final Context.ResourceKey<Map<Object, MessageStream<Message<?>>>> sequencedHandlingKey =
            Context.ResourceKey.withLabel("sequencedHandling");
    private final SequencingPolicy sequencingPolicy;

    /**
     * Constructs a {@code SequencingEventHandlingComponent} with the given {@code delegate} to receive calls.
     * <p>
     * The delegate will handle the actual event processing, while this component ensures proper sequencing based on the
     * sequence identifiers.
     *
     * @param delegate The {@link EventHandlingComponent} instance to delegate event handling calls to.
     */
    public SequencingEventHandlingComponentOnMessageStream(@Nonnull SequencingPolicy sequencingPolicy,
                                                           @Nonnull EventHandlingComponent delegate) {
        super(delegate);
        this.sequencingPolicy = sequencingPolicy;
    }

    /**
     * Handles the given {@code event} within the given {@code context}, ensuring sequential processing for events with
     * the same sequence identifier.
     * <p>
     * This method maintains a map of ongoing invocations per sequence identifier in the {@code ProcessingContext}.
     * Events with the same sequence identifier are processed sequentially using {@link MessageStream#whenComplete()} to
     * chain their execution. Events with different sequence identifiers can be processed concurrently.
     * <p>
     * The sequencing logic is thread-safe through the use of
     * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}, which provides atomic check-and-update
     * semantics.
     *
     * @param event   The {@link EventMessage} to handle.
     * @param context The {@link ProcessingContext} in which the {@code event} is handled.
     * @return A {@link MessageStream.Empty} that completes when the event handling is finished. For sequenced events,
     * this may complete later than the actual delegate invocation if the event is waiting for a previous event with the
     * same sequence identifier to complete.
     */
    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        Object eventSequenceIdentifier = sequenceIdentifierFor(event, context);
        logger.info("Handling event [{}] with sequence identifier [{}]", event, eventSequenceIdentifier);

        Map<Object, MessageStream<Message<?>>> sequenceMap = context.computeResourceIfAbsent(
                sequencedHandlingKey,
                ConcurrentHashMap::new
        );

        return sequenceMap.compute(eventSequenceIdentifier, (key, previousInvocation) -> {
                                       if (previousInvocation == null) {
                                           logger.info("No previous invocation for sequence identifier [{}], processing event [{}] immediately",
                                                       eventSequenceIdentifier, event);
                                           return delegate.handle(event, context).cast()
                                                          .whenComplete(() -> {
                                                          });
                                       } else {
                                           logger.info("Chaining event [{}] to previous invocation for sequence identifier [{}]",
                                                       event, eventSequenceIdentifier);
                                           return previousInvocation.concatWith(delegate.handle(event, context).cast())
                                                   .whenComplete(() -> {});
                                       }
                                   }
        ).ignoreEntries().cast();
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        requireNonNull(event, "Event Message may not be null");
        return sequencingPolicy.getSequenceIdentifierFor(event)
                               .orElseGet(() -> delegate.sequenceIdentifierFor(event, context));
    }
}
