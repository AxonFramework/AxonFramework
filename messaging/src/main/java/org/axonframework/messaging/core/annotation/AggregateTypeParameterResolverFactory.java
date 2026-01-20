/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Priority;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts parameters of a {@link String} type that
 * are annotated with the {@link AggregateType} annotation and assigns the aggregate type from the
 * {@link ProcessingContext} if present.
 *
 * @author Frank Scheffler
 * @since 4.7.0
 */
@Priority(Priority.HIGH)
public final class AggregateTypeParameterResolverFactory
        extends AbstractAnnotatedParameterResolverFactory<AggregateType, String> {

    private final ParameterResolver<String> resolver;

    /**
     * Initialize a {@link ParameterResolverFactory} for {@link MessageIdentifier} annotated parameters.
     */
    public AggregateTypeParameterResolverFactory() {
        super(AggregateType.class, String.class);
        resolver = new AggregateTypeParameterResolver();
    }

    @Override
    protected ParameterResolver<String> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver to resolve {@link AggregateType} parameters
     */
    static class AggregateTypeParameterResolver implements ParameterResolver<String> {

        @Nonnull
        @Override
        public CompletableFuture<String> resolveParameterValue(@Nonnull ProcessingContext context) {
            var aggregateType = context.getResource(LegacyResources.AGGREGATE_TYPE_KEY);
            if (aggregateType != null) {
                return CompletableFuture.completedFuture(aggregateType);
            }
            return FutureUtils.emptyCompletedFuture();
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return context.containsResource(LegacyResources.AGGREGATE_TYPE_KEY);
        }
    }
}
