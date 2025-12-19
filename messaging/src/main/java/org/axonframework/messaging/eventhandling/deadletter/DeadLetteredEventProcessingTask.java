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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.LegacyMessageSupportingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A processing task dedicated to handling a single {@link DeadLetter dead letter} of an {@link EventMessage}.
 * <p>
 * Used by {@link DeadLetteringEventHandlingComponent} to process dead letters through the delegate
 * {@link EventHandlingComponent}. The task ensures the dead letter is stored in the {@link ProcessingContext} so that
 * parameter resolvers can access it during processing.
 * <p>
 * Key differences from AF4's implementation:
 * <ul>
 *   <li>Uses {@link ProcessingContext} instead of {@code LegacyUnitOfWork}</li>
 *   <li>Returns {@link CompletableFuture CompletableFuture&lt;EnqueueDecision&gt;} for async processing</li>
 *   <li>Stores dead letter in {@link ProcessingContext} using {@link DeadLetter#RESOURCE_KEY}</li>
 *   <li>Error detection via {@link MessageStream} completion instead of try/catch</li>
 * </ul>
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DeadLetteredEventProcessingTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final EventHandlingComponent delegate;
    private final EnqueuePolicy<EventMessage> enqueuePolicy;

    /**
     * Constructs a {@link DeadLetteredEventProcessingTask}.
     *
     * @param delegate      The {@link EventHandlingComponent} to delegate event handling to.
     * @param enqueuePolicy The {@link EnqueuePolicy} to apply when processing fails.
     */
    DeadLetteredEventProcessingTask(EventHandlingComponent delegate, EnqueuePolicy<EventMessage> enqueuePolicy) {
        this.delegate = delegate;
        this.enqueuePolicy = enqueuePolicy;
    }

    /**
     * Processes the given {@code letter} through this task's delegate {@link EventHandlingComponent}.
     * <p>
     * Returns an {@link EnqueueDecision} to
     * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue#evict(DeadLetter) evict} the
     * {@code letter} on successful handling. On unsuccessful event handling, the configured {@link EnqueuePolicy} is
     * used to decide what to do with the {@code letter}.
     *
     * @param letter The {@link DeadLetter dead letter} to process.
     * @return A {@link CompletableFuture} containing an {@link EnqueueDecision} describing what to do after processing
     * the given {@code letter}.
     */
    public CompletableFuture<EnqueueDecision<EventMessage>> process(DeadLetter<? extends EventMessage> letter) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start evaluation of dead letter with message id [{}].", letter.message().identifier());
        }

        EventMessage message = letter.message();
        ProcessingContext context = new LegacyMessageSupportingContext(message);

        // Store dead letter in context for parameter resolvers
        context.putResource(DeadLetter.RESOURCE_KEY, letter);

        AtomicReference<EnqueueDecision<EventMessage>> decision = new AtomicReference<>();

        MessageStream.Empty<Message> result = delegate.handle(message, context);

        return result.reduce(null, (acc, entry) -> acc)
                     .handle((ignored, error) -> {
                         if (error != null) {
                             return onError(letter, error);
                         } else if (result.error().isPresent()) {
                             return onError(letter, result.error().get());
                         } else {
                             return onSuccess(letter);
                         }
                     });
    }

    /**
     * Handles successful processing of a dead letter.
     *
     * @param letter The dead letter that was successfully processed.
     * @return An {@link EnqueueDecision} to evict the letter from the queue.
     */
    private EnqueueDecision<EventMessage> onSuccess(DeadLetter<? extends EventMessage> letter) {
        if (logger.isInfoEnabled()) {
            logger.info("Processing dead letter with message id [{}] was successful.",
                        letter.message().identifier());
        }
        return Decisions.evict();
    }

    /**
     * Handles failed processing of a dead letter by applying the enqueue policy.
     *
     * @param letter The dead letter that failed processing.
     * @param cause  The error that caused the failure.
     * @return An {@link EnqueueDecision} based on the enqueue policy.
     */
    private EnqueueDecision<EventMessage> onError(DeadLetter<? extends EventMessage> letter, Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("Processing dead letter with message id [{}] failed.",
                        letter.message().identifier(), cause);
        }
        return enqueuePolicy.decide(letter, cause);
    }
}
