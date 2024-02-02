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

import java.time.Instant;

/**
 * AbstractAnnotatedParameterResolverFactory that accepts parameters with type {@link Instant} that are annotated
 * with the {@link Timestamp} annotation and assigns the timestamp of the EventMessage.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Priority(Priority.HIGH)
public final class TimestampParameterResolverFactory
        extends AbstractAnnotatedParameterResolverFactory<Timestamp, Instant> {

    private final ParameterResolver<Instant> resolver;

    /**
     * Initializes a {@link ParameterResolverFactory} for {@link Timestamp}
     * annotated parameters
     */
    public TimestampParameterResolverFactory() {
        super(Timestamp.class, Instant.class);
        resolver = new TimestampParameterResolver();
    }

    @Override
    protected ParameterResolver<Instant> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver that resolved Timestamp parameters
     */
    static class TimestampParameterResolver implements ParameterResolver<Instant> {

        @Override
        public Instant resolveParameterValue(Message message, ProcessingContext processingContext) {
            if (message instanceof EventMessage) {
                return ((EventMessage) message).getTimestamp();
            }
            return null;
        }

        @Override
        public boolean matches(Message message, ProcessingContext processingContext) {
            return message instanceof EventMessage;
        }
    }
}
