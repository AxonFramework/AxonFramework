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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.serialization.Converter;

import java.util.Objects;

/**
 * Implementation of the {@link EntityIdResolver} that converts the payload through the configured {@link Converter}
 * the, taking the expected representation of the message handler through the {@link AnnotatedEntityMetamodel}. It will
 * then use the delegate {@link EntityIdResolver} to resolve the id, defaulting to the
 * {@link AnnotationBasedEntityIdResolver}.
 *
 * @param <ID> The type of the identifier to resolve.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class AnnotatedEntityIdResolver<ID> implements EntityIdResolver<ID>, DescribableComponent {

    private final AnnotatedEntityMetamodel<?> metamodel;
    private final MessageConverter converter;
    private final EntityIdResolver<ID> delegate;
    private final Class<ID> idType;

    /**
     * Constructs an {@code AnnotatedEntityMetamodelEntityIdResolver} for the provided
     * {@link AnnotatedEntityMetamodel}.
     *
     * @param metamodel The metamodel that dictates the expected representation of the message.
     * @param idType    The type of the id that will be resolved.
     * @param converter The {@link Converter to use}.
     * @param delegate  The {@link EntityIdResolver} to use on the message after conversion.
     */
    public AnnotatedEntityIdResolver(@Nonnull AnnotatedEntityMetamodel<?> metamodel,
                                     @Nonnull Class<ID> idType,
                                     @Nonnull MessageConverter converter,
                                     @Nonnull EntityIdResolver<ID> delegate) {
        this.idType = Objects.requireNonNull(idType, "The idType should not be null.");
        this.metamodel = Objects.requireNonNull(metamodel, "The metamodel should not be null,");
        this.converter = Objects.requireNonNull(converter, "The converter should not be null.");
        this.delegate = Objects.requireNonNull(delegate, "The delegate should not be null.");
    }

    @Nonnull
    @Override
    public ID resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        Class<?> expectedRepresentation = metamodel.getExpectedRepresentation(message.type().qualifiedName());
        if (expectedRepresentation != null) {
            Message<?> convertedMessage = converter.convertMessage(message, expectedRepresentation);
            return delegate.resolve(convertedMessage, context);
        }
        throw new AxonConfigurationException(
                "No expected representation found for message type [" + message.type().qualifiedName() + "]"
        );
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("converter", converter);
        descriptor.describeProperty("idType", idType);
        descriptor.describeProperty("metaModel", metamodel);
    }
}
