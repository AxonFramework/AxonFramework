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

import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link SegmentMatcher}.
 *
 * @author Mateusz Nowak
 */
class SegmentMatcherTest {

    private static final String TEST_AGGREGATE_ID = "aggregateId";
    private SequencingPolicy<EventMessage<?>> sequencingPolicy;
    private SegmentMatcher testSubject;

    @BeforeEach
    void setUp() {
        sequencingPolicy = mock(SequencingPolicy.class);
        testSubject = new SegmentMatcher(sequencingPolicy);
    }

    @Test
    void matchesReturnsTrueWhenSegmentMatchesEventBasedOnSequenceIdentifier() {
        // given
        EventMessage<?> testMessage = EventTestUtils.asEventMessage("test-payload");
        Segment segment = new Segment(0, 0); // Root segment matches everything
        when(sequencingPolicy.getSequenceIdentifierFor(testMessage)).thenReturn(TEST_AGGREGATE_ID);

        // when
        boolean result = testSubject.matches(segment, testMessage);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void usesEventMessageIdentifierAsSequenceIdentifierWhenPolicyReturnsNull() {
        // given
        String messageId = UUID.randomUUID().toString();
        MessageType messageType = new MessageType(new QualifiedName(String.class));
        EventMessage<?> testMessage = EventTestUtils.asEventMessage(
                new GenericEventMessage<>(messageId, messageType, "test-payload", MetaData.emptyInstance(), Instant.now()));
        Segment segment = Segment.ROOT_SEGMENT; // Matches everything
        when(sequencingPolicy.getSequenceIdentifierFor(testMessage)).thenReturn(null);

        // when
        boolean result = testSubject.matches(segment, testMessage);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void getSequencingPolicyReturnsConfiguredPolicy() {
        // when
        SequencingPolicy<? super EventMessage<?>> result = testSubject.getSequencingPolicy();

        // then
        assertThat(result).isSameAs(sequencingPolicy);
    }
}
