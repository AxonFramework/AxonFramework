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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventProcessor implements EventProcessor, Consumer<List<? extends EventMessage<?>>> {
    private final Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors = new CopyOnWriteArraySet<>();
    private final String name;
    private final EventHandlerInvoker eventHandlerInvoker;
    private final RollbackConfiguration rollbackConfiguration;
    private final ErrorHandler errorHandler;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;

    public AbstractEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                  RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                  MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this.name = requireNonNull(name);
        this.eventHandlerInvoker = requireNonNull(eventHandlerInvoker);
        this.rollbackConfiguration = requireNonNull(rollbackConfiguration);
        this.errorHandler = requireNonNull(errorHandler);
        this.messageMonitor = messageMonitor;
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

    @Override
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void accept(List<? extends EventMessage<?>> eventMessages) {
        Map<? extends EventMessage<?>, MessageMonitor.MonitorCallback> monitorCallbacks =
                eventMessages.stream().collect(toMap(Function.identity(), messageMonitor::onMessageIngested));
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventMessages);
        try {
            unitOfWork.executeWithResult(
                    () -> {
                        unitOfWork.onRollback(uow -> errorHandler
                                .handleError(getName(), uow.getExecutionResult().getExceptionResult(), eventMessages,
                                             () -> accept(eventMessages)));
                        unitOfWork.onCleanup(uow -> {
                            MessageMonitor.MonitorCallback callback = monitorCallbacks.get(uow.getMessage());
                            if (uow.isRolledBack()) {
                                callback.reportFailure(uow.getExecutionResult().getExceptionResult());
                            } else {
                                callback.reportSuccess();
                            }
                        });
                        return new DefaultInterceptorChain<>(unitOfWork, interceptors, eventHandlerInvoker).proceed();
                    },
                    rollbackConfiguration);
        } catch (Exception e) {
            throw new EventProcessingException(
                    String.format("An exception occurred while processing events in EventProcessor [%s].%s", getName(),
                                  unitOfWork.isRolledBack() ? " Unit of Work has been rolled back." : ""), e);
        }
    }
}
