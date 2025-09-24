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

package org.axonframework.commandhandling.annotations;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.gateway.CommandDispatcher;
import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.annotations.ParameterResolver;
import org.axonframework.messaging.annotations.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * {@link ParameterResolverFactory} that ensures the {@link CommandDispatcher} is resolved in the context of the current
 * {@link ProcessingContext}.
 * <p>
 * For any message handler that declares this parameter, it will call
 * {@link CommandDispatcher#forContext(ProcessingContext)} to create the appender.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class CommandDispatcherParameterResolverFactory implements ParameterResolverFactory {

    @Nullable
    @Override
    public ParameterResolver<CommandDispatcher> createInstance(@Nonnull Executable executable,
                                                               @Nonnull Parameter[] parameters,
                                                               int parameterIndex) {
        if (!CommandDispatcher.class.isAssignableFrom(parameters[parameterIndex].getType())) {
            return null;
        }

        return new ParameterResolver<>() {
            @Nullable
            @Override
            public CommandDispatcher resolveParameterValue(@Nonnull ProcessingContext context) {
                return CommandDispatcher.forContext(context);
            }

            @Override
            public boolean matches(@Nonnull ProcessingContext context) {
                return true;
            }
        };
    }
}
