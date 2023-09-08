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

package org.axonframework.modelling.command;

import org.axonframework.common.BuilderUtils;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;

/**
 * Default implementation of the {@link RepositorySpanFactory}. The attribute used for the id of the aggrate can be
 * configured.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public class DefaultRepositorySpanFactory implements RepositorySpanFactory {

    private final SpanFactory spanFactory;
    private final String aggregateIdAttribute;

    /**
     * Creates a new {@link DefaultRepositorySpanFactory} using the provided {@code builder}.
     *
     * @param builder The builder to build the {@link DefaultRepositorySpanFactory} from.
     */
    protected DefaultRepositorySpanFactory(Builder builder) {
        builder.validate();
        this.spanFactory = builder.builderSpanFactory;
        this.aggregateIdAttribute = builder.builderAggregateIdAttribute;
    }


    /**
     * Creates a new {@link Builder} to build a {@link DefaultRepositorySpanFactory} with. The {@code spanFactory} is a
     * required field and should be provided.
     *
     * @return The {@link Builder} to build a {@link DefaultRepositorySpanFactory} with.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Span createLoadSpan(String aggregateId) {
        return spanFactory.createInternalSpan(() -> "Repository.load")
                .addAttribute(aggregateIdAttribute, aggregateId);
    }

    @Override
    public Span createObtainLockSpan(String aggregateId) {
        return spanFactory.createInternalSpan(() -> "Repository.obtainLock")
                          .addAttribute(aggregateIdAttribute, aggregateId);
    }

    @Override
    public Span createInitializeStateSpan(String aggregateType, String aggregateId) {
        return spanFactory.createInternalSpan(() -> "Repository.initializeState(" + aggregateType + ")")
                          .addAttribute(aggregateIdAttribute, aggregateId);
    }


    /**
     * Builder class to instantiate a {@link DefaultRepositorySpanFactory}. The {@code spanFactory} is a required field
     * and should be provided.
     */
    public static class Builder {

        private SpanFactory builderSpanFactory;
        private String builderAggregateIdAttribute = "axon.aggregateId";

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
         * Sets the attribute name to use for the aggregate id. Defaults to {@code axon.aggregateId}.
         * @param aggregateIdAttribute The attribute name to use for the aggregate id.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder aggregateIdAttribute(String aggregateIdAttribute) {
            BuilderUtils.assertNonEmpty(aggregateIdAttribute, "aggregateIdAttribute may not be null");
            this.builderAggregateIdAttribute = aggregateIdAttribute;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         */
        protected void validate() {
            BuilderUtils.assertNonNull(builderSpanFactory, "spanFactory may not be null");
        }

        /**
         * Initializes a {@link DefaultRepositorySpanFactory} as specified through this Builder.
         *
         * @return The {@link DefaultRepositorySpanFactory} as specified through this Builder.
         */
        public DefaultRepositorySpanFactory build() {
            return new DefaultRepositorySpanFactory(this);
        }
    }
}
