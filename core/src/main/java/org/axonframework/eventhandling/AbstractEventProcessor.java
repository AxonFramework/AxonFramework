/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.unitofwork.ExecutionResult;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventProcessor implements EventProcessor, Consumer<List<? extends EventMessage<?>>> {
    private final Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors = new CopyOnWriteArraySet<>();
    private final EventHandlerInvoker eventHandlerInvoker;
    private final RollbackConfiguration rollbackConfiguration;
    private final ErrorHandler errorHandler;

    public AbstractEventProcessor(EventHandlerInvoker eventHandlerInvoker, RollbackConfiguration rollbackConfiguration,
                                  ErrorHandler errorHandler) {
        this.eventHandlerInvoker = requireNonNull(eventHandlerInvoker);
        this.rollbackConfiguration = requireNonNull(rollbackConfiguration);
        this.errorHandler = requireNonNull(errorHandler);
    }

    @Override
    public String getName() {
        return eventHandlerInvoker.getName();
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
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventMessages);
        unitOfWork.onRollback(uow -> {
            ExecutionResult executionResult = uow.getExecutionResult();
            Throwable error = executionResult == null || !executionResult.isExceptionResult() ? null :
                    executionResult.getExceptionResult();
            errorHandler.handleError(getName(), error, eventMessages, () -> accept(eventMessages));
        });
        try {
            unitOfWork.executeWithResult(
                    () -> new DefaultInterceptorChain<>(unitOfWork, interceptors, eventHandlerInvoker).proceed(),
                    rollbackConfiguration);
        } catch (Exception e) {
            throw new EventProcessingException(
                    String.format("An exception occurred while processing events in EventProcessor [%s].%s", getName(),
                                  unitOfWork.isRolledBack() ? " Unit of Work has been rolled back." : ""), e);
        }
    }
}
