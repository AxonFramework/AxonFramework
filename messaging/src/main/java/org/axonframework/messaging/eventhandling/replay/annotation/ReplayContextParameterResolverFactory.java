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

package org.axonframework.messaging.eventhandling.replay.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of the {@link ParameterResolverFactory} which resolves the parameter annotated with
 * {@link ReplayContext}.
 * <p>
 * Will resolve the parameter from the {@link ProcessingContext} if it contains a {@link ReplayToken} with a matching
 * context of that type. Otherwise, it will resolve always to null. This parameter resolver will always match to prevent
 * missing event handlers.
 * <p>
 * <b>Note:</b> As of Axon Framework 5.0, the parameter type must implement
 * {@link org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext ReplayContext}.
 * For simple string contexts, use
 * {@link org.axonframework.messaging.eventhandling.processing.streaming.token.StringReplayContext StringReplayContext}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class ReplayContextParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<Object> createInstance(@Nonnull Executable executable,
                                                    @Nonnull Parameter[] parameters,
                                                    int parameterIndex) {
        Parameter parameter = parameters[parameterIndex];
        if (parameter.isAnnotationPresent(ReplayContext.class)) {
            Class<?> paramType = parameter.getType();
            if (!org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext.class
                    .isAssignableFrom(paramType)) {
                throw new IllegalArgumentException(
                        "Parameter annotated with @ReplayContext must be of a type that implements " +
                        "org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext. " +
                        "Found: " + paramType.getName() + ". " +
                        "For simple string contexts, use StringReplayContext."
                );
            }
            @SuppressWarnings("unchecked")
            Class<? extends org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext>
                    replayContextType = (Class<? extends org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext>) paramType;
            return new ReplayContextParameterResolver(replayContextType);
        }
        return null;
    }

    private static class ReplayContextParameterResolver implements ParameterResolver<Object> {

        private final Class<? extends org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext> type;

        public ReplayContextParameterResolver(
                Class<? extends org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayContext> type
        ) {
            this.type = type;
        }

        @Nonnull
        @Override
        public CompletableFuture<Object> resolveParameterValue(@Nonnull ProcessingContext context) {
            Optional<TrackingToken> token = TrackingToken.fromContext(context);
            if (token.isPresent()) {
                return CompletableFuture.completedFuture(
                        ReplayToken.replayContext(token.get(), type).orElse(null)
                );
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return Message.fromContext(context) instanceof EventMessage;
        }
    }
}
