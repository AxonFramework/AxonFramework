/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link SequencingPolicy} implementation that extracts the sequence identifier from the message payload based on a
 * given property extractor.
 *
 * @param <T> the type of the supported payload
 * @param <K> the type of the extracted property
 * @param <M> the type of message to sequence
 * @author Mateusz Nowak
 * @author Nils Christian Ehmke
 * @since 5.0.0
 */
public class ExtractionSequencingPolicy<T, K, M extends Message> implements SequencingPolicy<M> {

    private final Class<T> payloadClass;
    private final Function<T, K> identifierExtractor;

    /**
     * Creates a new instance of the {@link ExtractionSequencingPolicy}, which extracts the sequence identifier from the
     * message payload of the given {@code payloadClass} using the given {@code identifierExtractor}.
     *
     * @param payloadClass        The class of the supported payload.
     * @param identifierExtractor The function to extract the sequence identifier from the payload.
     */
    public ExtractionSequencingPolicy(
            @Nonnull Class<T> payloadClass,
            @Nonnull Function<T, K> identifierExtractor
    ) {
        this.payloadClass = Objects.requireNonNull(payloadClass, "Payload class may not be null.");
        this.identifierExtractor = Objects.requireNonNull(identifierExtractor,
                                                          "Identifier extractor function may not be null.");
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(
            @Nonnull final M message,
            @Nonnull ProcessingContext context
    ) {
        Objects.requireNonNull(message, "Message may not be null");
        Objects.requireNonNull(context, "ProcessingContext may not be null");

        var converter = message instanceof EventMessage ? context.component(EventConverter.class) : context.component(MessageConverter.class);
        var convertedPayload = message.payloadAs(payloadClass, converter);
        return Optional.ofNullable(identifierExtractor.apply(convertedPayload));
    }
}
