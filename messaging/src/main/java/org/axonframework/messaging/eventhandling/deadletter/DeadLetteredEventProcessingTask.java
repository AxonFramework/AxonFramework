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

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
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

/**
 * A processing task dedicated to handling a single {@link DeadLetter dead letter} of an {@link EventMessage}.
 * <p>
 * Used by {@link DeadLetteringEventHandlingComponent} to process dead letters through the delegate
 * {@link EventHandlingComponent}. Each dead letter is processed in its own {@link UnitOfWork}, which ensures proper
 * transaction boundaries. The task ensures the dead letter is stored in the {@link ProcessingContext} so that parameter
 * resolvers can access it during processing.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @since 4.6.0
 */
@Internal
class DeadLetteredEventProcessingTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final EventHandlingComponent delegate;
    private final EnqueuePolicy<EventMessage> enqueuePolicy;
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Constructs a {@link DeadLetteredEventProcessingTask}.
     *
     * @param delegate          The {@link EventHandlingComponent} to delegate event handling to.
     * @param enqueuePolicy     The {@link EnqueuePolicy} to apply when processing fails.
     * @param unitOfWorkFactory The {@link UnitOfWorkFactory} to create {@link UnitOfWork} instances for processing.
     */
    DeadLetteredEventProcessingTask(EventHandlingComponent delegate,
                                    EnqueuePolicy<EventMessage> enqueuePolicy,
                                    UnitOfWorkFactory unitOfWorkFactory) {
        this.delegate = delegate;
        this.enqueuePolicy = enqueuePolicy;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    /**
     * Processes the given {@code letter} through this task's delegate {@link EventHandlingComponent}.
     * <p>
     * Each dead letter is processed in its own {@link UnitOfWork}, which provides proper transaction boundaries. Returns
     * an {@link EnqueueDecision} to
     * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue#evict(DeadLetter) evict} the {@code letter}
     * on successful handling. On unsuccessful event handling, the configured {@link EnqueuePolicy} is used to decide
     * what to do with the {@code letter}.
     * <p>
     * The dead letter is added to the {@link UnitOfWork}'s {@link ProcessingContext} as a resource (via
     * {@link DeadLetter#RESOURCE_KEY}) so that parameter resolvers can access it during processing. The message from
     * the dead letter is also added to the context via {@link Message#RESOURCE_KEY}.
     *
     * @param letter The {@link DeadLetter dead letter} to process.
     * @return A {@link CompletableFuture} containing an {@link EnqueueDecision} describing what to do after processing
     * the given {@code letter}.
     */
    public CompletableFuture<EnqueueDecision<EventMessage>> process(DeadLetter<? extends EventMessage> letter) {
        EventMessage message = letter.message();
        if (logger.isDebugEnabled()) {
            logger.debug("Start evaluation of dead letter with message id [{}].", message.identifier());
        }

        UnitOfWork unitOfWork = unitOfWorkFactory.create(message.identifier());

        return unitOfWork.executeWithResult(context -> {
            ProcessingContext enrichedContext = context
                    .withResource(DeadLetter.RESOURCE_KEY, letter)
                    .withResource(Message.RESOURCE_KEY, message);

            MessageStream.Empty<Message> result = delegate.handle(message, enrichedContext);

            return result.asCompletableFuture()
                         .handle((ignored, error) -> {
                             if (error != null) {
                                 return onError(letter, error);
                             } else {
                                 return onSuccess(letter);
                             }
                         });
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
