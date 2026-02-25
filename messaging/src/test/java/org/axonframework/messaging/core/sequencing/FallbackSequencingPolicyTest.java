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
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link FallbackSequencingPolicy}.
 *
 * @author Mateusz Nowak
 */
final class FallbackSequencingPolicyTest {

    @Nested
    class Construction {

        @Test
        void shouldThrowNullPointerExceptionWhenDelegateIsNull() {
            // given
            SequencingPolicy<Message> fallback = (event, context) -> Optional.of("fallback");

            // when / then
            assertThrows(NullPointerException.class,
                         () -> new FallbackSequencingPolicy<>(null, fallback, RuntimeException.class));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenFallbackIsNull() {
            // given
            SequencingPolicy<Message> delegate = (event, context) -> Optional.of("delegate");

            // when / then
            assertThrows(NullPointerException.class,
                         () -> new FallbackSequencingPolicy<>(delegate, null, RuntimeException.class));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenExceptionTypeIsNull() {
            // given
            SequencingPolicy<Message> delegate = (event, context) -> Optional.of("delegate");
            SequencingPolicy<Message> fallback = (event, context) -> Optional.of("fallback");

            // when / then
            assertThrows(NullPointerException.class,
                         () -> new FallbackSequencingPolicy<>(delegate, fallback, null));
        }
    }

    @Nested
    class SequenceIdentification {

        @Test
        void shouldUseDelegateWhenDelegateSucceeds() {
            // given
            var expectedIdentifier = "delegate-result";
            SequencingPolicy<EventMessage> delegate = (event, context) -> Optional.of(expectedIdentifier);
            SequencingPolicy<EventMessage> fallback = (event, context) -> Optional.of("fallback-result");
            FallbackSequencingPolicy<RuntimeException, EventMessage> policy =
                    new FallbackSequencingPolicy<>(delegate, fallback, RuntimeException.class);

            // when
            var result = policy.sequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).hasValue(expectedIdentifier);
        }

        @Test
        void shouldUseFallbackWhenDelegateThrowsSpecifiedException() {
            // given
            var expectedIdentifier = "fallback-result";
            SequencingPolicy<EventMessage> delegate = (event, context) -> {
                throw new IllegalArgumentException("Delegate failed");
            };
            SequencingPolicy<EventMessage> fallback = (event, context) -> Optional.of(expectedIdentifier);
            FallbackSequencingPolicy<IllegalArgumentException, EventMessage> policy =
                    new FallbackSequencingPolicy<>(delegate, fallback, IllegalArgumentException.class);

            // when
            var result = policy.sequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).hasValue(expectedIdentifier);
        }

        @Test
        void shouldRethrowExceptionWhenDelegateThrowsUnhandledException() {
            // given
            SequencingPolicy<EventMessage> delegate = (event, context) -> {
                throw new RuntimeException("Unhandled exception");
            };
            SequencingPolicy<EventMessage> fallback = (event, context) -> Optional.of("fallback-result");
            FallbackSequencingPolicy<IllegalArgumentException, EventMessage> policy =
                    new FallbackSequencingPolicy<>(delegate, fallback, IllegalArgumentException.class);
            EventMessage exMessage = anEvent("test");
            ProcessingContext exContext = aProcessingContext();

            // when / then
            assertThrows(RuntimeException.class,
                         () -> policy.sequenceIdentifierFor(exMessage, exContext));
        }

        @Test
        void shouldHandleSubclassOfSpecifiedException() {
            // given
            var expectedIdentifier = "fallback-result";
            SequencingPolicy<EventMessage> delegate = (event, context) -> {
                throw new IllegalStateException("Delegate failed with subclass");
            };
            SequencingPolicy<EventMessage> fallback = (event, context) -> Optional.of(expectedIdentifier);
            FallbackSequencingPolicy<RuntimeException, EventMessage> policy =
                    new FallbackSequencingPolicy<>(delegate, fallback, RuntimeException.class);

            // when
            var result = policy.sequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).hasValue(expectedIdentifier);
        }

        @Test
        void shouldNotCallFallbackWhenDelegateSucceeds() {
            // given
            SequencingPolicy<EventMessage> delegate = (event, context) -> Optional.of("delegate-result");
            SequencingPolicy<EventMessage> fallback = (event, context) -> {
                throw new RuntimeException("Fallback should not be called");
            };
            FallbackSequencingPolicy<IllegalArgumentException, EventMessage> policy =
                    new FallbackSequencingPolicy<>(delegate, fallback, IllegalArgumentException.class);

            // when / then
            var result = policy.sequenceIdentifierFor(anEvent("test"), aProcessingContext());
            assertThat(result).hasValue("delegate-result");
        }
    }

    @Nested
    class ComplexScenarios {

        @Test
        void shouldHandleConversionExceptionFromPropertyPolicy() {
            // given
            EventMessage eventWithCorrectType = anEvent(new TestEvent("test-id"));
            EventMessage eventWithWrongType = anEvent("string-payload");

            FallbackSequencingPolicy<ConversionException, EventMessage> policy = getConversionExceptionFallbackSequencingPolicy();

            // when
            var resultForCorrectType = policy.sequenceIdentifierFor(eventWithCorrectType, aProcessingContext());
            var resultForWrongType = policy.sequenceIdentifierFor(eventWithWrongType, aProcessingContext());

            // then
            assertThat(resultForCorrectType).hasValue("test-id");
            assertThat(resultForWrongType).hasValue("default-sequence");
        }

        @Nonnull
        private FallbackSequencingPolicy<ConversionException, EventMessage> getConversionExceptionFallbackSequencingPolicy() {
            SequencingPolicy<EventMessage> propertyBasedPolicy = (event, context) -> {
                if (event.payload() instanceof TestEvent(String id)) {
                    return Optional.of(id);
                }
                throw new ConversionException("Cannot convert payload");
            };

            SequencingPolicy<EventMessage> generalPolicy = (event, context) -> Optional.of("default-sequence");

            return new FallbackSequencingPolicy<>(propertyBasedPolicy, generalPolicy, ConversionException.class);
        }

        @Test
        void shouldAllowChainingSeveralFallbackPolicies() {
            // given
            SequencingPolicy<EventMessage> firstPolicy = (event, context) -> {
                throw new IllegalArgumentException("First policy failed");
            };
            SequencingPolicy<EventMessage> secondPolicy = (event, context) -> {
                throw new ConversionException("Second policy failed");
            };
            SequencingPolicy<EventMessage> finalPolicy = (event, context) -> Optional.of("final-result");

            FallbackSequencingPolicy<ConversionException, EventMessage> secondFallback =
                    new FallbackSequencingPolicy<>(secondPolicy, finalPolicy, ConversionException.class);
            FallbackSequencingPolicy<IllegalArgumentException, EventMessage> firstFallback =
                    new FallbackSequencingPolicy<>(firstPolicy, secondFallback, IllegalArgumentException.class);

            // when
            var result = firstFallback.sequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).hasValue("final-result");
        }
    }

    private EventMessage anEvent(final Object payload) {
        return EventTestUtils.asEventMessage(payload);
    }

    private static ProcessingContext aProcessingContext() {
        return new StubProcessingContext();
    }

    private record TestEvent(String id) {

    }
}