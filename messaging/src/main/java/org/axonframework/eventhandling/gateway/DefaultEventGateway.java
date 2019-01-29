/*
 * Copyright (c) 2010-2019. Axon Framework
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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default implementation of the EventGateway interface. It allow configuration of {@link MessageDispatchInterceptor EventDispatchInterceptors}.
 * The Event Dispatch Interceptors can intercept and alter events dispatched on this specific gateway. Typically,
 * this would be used to add gateway specific meta data to the Event.
 *
 * @author Bert laverman
 * @since 4.0.4
 */
public class DefaultEventGateway extends AbstractEventGateway implements EventGateway {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEventGateway.class);

    private DefaultEventGateway(Builder builder) {
        super(builder);
    }

    /**
     * Return a builder.
     * @return A new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void publish(List<?> events) {
        events.forEach(this::publish);
    }

    /**
     * A Builder class for DefaultEventGateways.
     */
    public static class Builder extends AbstractEventGateway.Builder {

        @Override
        public Builder eventBus(EventBus eventBus) {
            super.eventBus(eventBus);
            return this;
        }

        @Override
        public Builder dispatchInterceptors(MessageDispatchInterceptor<? super EventMessage<?>>... dispatchInterceptors) {
            super.dispatchInterceptors(dispatchInterceptors);
            return this;
        }

        @Override
        public Builder dispatchInterceptors(List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors) {
            super.dispatchInterceptors(dispatchInterceptors);
            return this;
        }

        /**
         * Initializes a {@link DefaultEventGateway} as specified through this Builder.
         *
         * @return a {@link DefaultEventGateway} as specified through this Builder
         */
        public DefaultEventGateway build() {
            validate();
            return new DefaultEventGateway(this);
        }
    }
}
