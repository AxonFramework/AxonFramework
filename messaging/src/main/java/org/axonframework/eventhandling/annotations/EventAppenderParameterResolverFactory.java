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

package org.axonframework.eventhandling.annotations;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.annotations.ParameterResolver;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * {@link ParameterResolverFactory} that ensures the {@link EventAppender} is resolved in the context of the current
 * {@link ProcessingContext}.
 * <p>
 * For any message handler that declares this parameter, it will call
 * {@link EventAppender#forContext(ProcessingContext)} to create the appender.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class EventAppenderParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<?> createInstance(@Nonnull Executable executable,
                                               @Nonnull Parameter[] parameters,
                                               int parameterIndex) {
        if (EventAppender.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return new ParameterResolver<>() {
                @Nullable
                @Override
                public Object resolveParameterValue(@Nonnull ProcessingContext context) {
                    return EventAppender.forContext(context);
                }

                @Override
                public boolean matches(@Nonnull ProcessingContext context) {
                    return true;
                }
            };
        }
        return null;
    }
}
