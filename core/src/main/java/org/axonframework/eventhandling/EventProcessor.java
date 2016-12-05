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
import org.axonframework.messaging.MessageHandlerInterceptor;

/**
 * An Event Processor processes event messages from an event queue or event bus.
 * <p/>
 * Typically, an Event Processor is in charge of publishing the events to a group of registered listeners. This allows
 * attributes and behavior (e.g. transaction management, asynchronous processing, distribution) to be applied over
 * a whole group at once.
 * <p/>
 * Another use for Event Processors is to dispatch events to an exchange for remote processing.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface EventProcessor {

    /**
     * Returns the name of this event processor. This name is used to detect distributed instances of the
     * same event processor. Multiple instances referring to the same logical event processor (on different JVM's)
     * must have the same name.
     *
     * @return the name of this event processor
     */
    String getName();

    /**
     * Registers the given {@code interceptor} to this event processor. The {@code interceptor} will
     * receive each event message that is about to be published but before it has reached its event handlers.
     * Interceptors are free to modify the event message or stop publication altogether. In
     * addition, interceptors are able to interact with the {@link org.axonframework.messaging.unitofwork.UnitOfWork}
     * that is created to process the message.
     * <p/>
     * For example, if a {@link org.axonframework.messaging.interceptors.CorrelationDataInterceptor} is registered,
     * each command or event message triggered in response to an intercepted event will get correlation metadata
     * from the intercepted event.
     *
     * @param interceptor The interceptor to register.
     * @return a handle to unregister the {@code interceptor}. When unregistered the {@code interceptor} will
     * no longer receive events from this event processor.
     */
    Registration registerInterceptor(MessageHandlerInterceptor<EventMessage<?>> interceptor);

    /**
     * Start processing events.
     */
    void start();

    /**
     * Stop processing events.
     */
    void shutDown();
}
