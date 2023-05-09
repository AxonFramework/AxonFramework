/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.common.Priority;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.messaging.Message;

/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts parameters of a {@link String} type that
 * are annotated with the {@link AggregateType} annotation and assigns the aggregate type of the
 * {@link org.axonframework.eventhandling.DomainEventMessage}.
 *
 * @author Frank Scheffler
 * @since 4.7.0
 */
@Priority(Priority.HIGH)
public final class AggregateTypeParameterResolverFactory
        extends AbstractAnnotatedParameterResolverFactory<AggregateType, String> {

    private final ParameterResolver<String> resolver;

    /**
     * Initialize a {@link ParameterResolverFactory} for {@link MessageIdentifier} annotated parameters.
     */
    public AggregateTypeParameterResolverFactory() {
        super(AggregateType.class, String.class);
        resolver = new AggregateTypeParameterResolver();
    }

    @Override
    protected ParameterResolver<String> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver to resolve {@link AggregateType} parameters
     */
    static class AggregateTypeParameterResolver implements ParameterResolver<String> {

        @Override
        public String resolveParameterValue(Message message) {
            return ((DomainEventMessage) message).getType();
        }

        @Override
        public boolean matches(Message message) {
            return message instanceof DomainEventMessage;
        }
    }
}
