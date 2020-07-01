/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling.gateway;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.reactive.ReactorMessageDispatchInterceptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Arrays.asList;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link ReactorEventGateway} that uses Project Reactor to achieve reactiveness.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactorEventGateway implements ReactorEventGateway {

    private final EventBus eventBus;
    private final List<ReactorMessageDispatchInterceptor<EventMessage<?>>> dispatchInterceptors;

    /**
     * Creates an instance of {@link DefaultReactorEventGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventBus} is not {@code null} and throws an {@link
     * org.axonframework.common.AxonConfigurationException} if it is.
     * </p>
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultReactorEventGateway} instance
     */
    protected DefaultReactorEventGateway(Builder builder) {
        builder.validate();
        this.eventBus = builder.eventBus;
        this.dispatchInterceptors = builder.dispatchInterceptors;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultReactorEventGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link EventBus} is a <b>hard requirement</b>
     * </p>
     *
     * @return a Builder to be able to create a {@link DefaultReactorEventGateway}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Flux<Object> publish(List<?> events) {
        return Flux.fromIterable(events)
                   .map(event -> Mono.<EventMessage<?>>just(GenericEventMessage.asEventMessage(event)))
                   .flatMap(this::processEventInterceptors)
                   .flatMap(this::publishEvent)
                   .transform(this::getPayload);
    }

    @Override
    public Registration registerDispatchInterceptor(ReactorMessageDispatchInterceptor<EventMessage<?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    private Mono<EventMessage<?>> processEventInterceptors(Mono<EventMessage<?>> eventMessage) {
        return Flux.fromIterable(dispatchInterceptors)
                   .reduce(eventMessage, (event, interceptor) -> interceptor.intercept(event))
                   .flatMap(Mono::from);
    }

    private Mono<EventMessage<?>> publishEvent(EventMessage<?> eventMessage) {
        return Mono.fromRunnable(() -> eventBus.publish(eventMessage))
                   .thenReturn(eventMessage);
    }

    private Flux<Object> getPayload(Flux<EventMessage<?>> eventMessage) {
        return eventMessage.filter(r -> Objects.nonNull(r.getPayload()))
                           .map(Message::getPayload);
    }

    /**
     * Builder class to instantiate {@link DefaultReactorEventGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link EventBus} is a <b>hard requirement</b>
     * </p>
     */
    public static class Builder {

        private EventBus eventBus;
        private List<ReactorMessageDispatchInterceptor<EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

        /**
         * Sets the {@link EventBus} used to publish events.
         *
         * @param eventBus a {@link EventBus} used to publish events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventBus(EventBus eventBus) {
            assertNonNull(eventBus, "EventBus may not be null");
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Sets {@link ReactorMessageDispatchInterceptor}s for {@link EventMessage}s. Are invoked when an event is
         * being dispatched.
         *
         * @param dispatchInterceptors which are invoked when an event is being published
         * @return the current Builder instance, for fluent interfacing
         */
        @SafeVarargs
        public final Builder dispatchInterceptors(
                ReactorMessageDispatchInterceptor<EventMessage<?>>... dispatchInterceptors) {
            return dispatchInterceptors(asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link List} of {@link ReactorMessageDispatchInterceptor}s for {@link EventMessage}s. Are invoked
         * when an event is being dispatched.
         *
         * @param dispatchInterceptors which are invoked when an event is being published
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<ReactorMessageDispatchInterceptor<EventMessage<?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors != null && !dispatchInterceptors.isEmpty()
                    ? new CopyOnWriteArrayList<>(dispatchInterceptors)
                    : new CopyOnWriteArrayList<>();
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

        /**
         * Initializes a {@link DefaultReactorEventGateway} as specified through this Builder.
         *
         * @return a {@link DefaultReactorEventGateway} as specified through this Builder
         */
        public DefaultReactorEventGateway build() {
            return new DefaultReactorEventGateway(this);
        }
    }
}
