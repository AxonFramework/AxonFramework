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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ParameterResolverFactory} that ensures the {@link QueryUpdateEmitter} is resolved in the context of the
 * current {@link ProcessingContext}.
 * <p>
 * For any message handler that declares this parameter, it will call
 * {@link QueryUpdateEmitter#forContext(ProcessingContext)} to create the appender.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class QueryUpdateEmitterParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<QueryUpdateEmitter> createInstance(@Nonnull Executable executable,
                                                                @Nonnull Parameter[] parameters,
                                                                int parameterIndex) {
        if (!QueryUpdateEmitter.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return null;
        }

        return new ParameterResolver<>() {
            @Nonnull
            @Override
            public CompletableFuture<QueryUpdateEmitter> resolveParameterValue(@Nonnull ProcessingContext context) {
                return CompletableFuture.completedFuture(QueryUpdateEmitter.forContext(context));
            }

            @Override
            public boolean matches(@Nonnull ProcessingContext context) {
                return true;
            }
        };
    }
}
