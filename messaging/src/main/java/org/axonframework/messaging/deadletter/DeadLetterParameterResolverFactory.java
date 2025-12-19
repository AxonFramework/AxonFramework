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

package org.axonframework.messaging.deadletter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link ParameterResolverFactory} constructing a {@link ParameterResolver} resolving the {@link DeadLetter} that is
 * being processed.
 * <p>
 * Expects the {@code DeadLetter} to reside in the {@link ProcessingContext} using the {@link DeadLetter#RESOURCE_KEY}.
 * Hence, the {@code DeadLetter} processor is required to add it to the context before invoking the message handlers.
 * If no {@code DeadLetter} is present in the context, the resolver will return {@code null}.
 * <p>
 * The parameter resolver matches for any type of {@link Message}.
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 4.7.0
 */
public class DeadLetterParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<DeadLetter<?>> createInstance(@Nonnull Executable executable,
                                                           @Nonnull Parameter[] parameters,
                                                           int parameterIndex) {
        return DeadLetter.class.equals(parameters[parameterIndex].getType()) ? new DeadLetterParameterResolver() : null;
    }

    /**
     * A {@link ParameterResolver} implementation resolving the current {@link DeadLetter}.
     * <p>
     * Expects the {@code DeadLetter} to reside in the {@link ProcessingContext} using the
     * {@link DeadLetter#RESOURCE_KEY}. Furthermore, this resolver matches for any type of {@link Message}.
     */
    static class DeadLetterParameterResolver implements ParameterResolver<DeadLetter<?>> {

        @Nonnull
        @Override
        public CompletableFuture<DeadLetter<?>> resolveParameterValue(@Nonnull ProcessingContext context) {
            return CompletableFuture.completedFuture(context.getResource(DeadLetter.RESOURCE_KEY));
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }
}
