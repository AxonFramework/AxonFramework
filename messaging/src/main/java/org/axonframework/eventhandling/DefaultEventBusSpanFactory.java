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

import org.axonframework.common.BuilderUtils;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;

import static java.lang.String.format;

/**
 * Default implementation of the {@link EventBusSpanFactory}.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public class DefaultEventBusSpanFactory implements EventBusSpanFactory {

    private final SpanFactory spanFactory;

    /**
     * Creates a new {@link DefaultEventBusSpanFactory} using the provided {@code builder}.
     *
     * @param builder The builder to build the {@link DefaultEventBusSpanFactory} from.
     */
    protected DefaultEventBusSpanFactory(Builder builder) {
        builder.validate();
        this.spanFactory = builder.builderSpanFactory;
    }

    @Override
    public Span createPublishEventSpan(EventMessage<?> eventMessage) {
        return spanFactory.createDispatchSpan(() -> "publishEvent", eventMessage);
    }

    @Override
    public Span createCommitEventsSpan() {
        return spanFactory.createInternalSpan(() -> "commitEvents");
    }

    @Override
    public <T> EventMessage<T> propagateContext(EventMessage<T> eventMessage) {
        return spanFactory.propagateContext(eventMessage);
    }

    /**
     * Creates a new {@link Builder} to build a {@link DefaultEventBusSpanFactory} with. The {@code spanFactory} is a
     * required field and should be provided.
     *
     * @return The {@link Builder} to build a {@link DefaultEventBusSpanFactory} with.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class to instantiate a {@link DefaultEventBusSpanFactory}. The {@code spanFactory} is a required field
     * and should be provided.
     */
    public static class Builder {

        private SpanFactory builderSpanFactory;

        /**
         * Sets the {@link SpanFactory} to use to create the spans. This is a required field.
         *
         * @param spanFactory The {@link SpanFactory} to use to create the spans.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(SpanFactory spanFactory) {
            BuilderUtils.assertNonNull(spanFactory, "spanFactory may not be null");
            this.builderSpanFactory = spanFactory;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         */
        protected void validate() {
            BuilderUtils.assertNonNull(builderSpanFactory, "spanFactory may not be null");
        }

        /**
         * Initializes a {@link DefaultEventBusSpanFactory} as specified through this Builder.
         *
         * @return The {@link DefaultEventBusSpanFactory} as specified through this Builder.
         */
        public DefaultEventBusSpanFactory build() {
            return new DefaultEventBusSpanFactory(this);
        }
    }
}
