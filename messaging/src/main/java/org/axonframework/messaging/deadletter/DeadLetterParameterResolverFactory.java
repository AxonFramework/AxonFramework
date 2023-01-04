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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * A {@link ParameterResolverFactory} constructing a {@link ParameterResolver} resolving the {@link DeadLetter} that is
 * being processed.
 * <p>
 * Expects the {@code DeadLetter} to reside under the {@link UnitOfWork#resources()} under the key
 * {@link DeadLetterParameterResolverFactory#CURRENT_DEAD_LETTER}. Hence, the {@code DeadLetter} processor is required
 * to add it to the resources before invoking the message handlers. If no {@link UnitOfWork} is active or there is no
 * {@code DeadLetter} is present, the resolver will return {@code null}.
 * <p>
 * The parameter resolver matches for any type of {@link Message}.
 *
 * @author Steven van Beelen
 * @since 4.7.0
 */
public class DeadLetterParameterResolverFactory implements ParameterResolverFactory {

    /**
     * Constant referring to the current {@link DeadLetter} within the {@link UnitOfWork#resources()}.
     */
    public static final String CURRENT_DEAD_LETTER = "___Axon_Current_Dead_Letter";

    @Override
    public ParameterResolver<DeadLetter<?>> createInstance(Executable executable,
                                                           Parameter[] parameters,
                                                           int parameterIndex) {
        return DeadLetter.class.equals(parameters[parameterIndex].getType()) ? new DeadLetterParameterResolver() : null;
    }

    /**
     * A {@link ParameterResolver} implementation resolving the current {@link DeadLetter}.
     * <p>
     * Expects the {@code DeadLetter} to reside under the {@link UnitOfWork#resources()} under the key
     * {@link DeadLetterParameterResolverFactory#CURRENT_DEAD_LETTER}. Furthermore, this resolver matches for any type
     * of {@link Message}.
     */
    static class DeadLetterParameterResolver implements ParameterResolver<DeadLetter<?>> {

        @Override
        public DeadLetter<?> resolveParameterValue(Message<?> message) {
            return CurrentUnitOfWork.isStarted()
                    ? CurrentUnitOfWork.get().getOrDefaultResource(CURRENT_DEAD_LETTER, null)
                    : null;
        }

        @Override
        public boolean matches(Message<?> message) {
            return true;
        }
    }
}
