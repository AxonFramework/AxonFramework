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

package org.axonframework.eventhandling;

import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link SegmentMatcher}.
 *
 * @author Mateusz Nowak
 */
class SegmentMatcherTest {

    @Test
    void matchesReturnsTrueWhenSegmentMatchesEventBasedOnSequenceIdentifier() {
        // given
        SegmentMatcher testSubject = new SegmentMatcher(message -> Optional.of("sample-identifier"));
        EventMessage<?> testMessage = EventTestUtils.asEventMessage("test-payload");
        Segment segment = new Segment(0, 0); // Root segment matches everything

        // when
        boolean result = testSubject.matches(segment, testMessage);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void usesEventMessageIdentifierAsSequenceIdentifierWhenPolicyReturnsNull() {
        // given
        SegmentMatcher testSubject = new SegmentMatcher(message -> Optional.empty());
        String messageId = UUID.randomUUID().toString();
        MessageType messageType = new MessageType(new QualifiedName(String.class));
        EventMessage<?> testMessage = EventTestUtils.asEventMessage(
                new GenericEventMessage<>(messageId,
                                          messageType,
                                          "test-payload",
                                          MetaData.emptyInstance(),
                                          Instant.now()));
        Segment segment = Segment.ROOT_SEGMENT; // Matches everything

        // when
        boolean result = testSubject.matches(segment, testMessage);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void matchesReturnsFalseWhenSegmentDoesNotMatchEventBasedOnSequenceIdentifier() {
        // given
        Segment segmentEven = new Segment(1, 1); // Will match events with odd hash
        String sequenceId = "even"; // "even" has a hash code of 3021508, which is even
        SegmentMatcher testSubject = new SegmentMatcher(message -> Optional.of(sequenceId));
        EventMessage<?> oddMessage = EventTestUtils.asEventMessage("test-payload");

        // when
        boolean result = testSubject.matches(segmentEven, oddMessage);

        // then
        assertThat(result).isFalse();
    }
}