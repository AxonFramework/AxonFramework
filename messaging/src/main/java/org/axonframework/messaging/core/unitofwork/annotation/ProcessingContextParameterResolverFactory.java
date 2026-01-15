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

package org.axonframework.messaging.core.unitofwork.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Priority;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.common.Priority.LOW;

/**
 * {@link ParameterResolverFactory} implementation that provides a {@link ParameterResolver} for parameters of type
 * {@link ProcessingContext}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Priority(LOW)
public class ProcessingContextParameterResolverFactory implements ParameterResolverFactory {

    private static final ProcessingContextParameterResolver INSTANCE = new ProcessingContextParameterResolver();

    @Nullable
    @Override
    public ParameterResolver<ProcessingContext> createInstance(@Nonnull Executable executable, @Nonnull Parameter[] parameters,
                                                               int parameterIndex) {

        Parameter parameter = parameters[parameterIndex];
        if (parameter.getType().isAssignableFrom(ProcessingContext.class)) {
            return INSTANCE;
        }
        return null;
    }

    private static class ProcessingContextParameterResolver implements ParameterResolver<ProcessingContext> {

        @Nonnull
        @Override
        public CompletableFuture<ProcessingContext> resolveParameterValue(@Nonnull ProcessingContext context) {
            return CompletableFuture.completedFuture(context);
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }
}
