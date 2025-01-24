/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageTypeResolver;

import java.util.List;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Default implementation of the EventGateway interface. It allow configuration of
 * {@link MessageDispatchInterceptor EventDispatchInterceptors}.
 * The Event Dispatch Interceptors can intercept and alter events dispatched on this specific gateway. Typically,
 * this would be used to add gateway specific meta data to the Event.
 *
 * @author Bert laverman
 * @since 4.1
 */
public class DefaultEventGateway extends AbstractEventGateway implements EventGateway {

    /**
     * Instantiate a {@link DefaultEventGateway} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventBus} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultEventGateway}
     *                instance
     */
    protected DefaultEventGateway(Builder builder) {
        super(builder);
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultEventGateway}.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link EventBus} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultEventGateway}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void publish(@Nonnull List<?> events) {
        events.forEach(this::publish);
    }

    /**
     * A Builder class for {@link DefaultEventGateway}s.
     * <p>
     * The {@code dispatchInterceptors} are defaulted to an empty list.
     * The {@link EventBus} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends AbstractEventGateway.Builder {

        @Override
        public Builder eventBus(@Nonnull EventBus eventBus) {
            super.eventBus(eventBus);
            return this;
        }

        @Override
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super EventMessage<?>>... dispatchInterceptors) {
            super.dispatchInterceptors(dispatchInterceptors);
            return this;
        }

        @Override
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors) {
            super.dispatchInterceptors(dispatchInterceptors);
            return this;
        }

        @Override
        public AbstractEventGateway.Builder messageNameResolver(MessageTypeResolver messageTypeResolver) {
            assertNonNull(messageTypeResolver, "MessageNameResolver may not be null");
            super.messageNameResolver(messageTypeResolver);
            return this;
        }

        /**
         * Initializes a {@link DefaultEventGateway} as specified through this Builder.
         *
         * @return a {@link DefaultEventGateway} as specified through this Builder
         */
        public DefaultEventGateway build() {
            return new DefaultEventGateway(this);
        }
    }
}
