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

package org.axonframework.eventhandling.replay.annotations;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.processors.streaming.token.ReplayToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotations.ParameterResolver;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * An implementation of the {@link ParameterResolverFactory} which resolves the parameter annotated with
 * {@link ReplayContext}.
 * <p>
 * Will resolve the parameter from the {@link ProcessingContext} if it contains a {@link ReplayToken} with a matching
 * context of that type. Otherwise, it will resolve always to null. This parameter resolver will always match to prevent
 * missing event handlers.
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
            return new ReplayContextParameterResolver(parameter.getType());
        }
        return null;
    }

    private static class ReplayContextParameterResolver implements ParameterResolver<Object> {

        private final Class<?> type;

        public ReplayContextParameterResolver(Class<?> type) {
            this.type = type;
        }

        @Nullable
        @Override
        public Object resolveParameterValue(@Nonnull ProcessingContext context) {
            Optional<TrackingToken> token = TrackingToken.fromContext(context);
            if (token.isPresent()) {
                return ReplayToken.replayContext(token.get(), this.type).orElse(null);
            }
            return false;
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return Message.fromContext(context) instanceof EventMessage;
        }
    }
}
