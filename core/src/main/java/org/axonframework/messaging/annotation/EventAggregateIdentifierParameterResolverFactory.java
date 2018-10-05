/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.common.Priority;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.messaging.Message;

/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts
 * parameters of a {@link String} type that are annotated with the {@link EventAggregateIdentifier}
 * annotation and assigns the identifier of the DomainEventMessage.
 */
@Priority(Priority.HIGH)
public final class EventAggregateIdentifierParameterResolverFactory extends AbstractAnnotatedParameterResolverFactory<EventAggregateIdentifier, String> {

    private final ParameterResolver<String> resolver;

    /**
     * Initialize a {@link ParameterResolverFactory} for {@link EventAggregateIdentifier}
     * annotated parameters
     */
    public EventAggregateIdentifierParameterResolverFactory() {
        super(EventAggregateIdentifier.class, String.class);
        resolver = new MessageIdentifierParameterResolver();
    }

    @Override
    protected ParameterResolver<String> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver to resolve AggregateIdentifier parameters
     */
    static class MessageIdentifierParameterResolver implements ParameterResolver<String> {

        @Override
        public String resolveParameterValue(Message message) {
            if (message instanceof DomainEventMessage) {
                return ((DomainEventMessage) message).getAggregateIdentifier();
            }
            throw new IllegalArgumentException();
        }

        @Override
        public boolean matches(Message message) {
            return message instanceof DomainEventMessage;
        }

    }

}
