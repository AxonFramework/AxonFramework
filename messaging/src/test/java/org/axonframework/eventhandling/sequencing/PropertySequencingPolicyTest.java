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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PropertySequencingPolicy}.
 *
 * @author Nils Christian Ehmke
 */
@DisplayName("Unit-Test for the PropertySequencingPolicy")
final class PropertySequencingPolicyTest {

    @Test
    void propertyExtractorShouldReadCorrectValue() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyExtractor(TestEvent::id)
                .build();

        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("42")),
                aProcessingContext())
        ).hasValue("42");
    }

    @Test
    void propertyShouldReadCorrectValue() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("id")
                .build();

        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent(new TestEvent("42")),
                aProcessingContext())
        ).hasValue("42");
    }

    @Test
    void defaultFallbackShouldThrowException() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("id")
                .build();

        assertThrows(IllegalArgumentException.class,
                     () -> sequencingPolicy.getSequenceIdentifierFor(anEvent("42"), aProcessingContext()));
    }

    @Test
    void fallbackShouldBeApplied() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("id")
                .fallbackSequencingPolicy((event, context) -> Optional.of("A"))
                .build();

        assertThat(sequencingPolicy.getSequenceIdentifierFor(
                anEvent("42"),
                aProcessingContext())
        ).hasValue("A");
    }

    private EventMessage anEvent(final Object payload) {
        return EventTestUtils.asEventMessage(payload);
    }

    private static StubProcessingContext aProcessingContext() {
        return StubProcessingContext.withComponent(
                EventConverter.class,
                new DelegatingEventConverter(new JacksonConverter())
        );
    }

    private record TestEvent(String id) {

    }
}
