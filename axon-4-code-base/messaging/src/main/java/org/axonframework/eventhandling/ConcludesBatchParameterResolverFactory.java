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

package org.axonframework.eventhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AbstractAnnotatedParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

/**
 * Parameter resolver factory for boolean event handler parameters annotated with {@link ConcludesBatch}. If the event
 * is processed in the context of a {@link BatchingUnitOfWork} and is the last of the batch the resolver injects a
 * value of {@code true}. If the event is processed in another unit of work it is always assumed to be the last of a
 * batch.
 *
 * @author Rene de Waele
 */
public class ConcludesBatchParameterResolverFactory extends AbstractAnnotatedParameterResolverFactory<ConcludesBatch,
        Boolean> implements ParameterResolver<Boolean> {

    /**
     * Initialize a ConcludesBatchParameterResolverFactory.
     */
    public ConcludesBatchParameterResolverFactory() {
        super(ConcludesBatch.class, Boolean.class);
    }

    @Override
    protected ParameterResolver<Boolean> getResolver() {
        return this;
    }

    @Override
    public Boolean resolveParameterValue(Message<?> message) {
        return CurrentUnitOfWork.map(unitOfWork -> !(unitOfWork instanceof BatchingUnitOfWork<?>) ||
                ((BatchingUnitOfWork<?>) unitOfWork).isLastMessage(message)).orElse(true);
    }

    @Override
    public boolean matches(Message<?> message) {
        return message instanceof EventMessage<?>;
    }
}
