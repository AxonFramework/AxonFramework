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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventUtils;
import org.axonframework.messaging.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of an EventGateway, which handles the dispatch interceptors. The
 * actual publishing of events is left to the subclasses.
 *
 * @author Bert Laverman
 * @since 4.1
 */
public abstract class AbstractEventGateway {

    private final EventBus eventBus;
    private final List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors;
    private final MessageNameResolver messageNameResolver;

    /**
     * Instantiate an {@link AbstractEventGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventBus} is not {@code null} and throws an {@link AxonConfigurationException}
     * if it is.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractEventGateway} instance
     */
    protected AbstractEventGateway(Builder builder) {
        builder.validate();
        this.eventBus = builder.eventBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
        this.messageNameResolver = builder.messageNameResolver;
    }

    /**
     * Publishes (dispatches) an event.
     *
     * @param event The event to publish.
     */
    protected void publish(@Nonnull Object event) {
        this.eventBus.publish(processInterceptors(EventUtils.asEventMessage(event, messageNameResolver)));
    }

    /**
     * Registers an event dispatch interceptor within an {@link EventGateway}.
     *
     * @param interceptor To intercept event messages
     * @return a registration which can be used to cancel the registration of given interceptor
     */
    public Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super EventMessage<?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    /**
     * Invokes all the dispatch interceptors and returns the EventMessage instance that should be dispatched.
     *
     * @param eventMessage The incoming event message
     * @return The event message to dispatch
     */
    @SuppressWarnings("unchecked")
    protected <E> EventMessage<? extends E> processInterceptors(EventMessage<E> eventMessage) {
        EventMessage<? extends E> message = eventMessage;
        for (MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor : dispatchInterceptors) {
            message = (EventMessage) dispatchInterceptor.handle(message);
        }
        return message;
    }

    /**
     * Returns the EventBus used by this EventGateway. Should be used for monitoring or testing.
     *
     * @return the EventBus used by this gateway.
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractEventGateway} implementations.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link EventBus} is a <b>hard requirement</b> and as such should be provided.
     */
    public abstract static class Builder {

        private EventBus eventBus;
        private List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors =
                new CopyOnWriteArrayList<>();
        private MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

        /**
         * Sets the {@link EventBus} used to publish events.
         *
         * @param eventBus an {@link EventBus} used to publish events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventBus(@Nonnull EventBus eventBus) {
            assertNonNull(eventBus, "EventBus may not be null");
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link EventMessage}s.
         * Are invoked when an event is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when an event is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super EventMessage<?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link MessageDispatchInterceptor}s for {@link EventMessage}s.
         * Are invoked when an event is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when an event is being dispatched
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && !dispatchInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(dispatchInterceptors)
                    : new CopyOnWriteArrayList<>();
            return this;
        }

        /**
         * Sets the {@link MessageNameResolver} to be used in order to resolve QualifiedName for published Event messages.
         *
         * @param messageNameResolver which provides QualifiedName for Event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageNameResolver(MessageNameResolver messageNameResolver) {
            this.messageNameResolver = messageNameResolver;
            return this;
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(eventBus, "The EventBus is a hard requirement and should be provided");
        }
    }
}
