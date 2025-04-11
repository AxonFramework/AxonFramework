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

package org.axonframework.eventhandling.gateway;

import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContextBindableComponent;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Interface towards the Event Handling components of an application. This interface provides a friendlier API toward
 * the {@link org.axonframework.eventhandling.EventSink} and allows for components to easily publish events.
 * <p>
 * Publishing an event generally requires a {@link ProcessingContext} to be provided. This guards the scope of the
 * transaction. For this reason, the {@link EventGateway} provides methods with, and without, a
 * {@link ProcessingContext} parameter. You can provide your current {@link ProcessingContext} to the
 * {@link EventGateway} to publish events in the current context. If no {@link ProcessingContext} is provided, the
 * behavior depends on the implementation. The {@link DefaultEventGateway} will create a new
 * {@link org.axonframework.messaging.unitofwork.AsyncUnitOfWork} and commit it right away.
 * <p>
 * When injected into a {@link org.axonframework.messaging.MessageHandler} as a parameter, the gateway will
 * automatically {@link #forProcessingContext(ProcessingContext) be bound to the context}. This means that any calls to
 * the {@link EventGateway} without a {@link ProcessingContext} will be published in the context of the message
 * handler.
 *
 * @author Bert Laverman
 * @see DefaultEventGateway
 * @since 4.1
 */
public interface EventGateway extends ProcessingContextBindableComponent<EventGateway> {

    /**
     * Publish a collection of events without specifying a {@link ProcessingContext}. If this gateway is bound to a
     * {@link ProcessingContext}, the events will be published in that context. If the gateway is not bound, the
     * implementation can decide how to proceed.
     *
     * @param events The collection of events to publish.
     */
    default void publish(Object... events) {
        publish(Arrays.asList(events));
    }

    /**
     * Publish a collection of events in a specific {@link ProcessingContext}.
     *
     * @param context The {@link ProcessingContext} in which to publish the events.
     * @param events  The collection of events to publish.
     */
    default void publish(@Nonnull ProcessingContext context, Object... events) {
        publish(context, Arrays.asList(events));
    }

    /**
     * Publish a collection of events without specifying a {@link ProcessingContext}. If this gateway is bound to a
     * {@link ProcessingContext}, the events will be published in that context. If the gateway is not bound, the
     * implementation can decide how to proceed.
     *
     * @param events The collection of events to publish.
     */
    void publish(@Nonnull List<?> events);

    /**
     * Publish a collection of events in a specific {@link ProcessingContext}.
     *
     * @param context The {@link ProcessingContext} in which to publish the events.
     * @param events  The collection of events to publish.
     */
    void publish(@Nonnull ProcessingContext context, @Nonnull List<?> events);

    /**
     * Bind this EventGateway to the given {@code processingContext}. This will ensure that any calls without specifying
     * the {@link ProcessingContext} will be published in the given {@code processingContext}.
     *
     * @param processingContext The {@link ProcessingContext} to bind this EventGateway to.
     * @return The EventGateway that is bound to the given {@code processingContext}.
     */
    default EventGateway forProcessingContext(ProcessingContext processingContext) {
        return new ProcessingContextBoundEventGateway(this, processingContext);
    }
}
