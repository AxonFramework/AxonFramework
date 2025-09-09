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

package org.axonframework.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.serialization.ConversionException;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the event message payload based
 * on a given property or property extractor. If the event message payload is not of a supported type, a fallback
 * sequencing policy is used. By default the fallback sequencing policy raises an exception.
 *
 * @param <T> The type of the supported event payloads.
 * @param <K> The type of the extracted property.
 * @author Nils Christian Ehmke
 * @since 4.5.2
 */
@SuppressWarnings("rawtypes")
public class PropertySequencingPolicy<T, K> extends ExpressionSequencingPolicy<T, K> {

    private final SequencingPolicy fallbackSequencingPolicy;

    /**
     * Creates a new instance of the {@link PropertySequencingPolicy}, which extracts the sequence identifier from the
     * event message payload of the given {@code payloadClass} using the given {@code identifierExtractor}. If the event
     * message payload is not of the given {@code payloadClass}, the given {@code fallbackSequencingPolicy} is used.
     *
     * @param payloadClass             The class of the supported event payloads.
     * @param propertyName             The name of the property to be extracted as sequence identifier.
     * @param fallbackSequencingPolicy The sequencing policy to be used if the event payload is not of the supported
     *                                 type.
     */
    public PropertySequencingPolicy(
            @Nonnull Class<T> payloadClass,
            @Nonnull String propertyName,
            @Nonnull EventConverter eventConverter,
            @Nonnull SequencingPolicy fallbackSequencingPolicy
    ) {
        super(payloadClass, extractProperty(payloadClass, propertyName)::getValue, eventConverter);
        this.fallbackSequencingPolicy = fallbackSequencingPolicy;
    }

    public PropertySequencingPolicy(
            @Nonnull Class<T> payloadClass,
            @Nonnull String propertyName,
            @Nonnull EventConverter eventConverter
    ) {
        this(payloadClass, propertyName, eventConverter, ExceptionRaisingSequencingPolicy.instance());
    }

    private static <T> Property<T> extractProperty(@Nonnull Class<T> payloadClass, @Nonnull String propertyName) {
        final Property<T> property = PropertyAccessStrategy.getProperty(payloadClass, propertyName);
        assertNonNull(property, "Property cannot be found");
        return property;
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(
            @Nonnull final EventMessage eventMessage,
            @Nonnull ProcessingContext context
    ) {
        try {
            return super.getSequenceIdentifierFor(eventMessage, context);
        } catch (Exception e) {
            return fallbackSequencingPolicy.getSequenceIdentifierFor(eventMessage, context);
        }
    }

    /**
     * A simple implementation of a {@link SequencingPolicy} that raises an {@link IllegalArgumentException}.
     */
    private static final class ExceptionRaisingSequencingPolicy implements SequencingPolicy {

        private static final ExceptionRaisingSequencingPolicy INSTANCE = new ExceptionRaisingSequencingPolicy();

        public static ExceptionRaisingSequencingPolicy instance() {
            return INSTANCE;
        }

        @Override
        public Optional<Object> getSequenceIdentifierFor(@Nonnull final EventMessage eventMessage,
                                                         @Nonnull ProcessingContext context) {
            throw new IllegalArgumentException(
                    "The event message payload is not of a supported type. "
                            + "Either make sure that the processor only consumes supported events "
                            + "or add a fallback sequencing policy."
            );
        }
    }
}
