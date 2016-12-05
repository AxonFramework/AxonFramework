/*
 * Copyright (c) 2010-2016. Axon Framework
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
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
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
 * can be passed to method {@link #process(List)} for processing.
 *
 * @author Rene de Waele
 */
public abstract class AbstractEventProcessor implements EventProcessor {

    private final Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors = new CopyOnWriteArraySet<>();
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
     *                              NoOpErrorHandler} is used.
     * @param messageMonitor        Monitor to be invoked before and after event processing. If {@code null} a {@link
     *                              NoOpMessageMonitor} is used.
     */
    public AbstractEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this.name = requireNonNull(name);
        this.eventHandlerInvoker = requireNonNull(eventHandlerInvoker);
        this.rollbackConfiguration = requireNonNull(rollbackConfiguration);
        this.errorHandler = getOrDefault(errorHandler, () -> NoOpErrorHandler.INSTANCE);
        this.messageMonitor = getOrDefault(messageMonitor,
                                           (Supplier<MessageMonitor<? super EventMessage<?>>>) NoOpMessageMonitor::instance);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Registration registerInterceptor(MessageHandlerInterceptor<EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Process a batch of events. The messages are processed in a new {@link UnitOfWork}. Before each message is handled
     * the event processor creates an interceptor chain containing all registered {@link MessageHandlerInterceptor
     * interceptors}.
     *
     * @param eventMessages The batch of messages that is to be processed
     */
    protected void process(List<? extends EventMessage<?>> eventMessages) {
        Map<? extends EventMessage<?>, MessageMonitor.MonitorCallback> monitorCallbacks =
                eventMessages.stream().collect(toMap(Function.identity(), messageMonitor::onMessageIngested));
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventMessages);
        try {
            unitOfWork.executeWithResult(() -> {
                unitOfWork.resources().put("messageMonitor", monitorCallbacks.get(unitOfWork.getMessage()));
                unitOfWork.onRollback(uow -> errorHandler
                        .handleError(getName(), uow.getExecutionResult().getExceptionResult(), eventMessages,
                                     () -> process(eventMessages)));
                unitOfWork.onCleanup(uow -> {
                    MessageMonitor.MonitorCallback callback = uow.getResource("messageMonitor");
                    if (uow.isRolledBack()) {
                        callback.reportFailure(uow.getExecutionResult().getExceptionResult());
                    } else {
                        callback.reportSuccess();
                    }
                });
                return new DefaultInterceptorChain<>(unitOfWork, interceptors, eventHandlerInvoker).proceed();
            }, rollbackConfiguration);
        } catch (Exception e) {
            throw new EventProcessingException(
                    String.format("An exception occurred while processing events in EventProcessor [%s].%s", getName(),
                                  unitOfWork.isRolledBack() ? " Unit of Work has been rolled back." : ""), e);
        }
    }
}
