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
import org.axonframework.common.io.IOUtils;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

/**
 * @author Rene de Waele
 */
public class SubscribingEventProcessor extends AbstractEventProcessor {

    private final EventBus eventBus;
    private final EventProcessingStrategy processingStrategy;
    private volatile Registration eventBusRegistration;

    public SubscribingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker, EventBus eventBus) {
        this(name, eventHandlerInvoker, eventBus, NoOpMessageMonitor.INSTANCE);
    }

    public SubscribingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker, EventBus eventBus,
                                     MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this(name, eventHandlerInvoker, RollbackConfigurationType.ANY_THROWABLE, eventBus,
             DirectEventProcessingStrategy.INSTANCE, NoOpErrorHandler.INSTANCE, messageMonitor);
    }

    public SubscribingEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                     RollbackConfiguration rollbackConfiguration, EventBus eventBus,
                                     EventProcessingStrategy processingStrategy, ErrorHandler errorHandler,
                                     MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(name, eventHandlerInvoker, rollbackConfiguration, errorHandler, messageMonitor);
        this.eventBus = eventBus;
        this.processingStrategy = processingStrategy;
    }

    public void start() {
        eventBusRegistration = eventBus.subscribe(eventMessages -> processingStrategy.handle(eventMessages,
                                                                                             this::doProcessBatch));
    }

    public void shutDown() {
        IOUtils.closeQuietly(eventBusRegistration);
    }
}
