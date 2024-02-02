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

package org.axonframework.test;

import org.axonframework.common.Priority;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static org.axonframework.common.Priority.LAST;

/**
 * ParameterResolverFactory implementation for use in test cases that prevent that all declared resources on message
 * handlers need to be configured. This ParameterResolverFactory will return a parameter resolver for any parameter,
 * but will fail when that resolver is being used.
 * <p>
 * Because of this behavior, it is important that any resource resolvers doing actual resolution are executed before
 * this instance.
 *
 * @author Allard Buijze
 * @since 2.1
 */
@Priority(LAST)
public final class FixtureResourceParameterResolverFactory implements ParameterResolverFactory {

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        return new FailingParameterResolver(parameters[parameterIndex].getType());
    }

    private static class FailingParameterResolver implements ParameterResolver {

        private final Class<?> parameterType;

        public FailingParameterResolver(Class<?> parameterType) {
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message message, ProcessingContext processingContext) {
            throw new FixtureExecutionException("No resource of type [" + parameterType.getName()
                                                        + "] has been registered. It is required for one of the handlers being executed.");
        }

        @Override
        public boolean matches(Message message, ProcessingContext processingContext) {
            return true;
        }
    }
}
