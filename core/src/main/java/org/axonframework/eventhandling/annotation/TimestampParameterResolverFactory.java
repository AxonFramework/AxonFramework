/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.Priority;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;

import java.time.ZonedDateTime;

/**
 * AbstractAnnotatedParameterResolverFactory that accepts parameters with type {@link ZonedDateTime} that are annotated
 * with the {@link Timestamp} annotation and assigns the timestamp of the EventMessage.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Priority(Priority.HIGH)
public final class TimestampParameterResolverFactory
        extends AbstractAnnotatedParameterResolverFactory<Timestamp, ZonedDateTime> {

    private final ParameterResolver<ZonedDateTime> resolver;

    /**
     * Initializes a {@link org.axonframework.common.annotation.ParameterResolverFactory} for {@link Timestamp}
     * annotated parameters
     */
    public TimestampParameterResolverFactory() {
        super(Timestamp.class, ZonedDateTime.class);
        resolver = new TimestampParameterResolver();
    }

    @Override
    protected ParameterResolver<ZonedDateTime> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver that resolved Timestamp parameters
     */
    static class TimestampParameterResolver implements ParameterResolver<ZonedDateTime> {

        @Override
        public ZonedDateTime resolveParameterValue(Message message) {
            if (message instanceof EventMessage) {
                return ((EventMessage) message).getTimestamp();
            }
            return null;
        }

        @Override
        public boolean matches(Message message) {
            return message instanceof EventMessage;
        }
    }
}
