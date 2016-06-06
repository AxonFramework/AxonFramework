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

import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Rene de Waele
 */
public class PublishingEventProcessor extends AbstractEventProcessor {

    private final List<EventListener> eventListeners;
    private final RollbackConfiguration rollbackConfiguration;
    private final ErrorHandler errorHandler;

    public PublishingEventProcessor(String name, EventListener... eventListeners) {
        this(name, DirectEventProcessingStrategy.INSTANCE, Arrays.asList(eventListeners),
             RollbackConfigurationType.ANY_THROWABLE, new LoggingErrorHandler());
    }

    public PublishingEventProcessor(String name, EventProcessingStrategy processingStrategy,
                                    List<EventListener> eventListeners, RollbackConfiguration rollbackConfiguration,
                                    ErrorHandler errorHandler) {
        super(name, processingStrategy);
        this.eventListeners = new ArrayList<>(eventListeners);
        this.rollbackConfiguration = rollbackConfiguration;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doHandle(List<? extends EventMessage<?>> eventMessages) {
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(eventMessages);
        try {
            unitOfWork.executeWithResult(
                    () -> new DefaultInterceptorChain<>(unitOfWork, interceptors(), (message, uow) -> {
                        for (EventListener listener : eventListeners) {
                            try {
                                listener.handle(message);
                            } catch (Exception e) {
                                errorHandler.onError(e, message, listener, getName());
                            }
                        }
                        return null;
                    }).proceed(), rollbackConfiguration);
        } catch (Exception e) {
            throw new EventProcessingException(
                    String.format("An exception occurred while processing events in EventProcessor [%s].", getName()),
                    e);
        }
    }

}
