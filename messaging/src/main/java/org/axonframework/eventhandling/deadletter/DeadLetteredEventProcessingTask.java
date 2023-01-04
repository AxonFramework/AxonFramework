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

import org.axonframework.common.ObjectUtils;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterParameterResolverFactory;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A {@link Function} dedicated to processing a single {@link DeadLetter dead letter} of an {@link EventMessage}. Used
 * by the {@link DeadLetteringEventHandlerInvoker} to ensure the dead letter is passed to the same set of
 * {@link EventMessageHandler event handling components}.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
class DeadLetteredEventProcessingTask
        implements Function<DeadLetter<EventMessage<?>>, EnqueueDecision<EventMessage<?>>> {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final List<EventMessageHandler> eventHandlingComponents;
    private final EnqueuePolicy<EventMessage<?>> enqueuePolicy;
    private final TransactionManager transactionManager;

    DeadLetteredEventProcessingTask(List<EventMessageHandler> eventHandlingComponents,
                                    EnqueuePolicy<EventMessage<?>> enqueuePolicy,
                                    TransactionManager transactionManager) {
        this.eventHandlingComponents = eventHandlingComponents;
        this.enqueuePolicy = enqueuePolicy;
        this.transactionManager = transactionManager;
    }

    @Override
    public EnqueueDecision<EventMessage<?>> apply(DeadLetter<EventMessage<?>> letter) {
        return process(letter);
    }

    /**
     * Process the given {@code letter} against this task's {@link EventMessageHandler event handling components}.
     * <p>
     * Will return an {@link EnqueueDecision} to
     * {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue#evict(DeadLetter) evict} the
     * {@code letter} on successful handling. On unsuccessful event handling the configured {@link EnqueuePolicy} is
     * used to decide what to do with the {@code letter}.
     *
     * @param letter The {@link DeadLetter dead letter} to process.
     * @return An {@link EnqueueDecision} describing what to do after processing the given {@code letter}.
     */
    public EnqueueDecision<EventMessage<?>> process(DeadLetter<? extends EventMessage<?>> letter) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start evaluation of dead letter with message id [{}].", letter.message().getIdentifier());
        }

        AtomicReference<EnqueueDecision<EventMessage<?>>> decision = new AtomicReference<>();
        UnitOfWork<? extends EventMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(letter.message());

        unitOfWork.attachTransaction(transactionManager);
        unitOfWork.resources()
                  .put(DeadLetterParameterResolverFactory.CURRENT_DEAD_LETTER, letter);
        unitOfWork.onPrepareCommit(uow -> decision.set(onCommit(letter)));
        unitOfWork.onRollback(uow -> decision.set(onRollback(letter, uow.getExecutionResult().getExceptionResult())));
        unitOfWork.executeWithResult(() -> handle(letter));

        return ObjectUtils.getOrDefault(decision.get(), Decisions::ignore);
    }

    private Object handle(DeadLetter<? extends EventMessage<?>> letter) throws Exception {
        for (EventMessageHandler handler : eventHandlingComponents) {
            handler.handle(letter.message());
        }
        // There's no result of event handling to return here.
        // We use this methods format to be able to define the Error Handler may throw Exceptions.
        return null;
    }

    private EnqueueDecision<EventMessage<?>> onCommit(DeadLetter<? extends EventMessage<?>> letter) {
        if (logger.isInfoEnabled()) {
            logger.info("Processing dead letter with message id [{}] was successful.", letter.message().getIdentifier());
        }
        return Decisions.evict();
    }

    private EnqueueDecision<EventMessage<?>> onRollback(DeadLetter<? extends EventMessage<?>> letter, Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("Processing dead letter with message id [{}] failed.", letter.message().getIdentifier(), cause);
        }
        return enqueuePolicy.decide(letter, cause);
    }
}