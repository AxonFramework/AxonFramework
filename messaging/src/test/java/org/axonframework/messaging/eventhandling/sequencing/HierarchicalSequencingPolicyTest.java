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

package org.axonframework.messaging.eventhandling.sequencing;

import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link HierarchicalSequencingPolicy}.
 *
 * @author Mateusz Nowak
 */
final class HierarchicalSequencingPolicyTest {

    @Nested
    class Construction {

        @Test
        void shouldThrowNullPointerExceptionWhenPrimaryIsNull() {
            // given
            SequencingPolicy secondary = (event, context) -> Optional.of("secondary");

            // when / then
            assertThrows(NullPointerException.class, () -> new HierarchicalSequencingPolicy(null, secondary));
        }

        @Test
        void shouldThrowNullPointerExceptionWhenSecondaryIsNull() {
            // given
            SequencingPolicy primary = (event, context) -> Optional.of("primary");

            // when / then
            assertThrows(NullPointerException.class, () -> new HierarchicalSequencingPolicy(primary, null));
        }
    }

    @Nested
    class SequenceIdentification {

        @Test
        void shouldUsePrimaryWhenPrimarySucceeds() {
            // given
            var expectedIdentifier = "primary-result";
            SequencingPolicy primary = (event, context) -> Optional.of(expectedIdentifier);
            SequencingPolicy secondary = (event, context) -> Optional.of("secondary-result");
            HierarchicalSequencingPolicy policy = new HierarchicalSequencingPolicy(primary, secondary);

            // when
            var result = policy.getSequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).hasValue(expectedIdentifier);
        }

        @Test
        void shouldUseSecondaryWhenPrimaryReturnsEmpty() {
            // given
            var expectedIdentifier = "secondary-result";
            SequencingPolicy primary = (event, context) -> Optional.empty();
            SequencingPolicy secondary = (event, context) -> Optional.of(expectedIdentifier);
            HierarchicalSequencingPolicy policy = new HierarchicalSequencingPolicy(primary, secondary);

            // when
            var result = policy.getSequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).hasValue(expectedIdentifier);
        }

        @Test
        void shouldReturnEmptyWhenBothReturnEmpty() {
            // given
            SequencingPolicy primary = (event, context) -> Optional.empty();
            SequencingPolicy secondary = (event, context) -> Optional.empty();
            HierarchicalSequencingPolicy policy = new HierarchicalSequencingPolicy(primary, secondary);

            // when
            var result = policy.getSequenceIdentifierFor(anEvent("test"), aProcessingContext());

            // then
            assertThat(result).isEmpty();
        }
    }

    private EventMessage anEvent(final Object payload) {
        return EventTestUtils.asEventMessage(payload);
    }

    private static ProcessingContext aProcessingContext() {
        return new StubProcessingContext();
    }
}