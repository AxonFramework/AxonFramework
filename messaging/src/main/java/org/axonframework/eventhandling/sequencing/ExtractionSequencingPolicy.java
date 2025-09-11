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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the event message payload based
 * on a given property extractor.
 *
 * @param <T> The type of the supported event payloads.
 * @param <K> The type of the extracted property.
 * @author Mateusz Nowak
 * @author Nils Christian Ehmke
 * @since 5.0.0
 */
public class ExtractionSequencingPolicy<T, K> implements SequencingPolicy {

    private final Class<T> payloadClass;
    private final Function<T, K> identifierExtractor;
    private final EventConverter eventConverter;

    /**
     * Creates a new instance of the {@link ExtractionSequencingPolicy}, which extracts the sequence identifier from the
     * event message payload of the given {@code payloadClass} using the given {@code identifierExtractor}.
     *
     * @param payloadClass        The class of the supported event payloads.
     * @param identifierExtractor The function to extract the sequence identifier from the event payload.
     * @param eventConverter      The converter to use to convert event messages if their payload is not of the expected
     *                            type.
     */
    public ExtractionSequencingPolicy(
            @Nonnull Class<T> payloadClass,
            @Nonnull Function<T, K> identifierExtractor,
            @Nonnull EventConverter eventConverter
    ) {
        this.payloadClass = Objects.requireNonNull(payloadClass, "Payload class may not be null.");
        this.identifierExtractor = Objects.requireNonNull(identifierExtractor,
                                                          "Identifier extractor function may not be null.");
        this.eventConverter = Objects.requireNonNull(eventConverter, "EventConverter may not be null");
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(
            @Nonnull final EventMessage eventMessage,
            @Nonnull ProcessingContext context
    ) {
        Objects.requireNonNull(eventMessage, "EventMessage may not be null");
        Objects.requireNonNull(context, "ProcessingContext may not be null");

        var convertedPayload = eventMessage.payloadAs(payloadClass, eventConverter);
        return Optional.ofNullable(identifierExtractor.apply(convertedPayload));
    }
}
