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
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link ExpressionSequencingPolicy}.
 *
 * @author Mateusz Nowak
 */
@DisplayName("Unit-Test for the ExpressionSequencingPolicy")
final class ExpressionSequencingPolicyTest {

    @Test
    void shouldExtractSequenceIdentifierFromMatchingPayloadType() {
        // given
        final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                TestEvent::id,
                eventConverter()
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("42", 1)),
                aProcessingContext())
        ).hasValue("42");
    }

    @Test
    void shouldExtractComplexSequenceIdentifier() {
        // given
        final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                event -> event.id() + "-" + event.version(),
                eventConverter()
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("agg-123", 5)),
                aProcessingContext())
        ).hasValue("agg-123-5");
    }

    @Test
    void shouldReturnNullWhenExtractorReturnsNull() {
        // given
        final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                event -> null,
                eventConverter()
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("42", 1)),
                aProcessingContext())
        ).isNotPresent();
    }

    @Test
    void shouldReturnEmptyWhenExtractorReturnsNull() {
        // given
        final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                event -> null,
                eventConverter()
        );

        // when
        var result = sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("42", 1)),
                aProcessingContext());

        // then
        assertThat(result).isNotPresent();
    }

    @Test
    void shouldConvertPayloadWhenNotDirectlyAssignable() {
        // given
        final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                TestEvent::id,
                eventConverter()
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("converted-42", 1)),
                aProcessingContext())
        ).hasValue("converted-42");
    }

    @Test
    void shouldThrowConversionExceptionWhenPayloadCannotBeConverted() {
        // given
        final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                TestEvent::id,
                eventConverter()
        );

        // when / then
        assertThrows(ConversionException.class,
                () -> sequencingPolicy.getSequenceIdentifierFor(anEvent("unconvertible-string"), aProcessingContext()));
    }

    @Test
    void shouldWorkWithFallbackPolicyForConversionErrors() {
        // given
        final SequencingPolicy expressionPolicy = new ExpressionSequencingPolicy<>(
                TestEvent.class,
                TestEvent::id,
                eventConverter()
        );
        final SequencingPolicy fallbackPolicy = new FallbackSequencingPolicy<>(
                expressionPolicy,
                (event, context) -> Optional.of("fallback-result"),
                ConversionException.class
        );

        // when / then
        assertThat(fallbackPolicy.getSequenceIdentifierFor(
                anEvent("unconvertible-string"),
                aProcessingContext())
        ).hasValue("fallback-result");
    }

    @Nested
    class ConstructorValidation {

        @Test
        void shouldThrowNullPointerExceptionWhenPayloadClassIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () ->
                    new ExpressionSequencingPolicy<>(null, TestEvent::id, eventConverter()));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenIdentifierExtractorIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () ->
                    new ExpressionSequencingPolicy<>(TestEvent.class, null, eventConverter()));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenEventConverterIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () ->
                    new ExpressionSequencingPolicy<>(TestEvent.class, null, eventConverter()));
        }
    }

    @Nested
    class MethodParameterValidation {

        @Test
        void shouldThrowNullPointerExceptionWhenEventMessageIsNull() {
            // given
            final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                    TestEvent.class,
                    TestEvent::id,
                    eventConverter()
            );

            // when / then
            assertThrows(NullPointerException.class, () ->
                    sequencingPolicy.getSequenceIdentifierFor(null, aProcessingContext()));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenProcessingContextIsNull() {
            // given
            final SequencingPolicy sequencingPolicy = new ExpressionSequencingPolicy<>(
                    TestEvent.class,
                    TestEvent::id,
                    eventConverter()
            );

            // when / then
            assertThrows(NullPointerException.class, () ->
                    sequencingPolicy.getSequenceIdentifierFor(anEvent(new TestEvent("42", 1)), null));
        }
    }

    private EventMessage anEvent(final Object payload) {
        return EventTestUtils.asEventMessage(payload);
    }

    private static StubProcessingContext aProcessingContext() {
        return new StubProcessingContext();
    }

    @Nonnull
    private static EventConverter eventConverter() {
        return new DelegatingEventConverter(new JacksonConverter());
    }

    private record TestEvent(String id, int version) {
    }
}