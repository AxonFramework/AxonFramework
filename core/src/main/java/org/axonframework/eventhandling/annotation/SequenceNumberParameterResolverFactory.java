/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.Message;


/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts parameters of a {@link Long} type
 * annotated with the {@link SequenceNumber} annotation and assigns the sequenceNumber of the DomainEventMessage.
 *
 * Primitive long parameters are also supported.
 *
 * @author Mark Ingram
 * @since 2.1
 */
public final class SequenceNumberParameterResolverFactory extends
        AbstractAnnotatedParameterResolverFactory<SequenceNumber, Long> {
    private final ParameterResolver<Long> resolver;

    /**
     * Initializes a {@link org.axonframework.common.annotation.ParameterResolverFactory} for {@link SequenceNumber}
     * annotated parameters
     */
    public SequenceNumberParameterResolverFactory() {
        super(SequenceNumber.class, Long.class);
        resolver = new SequenceNumberParameterResolver();
    }

    @Override
    protected ParameterResolver<Long> getResolver() {
        return resolver;
    }

    static class SequenceNumberParameterResolver implements ParameterResolver<Long> {
        @Override
        public Long resolveParameterValue(Message message) {
            if (message instanceof DomainEventMessage) {
                return ((DomainEventMessage) message).getSequenceNumber();
            }
            return null;
        }

        @Override
        public boolean matches(Message message) {
            return message instanceof DomainEventMessage;
        }
    }
}
