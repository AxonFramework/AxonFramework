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

import org.axonframework.common.Priority;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AbstractAnnotatedParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;


/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts parameters of a {@link Long} type
 * annotated with the {@link SequenceNumber} annotation and assigns the sequenceNumber of the DomainEventMessage.
 * <p/>
 * Primitive long parameters are also supported.
 *
 * @author Mark Ingram
 * @since 2.1
 */
@Priority(Priority.HIGH)
public final class SequenceNumberParameterResolverFactory extends
        AbstractAnnotatedParameterResolverFactory<SequenceNumber, Long> {

    private final ParameterResolver<Long> resolver;

    /**
     * Initializes a {@link ParameterResolverFactory} for {@link SequenceNumber}
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

    /**
     * ParameterResolver that resolves SequenceNumber parameters
     */
    public static class SequenceNumberParameterResolver implements ParameterResolver<Long> {

        @Override
        public Long resolveParameterValue(Message message, ProcessingContext processingContext) {
            if (message instanceof DomainEventMessage) {
                return ((DomainEventMessage) message).getSequenceNumber();
            }
            return null;
        }

        @Override
        public boolean matches(Message message, ProcessingContext processingContext) {
            return message instanceof DomainEventMessage;
        }
    }
}
