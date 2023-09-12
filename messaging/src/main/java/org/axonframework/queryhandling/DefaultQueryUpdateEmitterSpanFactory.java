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

package org.axonframework.queryhandling;

import org.axonframework.common.BuilderUtils;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;

/**
 * Default implementation of the {@link QueryUpdateEmitterSpanFactory}.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public class DefaultQueryUpdateEmitterSpanFactory implements QueryUpdateEmitterSpanFactory {

    private final SpanFactory spanFactory;

    /**
     * Creates a new {@link DefaultQueryUpdateEmitterSpanFactory} using the provided {@code builder}.
     *
     * @param builder The builder to build the {@link DefaultQueryUpdateEmitterSpanFactory} from.
     */
    protected DefaultQueryUpdateEmitterSpanFactory(Builder builder) {
        builder.validate();
        this.spanFactory = builder.builderSpanFactory;
    }


    /**
     * Creates a Builder to be able to create a {@link DefaultQueryUpdateEmitterSpanFactory}.
     * The {@code spanFactory} is a required field and should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultQueryUpdateEmitterSpanFactory}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Span createUpdateScheduleEmitSpan(SubscriptionQueryUpdateMessage<?> update) {
        return spanFactory.createInternalSpan(() -> "QueryUpdateEmitter.scheduleQueryUpdateMessage", update);
    }

    @Override
    public Span createUpdateEmitSpan(SubscriptionQueryUpdateMessage<?> update) {
        return spanFactory.createDispatchSpan(() -> "QueryUpdateEmitter.emitQueryUpdateMessage", update);
    }

    @Override
    public <T, M extends SubscriptionQueryUpdateMessage<T>> M propagateContext(M update) {
        return spanFactory.propagateContext(update);
    }

    /**
     * Builder class to instantiate a {@link DefaultQueryUpdateEmitterSpanFactory}. The {@code spanFactory} is a
     * required field and should be provided.
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
         * Validates whether the fields contained in this builder are set accordingly.
         */
        protected void validate() {
            BuilderUtils.assertNonNull(builderSpanFactory, "spanFactory may not be null");
        }

        /**
         * Initializes a {@link DefaultQueryUpdateEmitterSpanFactory} as specified through this Builder.
         *
         * @return The {@link DefaultQueryUpdateEmitterSpanFactory} as specified through this Builder.
         */
        public DefaultQueryUpdateEmitterSpanFactory build() {
            return new DefaultQueryUpdateEmitterSpanFactory(this);
        }
    }
}
