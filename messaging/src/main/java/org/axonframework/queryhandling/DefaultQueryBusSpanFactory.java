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
 * Default implementation of the {@link QueryBusSpanFactory}. Can be configured to include the query handling of a
 * distributed query in the same trace or not (true by default).
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public class DefaultQueryBusSpanFactory implements QueryBusSpanFactory {

    private final SpanFactory spanFactory;
    private final boolean distributedInSameTrace;

    /**
     * Creates a new {@link DefaultQueryBusSpanFactory} using the provided {@code builder}.
     *
     * @param builder The builder to build the {@link DefaultQueryBusSpanFactory} from.
     */
    protected DefaultQueryBusSpanFactory(Builder builder) {
        builder.validate();
        this.spanFactory = builder.builderSpanFactory;
        this.distributedInSameTrace = builder.distributedInSameTrace;
    }


    @Override
    public Span createQuerySpan(QueryMessage<?, ?> queryMessage, boolean distributed) {
        if (distributed) {
            return spanFactory.createDispatchSpan(() -> "QueryBus.queryDistributed", queryMessage);
        }
        return spanFactory.createInternalSpan(() -> "QueryBus.query", queryMessage);
    }

    @Override
    public Span createSubscriptionQuerySpan(SubscriptionQueryMessage<?, ?, ?> queryMessage, boolean distributed) {
        if (distributed) {
            return spanFactory.createDispatchSpan(() -> "QueryBus.subscriptionQueryDistributed", queryMessage);
        }
        return spanFactory.createInternalSpan(() -> "QueryBus.subscriptionQuery", queryMessage);
    }

    @Override
    public Span createSubscriptionQueryProcessUpdateSpan(SubscriptionQueryUpdateMessage<?> updateMessage,
                                                         SubscriptionQueryMessage<?, ?, ?> queryMessage) {
        return spanFactory.createChildHandlerSpan(() -> "QueryBus.queryUpdate", updateMessage, queryMessage);
    }

    @Override
    public Span createScatterGatherSpan(QueryMessage<?, ?> queryMessage, boolean distributed) {
        if (distributed) {
            return spanFactory.createDispatchSpan(() -> "QueryBus.scatterGatherQueryDistributed", queryMessage);
        }
        return spanFactory.createInternalSpan(() -> "QueryBus.scatterGatherQuery", queryMessage);
    }

    @Override
    public Span createScatterGatherHandlerSpan(QueryMessage<?, ?> queryMessage, int handlerIndex) {
        return spanFactory.createInternalSpan(() -> "QueryBus.scatterGatherHandler-" + handlerIndex, queryMessage);
    }

    @Override
    public Span createStreamingQuerySpan(QueryMessage<?, ?> queryMessage, boolean distributed) {
        if (distributed) {
            return spanFactory.createDispatchSpan(() -> "QueryBus.streamingQueryDistributed", queryMessage);
        }
        return spanFactory.createChildHandlerSpan(() -> "QueryBus.streamingQuery", queryMessage);
    }

    @Override
    public Span createQueryProcessingSpan(QueryMessage<?, ?> queryMessage) {
        if (distributedInSameTrace) {
            return spanFactory.createChildHandlerSpan(() -> "QueryBus.processQueryMessage", queryMessage);
        }
        return spanFactory.createLinkedHandlerSpan(() -> "QueryBus.processQueryMessage", queryMessage);
    }

    @Override
    public Span createResponseProcessingSpan(QueryMessage<?, ?> queryMessage) {
        return spanFactory.createInternalSpan(() -> "QueryBus.processQueryResponse", queryMessage);
    }

    @Override
    public <T, R, M extends QueryMessage<T, R>> M propagateContext(M queryMessage) {
        return spanFactory.propagateContext(queryMessage);
    }


    /**
     * Creates a Builder to be able to create a {@link DefaultQueryBusSpanFactory}. The default values are:
     * <ul>
     *     <li>{@code distributedInSameTrace} defaults to {@code true}</li>
     * </ul>
     * The {@code spanFactory} is a required field and should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultQueryBusSpanFactory}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class to instantiate a {@link DefaultQueryBusSpanFactory}. The default values are:
     * <ul>
     *     <li>{@code distributedInSameTrace} defaults to {@code true}</li>
     * </ul>
     * The {@code spanFactory} is a required field and should be provided.
     */
    public static class Builder {

        private boolean distributedInSameTrace = true;
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
         * Sets whether the distributed query should be in the same trace as the parent. Defaults to {@code true}.
         *
         * @param distributedInSameTrace whether the distributed query should be in the same trace as the parent.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder distributedInSameTrace(boolean distributedInSameTrace) {
            this.distributedInSameTrace = distributedInSameTrace;
            return this;
        }

        /**
         * Validates whether the fields contained in this builder are set accordingly.
         */
        protected void validate() {
            BuilderUtils.assertNonNull(builderSpanFactory, "spanFactory may not be null");
        }

        /**
         * Initializes a {@link DefaultQueryBusSpanFactory} as specified through this Builder.
         *
         * @return The {@link DefaultQueryBusSpanFactory} as specified through this Builder.
         */
        public DefaultQueryBusSpanFactory build() {
            return new DefaultQueryBusSpanFactory(this);
        }
    }
}
