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

package org.axonframework.messaging.eventhandling.processing.streaming.token.annotation;


import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.WrappedToken;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of a {@link ParameterResolverFactory} that resolves the {@link TrackingToken} from the
 * {@link ProcessingContext} whenever it's available.
 *
 * @author Allard Buijze
 * @since 3.0.0
 */
public class TrackingTokenParameterResolverFactory implements ParameterResolverFactory {

    private static final TrackingTokenParameterResolver RESOLVER = new TrackingTokenParameterResolver();

    @Nullable
    @Override
    public ParameterResolver<TrackingToken> createInstance(@Nonnull Executable executable,
                                                           @Nonnull Parameter[] parameters,
                                                           int parameterIndex) {
        if (TrackingToken.class.equals(parameters[parameterIndex].getType())) {
            return RESOLVER;
        }
        return null;
    }

    private static class TrackingTokenParameterResolver implements ParameterResolver<TrackingToken> {

        @Nonnull
        @Override
        public CompletableFuture<TrackingToken> resolveParameterValue(@Nonnull ProcessingContext context) {
            return CompletableFuture.completedFuture(
                    TrackingToken.fromContext(context)
                                 .map(this::unwrap)
                                 .orElse(null)
            );
        }

        private TrackingToken unwrap(TrackingToken trackingToken) {
            return WrappedToken.unwrapLowerBound(trackingToken);
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return context.containsResource(TrackingToken.RESOURCE_KEY);
        }
    }
}
