/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.common.Priority;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts parameters of a {@link String} type that
 * are annotated with the {@link MessageIdentifier} annotation and assigns the identifier of the Message.
 *
 * @author Steven van Beelen
 * @since 3.0
 */
@Priority(Priority.HIGH)
public final class MessageIdentifierParameterResolverFactory
        extends AbstractAnnotatedParameterResolverFactory<MessageIdentifier, String> {

    private final ParameterResolver<String> resolver;

    /**
     * Initialize a {@link ParameterResolverFactory} for {@link MessageIdentifier} annotated parameters.
     */
    public MessageIdentifierParameterResolverFactory() {
        super(MessageIdentifier.class, String.class);
        resolver = new MessageIdentifierParameterResolver();
    }

    @Override
    protected ParameterResolver<String> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver to resolve MessageIdentifier parameters
     */
    static class MessageIdentifierParameterResolver implements ParameterResolver<String> {

        @Override
        public String resolveParameterValue(@Nonnull ProcessingContext context) {
            return Message.fromContext(context).identifier();
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return Message.fromContext(context) != null;
        }
    }
}
