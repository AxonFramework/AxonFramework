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

package org.axonframework.eventhandling.replay;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.processors.streaming.token.ReplayToken;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * An implementation of the {@link org.axonframework.messaging.annotation.ParameterResolverFactory} which resolves the
 * {@link ReplayStatus} parameter. Will resolve a {@link ReplayStatus#REPLAY} parameter
 * if the {@link org.axonframework.messaging.Message} is a {@link org.axonframework.eventhandling.TrackedEventMessage},
 * containing a {@link ReplayToken}. Otherwise, it will resolve a
 * {@link ReplayStatus#REGULAR} parameter.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public class ReplayParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<ReplayStatus> createInstance(@Nonnull Executable executable, @Nonnull Parameter[] parameters, int parameterIndex) {
        if (ReplayStatus.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return new ReplayParameterResolver();
        }
        return null;
    }

    private static class ReplayParameterResolver implements ParameterResolver<ReplayStatus> {

        @Nullable
        @Override
        public ReplayStatus resolveParameterValue(@Nonnull ProcessingContext context) {
            if (Message.fromContext(context) instanceof EventMessage eventMessage) {
                return ReplayToken.isReplay(eventMessage) ? ReplayStatus.REPLAY : ReplayStatus.REGULAR;
            }
            return ReplayStatus.REGULAR;
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return Message.fromContext(context) instanceof EventMessage;
        }
    }
}
