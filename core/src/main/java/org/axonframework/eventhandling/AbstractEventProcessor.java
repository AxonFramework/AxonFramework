/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Abstract implementation of an {@link EventProcessor}. Before processing of a batch of messages this implementation
 * creates a Unit of Work to process the batch.
 * <p>
 * Actual handling of events is deferred to an {@link EventHandlerInvoker}. Before each message is handled by the
 * invoker this event processor creates an interceptor chain containing all registered {@link MessageHandlerInterceptor
 * interceptors}.
 * <p>
 * Implementations are in charge of providing the events that need to be processed. Once these events are obtained they
 * can be passed to method {@link #processInUnitOfWork(List, UnitOfWork, Segment)} for processing.
 *
 * @author Rene de Waele
 */
public abstract class AbstractEventProcessor implements EventProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();
    private final String name;
    private final EventHandlerInvoker eventHandlerInvoker;
    private final RollbackConfiguration rollbackConfiguration;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;

    /**
     * Initializes an event processor with given {@code name}. Actual handling of event messages is deferred to the
     * given {@code eventHandlerInvoker}.
     *
     * @param name                  The name of the event processor
     * @param eventHandlerInvoker   The component that handles the individual events
     * @param rollbackConfiguration Determines rollback behavior of the UnitOfWork while processing a batch of events
     * @param errorHandler          Invoked when a UnitOfWork is rolled back during processing. If {@code null} a {@link
     *                              PropagatingErrorHandler} is used.
     * @param messageMonitor        Monitor to be invoked before and after event processing. If {@code null} a {@link
     *                              NoOpMessageMonitor} is used.
     */
    public AbstractEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this.name = requireNonNull(name);
        this.eventHandlerInvoker = requireNonNull(eventHandlerInvoker);
        this.rollbackConfiguration = requireNonNull(rollbackConfiguration);
        this.errorHandler = getOrDefault(errorHandler, () -> PropagatingErrorHandler.INSTANCE);
        this.messageMonitor = getOrDefault(messageMonitor,
                                           (Supplier<MessageMonitor<? super EventMessage<?>>>) NoOpMessageMonitor::instance);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Indicates whether the processor can/should handle the given {@code eventMessage} for the given {@code segment}.
     * <p>
     * This implementation will delegate the decision to the {@link EventHandlerInvoker}.
     *
     * @param eventMessage The message for which to identify if the processor can handle it
     * @param segment      The segment for which the event should be processed
     * @return {@code true} if the event message should be handled, otherwise {@code false}
     *
     * @throws Exception if the {@code errorHandler} throws an Exception back on the
     *                   {@link ErrorHandler#handleError(ErrorContext)} call
     */
    protected boolean canHandle(EventMessage<?> eventMessage, Segment segment) throws Exception {
        try {
            return eventHandlerInvoker.canHandle(eventMessage, segment);
        } catch (Exception e) {
            errorHandler.handleError(new ErrorContext(getName(), e, Collections.singletonList(eventMessage)));
            return false;
        }
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered {@link MessageHandlerInterceptor
     * interceptors}.
     *
     * @param eventMessages The batch of messages that is to be processed
     * @param unitOfWork    The Unit of Work that has been prepared to process the messages
     * @param segment       The segment for which the events should be processed
     * @throws Exception when an exception occurred during processing of the batch
     */
    protected void processInUnitOfWork(List<? extends EventMessage<?>> eventMessages,
                                       UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                       Segment segment) throws Exception {
        try {
            unitOfWork.executeWithResult(() -> {
                MessageMonitor.MonitorCallback monitorCallback =
                        messageMonitor.onMessageIngested(unitOfWork.getMessage());
                return new DefaultInterceptorChain<>(unitOfWork, interceptors, m -> {
                    try {
                        eventHandlerInvoker.handle(m, segment);
                        monitorCallback.reportSuccess();
                        return null;
                    } catch (Throwable throwable) {
                        monitorCallback.reportFailure(throwable);
                        throw throwable;
                    }
                }).proceed();
            }, rollbackConfiguration);
        } catch (Exception e) {
            if (unitOfWork.isRolledBack()) {
                errorHandler.handleError(new ErrorContext(getName(), e, eventMessages));
            } else {
                logger.info("Exception occurred while processing a message, but unit of work was committed. {}",
                            e.getClass().getName());
            }
        }
    }

    /**
     * Returns the invoker assigned to this processor. The invoker is responsible for invoking the correct handler
     * methods for any given message.
     *
     * @return the invoker assigned to this processor
     */
    protected EventHandlerInvoker eventHandlerInvoker() {
        return eventHandlerInvoker;
    }

    /**
     * Report the given {@code eventMessage} as ignored. Any registered {@link MessageMonitor} shall be notified of the
     * ignored message.
     * <p>
     * Typically, messages are ignored when they are received by a processor that has no suitable Handler for the type
     * of Event received.
     *
     * @param eventMessage the message that has been ignored.
     */
    protected void reportIgnored(EventMessage<?> eventMessage) {
        messageMonitor.onMessageIngested(eventMessage).reportIgnored();
    }
}
