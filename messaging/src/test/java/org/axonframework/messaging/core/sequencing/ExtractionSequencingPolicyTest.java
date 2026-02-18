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
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ExtractionSequencingPolicy}.
 *
 * @author Mateusz Nowak
 */
final class ExtractionSequencingPolicyTest {

    @Test
    void shouldExtractSequenceIdentifierFromMatchingPayloadType() {
        // given
        SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                TestPayload::id
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestPayload("42", 1)),
                aProcessingContext())
        ).hasValue("42");
    }

    @Test
    void shouldExtractComplexSequenceIdentifier() {
        // given
        SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                event -> event.id() + "-" + event.version()
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestPayload("agg-123", 5)),
                aProcessingContext())
        ).hasValue("agg-123-5");
    }

    @Test
    void shouldReturnNullWhenExtractorReturnsNull() {
        // given
        SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                event -> null
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestPayload("42", 1)),
                aProcessingContext())
        ).isNotPresent();
    }

    @Test
    void shouldReturnEmptyWhenExtractorReturnsNull() {
        // given
        SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                event -> null
        );

        // when
        var result = sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestPayload("42", 1)),
                aProcessingContext());

        // then
        assertThat(result).isNotPresent();
    }

    @Test
    void shouldConvertPayloadWhenNotDirectlyAssignable() {
        // given
        SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                TestPayload::id
        );

        // when / then
        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent("""
                                {"id":"converted-42","version":"1"}
                                """),
                aProcessingContext())
        ).hasValue("converted-42");
    }

    @Test
    void shouldThrowConversionExceptionWhenPayloadCannotBeConverted() {
        // given
        SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                TestPayload::id
        );
        EventMessage exEvent = anEvent("unconvertible-string");
        StubProcessingContext exContext = aProcessingContext();

        // when / then
        assertThrows(ConversionException.class,
                     () -> sequencingPolicy.getSequenceIdentifierFor(exEvent,
                                                                     exContext));
    }

    @Test
    void shouldWorkWithFallbackPolicyForConversionErrors() {
        // given
        SequencingPolicy<Message> expressionPolicy = new ExtractionSequencingPolicy<>(
                TestPayload.class,
                TestPayload::id
        );
        SequencingPolicy<Message> fallbackPolicy = new FallbackSequencingPolicy<>(
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

    @Test
    void shouldUseEventConverterForEventMessages() {
        // setup
        Class<TestPayload> exPayloadType = TestPayload.class;
        TestPayload exPayload = new TestPayload("converted-42", 1);
        EventConverter exConverter = mock();
        EventMessage exMessage = mock();
        ProcessingContext exContext = mock();
        SequencingPolicy<Message> testSubject = new ExtractionSequencingPolicy<>(
                exPayloadType,
                TestPayload::id
        );

        when(exContext.component(EventConverter.class))
                .thenReturn(exConverter);
        when(exMessage.payloadAs(eq(exPayloadType), any()))
                .thenReturn(exPayload);

        // test
        assertThat(testSubject.getSequenceIdentifierFor(exMessage, exContext))
                .hasValue("converted-42");

        // verify
        verify(exContext).component(EventConverter.class);
        verify(exMessage).payloadAs(exPayloadType, exConverter);
        verifyNoMoreInteractions(exConverter, exContext, exMessage);
    }

    @Test
    void shouldUseMessageConverterForOtherMessages() {
        // setup
        Class<TestPayload> exPayloadType = TestPayload.class;
        TestPayload exPayload = new TestPayload("converted-42", 1);
        MessageConverter exConverter = mock();
        CommandMessage exMessage = mock();
        ProcessingContext exContext = mock();
        SequencingPolicy<Message> testSubject = new ExtractionSequencingPolicy<>(
                exPayloadType,
                TestPayload::id
        );

        when(exContext.component(MessageConverter.class))
                .thenReturn(exConverter);
        when(exMessage.payloadAs(eq(exPayloadType), any()))
                .thenReturn(exPayload);

        // test
        assertThat(testSubject.getSequenceIdentifierFor(exMessage, exContext))
                .hasValue("converted-42");

        // verify
        verify(exContext).component(MessageConverter.class);
        verify(exMessage).payloadAs(exPayloadType, exConverter);
        verifyNoMoreInteractions(exConverter, exContext, exMessage);
    }

    @Nested
    class ConstructorValidation {

        @Test
        void shouldThrowNullPointerExceptionWhenPayloadClassIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () ->
                    new ExtractionSequencingPolicy<>(null, TestPayload::id));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenIdentifierExtractorIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () ->
                    new ExtractionSequencingPolicy<>(TestPayload.class, null));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenEventConverterIsNull() {
            // when / then
            assertThrows(NullPointerException.class, () ->
                    new ExtractionSequencingPolicy<>(TestPayload.class, null));
        }
    }

    @Nested
    class MethodParameterValidation {

        @Test
        void shouldThrowNullPointerExceptionWhenEventMessageIsNull() {
            // given
            SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                    TestPayload.class,
                    TestPayload::id
            );
            StubProcessingContext exContext = aProcessingContext();

            // when / then
            assertThrows(NullPointerException.class, () ->
                    sequencingPolicy.getSequenceIdentifierFor(null, exContext));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenProcessingContextIsNull() {
            // given
            SequencingPolicy<Message> sequencingPolicy = new ExtractionSequencingPolicy<>(
                    TestPayload.class,
                    TestPayload::id
            );
            EventMessage exMessage = anEvent(new TestPayload("42", 1));

            // when / then
            assertThrows(NullPointerException.class, () ->
                    sequencingPolicy.getSequenceIdentifierFor(exMessage, null));
        }
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

    private record TestPayload(String id, int version) {

    }
}