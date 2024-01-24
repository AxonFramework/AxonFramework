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

package org.axonframework.eventhandling.replay;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * An implementation of the {@link ParameterResolverFactory} which resolves the parameter annotated with
 * {@link ReplayContext}. Will resolve the parameter if the {@link Message} is a
 * {@link org.axonframework.eventhandling.TrackedEventMessage}, containing a {@link ReplayToken} with a matching context
 * of that type. Otherwise, it will resolve always to null.
 * <p>
 * This parameter resolver will always match to prevent missing event handlers.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class ReplayContextParameterResolverFactory implements ParameterResolverFactory {

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
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

        @Override
        public Object resolveParameterValue(Message message, ProcessingContext processingContext) {
            return ReplayToken.replayContext((EventMessage<?>) message, this.type).orElse(null);
        }

        @Override
        public boolean matches(Message message, ProcessingContext processingContext) {
            return message instanceof TrackedEventMessage;
        }
    }
}
