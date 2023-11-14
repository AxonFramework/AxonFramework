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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.SpanFactory;

import javax.annotation.Nonnull;

/**
 * Implementation of the {@link EventBus} that dispatches events in the thread the publishes them.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleEventBus extends AbstractEventBus {

    /**
     * Instantiate a Builder to be able to create a {@link SimpleEventBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor}, the {@code queueCapacity} to
     * {@link Integer#MAX_VALUE} and the {@link EventBusSpanFactory} to a {@link DefaultEventBusSpanFactory} backed by a
     * {@link org.axonframework.tracing.NoOpSpanFactory}.
     *
     * @return a Builder to be able to create a {@link SimpleEventBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link SimpleEventBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleEventBus} instance
     */
    protected SimpleEventBus(Builder builder) {
        super(builder);
    }

    /**
     * Builder class to instantiate a {@link SimpleEventBus}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor} and the {@link SpanFactory} is defaulted
     * to {@link DefaultEventBusSpanFactory} backed by a {@link org.axonframework.tracing.NoOpSpanFactory}.
     */
    public static class Builder extends AbstractEventBus.Builder {

        @Override
        public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        @Override
        public Builder spanFactory(@Nonnull EventBusSpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        /**
         * Initializes a {@link SimpleEventBus} as specified through this Builder.
         *
         * @return a {@link SimpleEventBus} as specified through this Builder
         */
        public SimpleEventBus build() {
            return new SimpleEventBus(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
        }
    }

}
