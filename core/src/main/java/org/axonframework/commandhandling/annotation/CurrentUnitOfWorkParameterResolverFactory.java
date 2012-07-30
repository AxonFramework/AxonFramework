/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.Message;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;

import java.lang.annotation.Annotation;

/**
 * ParameterResolverFactory that add support for the UnitOfWork parameter type in annotated handlers.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CurrentUnitOfWorkParameterResolverFactory extends ParameterResolverFactory implements ParameterResolver {

    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        if (UnitOfWork.class.isAssignableFrom(parameterType)) {
            return this;
        }
        return null;
    }

    @Override
    public Object resolveParameterValue(Message message) {
        return CurrentUnitOfWork.get();
    }

    @Override
    public boolean matches(Message message) {
        return CommandMessage.class.isInstance(message) && CurrentUnitOfWork.isStarted();
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }
}
