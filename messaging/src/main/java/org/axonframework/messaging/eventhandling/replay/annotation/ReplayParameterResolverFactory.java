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

package org.axonframework.messaging.eventhandling.replay.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.replay.ReplayStatus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of the {@link ParameterResolverFactory} which resolves the
 * {@link ReplayStatus} parameter.
 * <p>
 * Will resolve a {@link ReplayStatus#REPLAY} parameter if the {@link ProcessingContext} is a contains a
 * {@link ReplayToken}. Otherwise, it will resolve a {@link ReplayStatus#REGULAR} parameter.
 *
 * @author Allard Buijze
 * @since 3.2.0
 */
public class ReplayParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<ReplayStatus> createInstance(@Nonnull Executable executable,
                                                          @Nonnull Parameter[] parameters,
                                                          int parameterIndex) {
        if (ReplayStatus.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return new ReplayParameterResolver();
        }
        return null;
    }

    private static class ReplayParameterResolver implements ParameterResolver<ReplayStatus> {

        @Nonnull
        @Override
        public CompletableFuture<ReplayStatus> resolveParameterValue(@Nonnull ProcessingContext context) {
            Optional<TrackingToken> optionalToken = TrackingToken.fromContext(context);
            if (Message.fromContext(context) instanceof EventMessage && optionalToken.isPresent()) {
                return CompletableFuture.completedFuture(
                        ReplayToken.isReplay(optionalToken.get()) ? ReplayStatus.REPLAY : ReplayStatus.REGULAR
                );
            }
            return CompletableFuture.completedFuture(ReplayStatus.REGULAR);
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return Message.fromContext(context) instanceof EventMessage;
        }
    }
}
