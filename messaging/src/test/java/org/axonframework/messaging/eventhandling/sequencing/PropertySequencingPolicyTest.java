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

package org.axonframework.messaging.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.json.JacksonConverter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PropertySequencingPolicy}.
 *
 * @author Nils Christian Ehmke
 */
final class PropertySequencingPolicyTest {

    @Test
    void propertyExtractorShouldReadCorrectValue() {
        final SequencingPolicy sequencingPolicy =
                new ExtractionSequencingPolicy<>(
                        TestEvent.class,
                        TestEvent::id
                );

        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("42")),
                aProcessingContext())
        ).hasValue("42");
    }

    @Test
    void propertyShouldReadCorrectValue() {
        final SequencingPolicy sequencingPolicy = new PropertySequencingPolicy<>(
                TestEvent.class,
                "id"
        );

        assertThat(sequencingPolicy.getSequenceIdentifierFor(anEvent(new TestEvent("42")), aProcessingContext())
        ).hasValue("42");
    }

    @Test
    void withoutFallbackShouldThrowException() {
        final SequencingPolicy sequencingPolicy = new PropertySequencingPolicy<>(
                TestEvent.class,
                "id"
        );

        assertThrows(ConversionException.class,
                     () -> sequencingPolicy.getSequenceIdentifierFor(anEvent("42"), aProcessingContext()));
    }

    @Test
    void withFallbackShouldNotThrowException() {
        final SequencingPolicy sequencingPolicy = new FallbackSequencingPolicy<>(
                new PropertySequencingPolicy<>(
                        TestEvent.class,
                        "id"
                ),
                (event, context) -> Optional.of("A"),
                ConversionException.class
        );

        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent("42"),
                aProcessingContext())
        ).hasValue("A");
    }

    private EventMessage anEvent(final Object payload) {
        return EventTestUtils.asEventMessage(payload);
    }

    private static StubProcessingContext aProcessingContext() {
        return StubProcessingContext.withComponent(EventConverter.class, eventConverter());
    }

    @Nonnull
    private static EventConverter eventConverter() {
        return new DelegatingEventConverter(new JacksonConverter());
    }

    private record TestEvent(String id) {

    }
}
