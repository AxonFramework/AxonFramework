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

package org.axonframework.commandhandling.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Priority;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * ParameterResolverFactory that add support for the UnitOfWork parameter type in annotated handlers.
 *
 * @author Allard Buijze
 * @since 2.0
 * @deprecated In favor of the {@link org.axonframework.messaging.unitofwork.ProcessingContextParameterResolverFactory}.
 */
@Priority(Priority.FIRST)
@Deprecated(since = "5.0.0")
public class CurrentUnitOfWorkParameterResolverFactory implements ParameterResolverFactory, ParameterResolver {

    @Nullable
    @Override
    public ParameterResolver createInstance(@Nonnull Executable executable, @Nonnull Parameter[] parameters, int parameterIndex) {
        if (LegacyUnitOfWork.class.equals(parameters[parameterIndex].getType())) {
            return this;
        }
        return null;
    }

    @Nullable
    @Override
    public Object resolveParameterValue(@Nonnull ProcessingContext context) {
        if (!CurrentUnitOfWork.isStarted()) {
            return null;
        }
        return CurrentUnitOfWork.get();
    }

    @Override
    public boolean matches(@Nonnull ProcessingContext context) {
        return true;
    }
}
