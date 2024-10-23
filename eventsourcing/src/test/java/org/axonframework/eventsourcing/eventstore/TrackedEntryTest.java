/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.ContextTestSuite;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link TrackedEntry}.
 *
 * @author Steven van Beelen
 */
class TrackedEntryTest extends ContextTestSuite<TrackedEntry<?>> {

    private static final EventMessage<Object> TEST_MESSAGE = GenericEventMessage.asEventMessage("some-event");
    private static final GlobalSequenceTrackingToken TEST_TOKEN = new GlobalSequenceTrackingToken(42L);

    @Override
    public TrackedEntry<EventMessage<?>> testSubject() {
        return new TrackedEntry<>(TEST_MESSAGE, TEST_TOKEN);
    }

    @Test
    void containsExpectedData() {
        MessageStream.Entry<EventMessage<?>> testSubject = testSubject();

        assertEquals(TEST_MESSAGE, testSubject.message());
        Optional<TrackingToken> optionalToken = TrackingToken.fromContext(testSubject);
        assertTrue(optionalToken.isPresent());
        assertEquals(TEST_TOKEN, optionalToken.get());
    }

    @Test
    void mapsContainedMessageAndContextAsExpected() {
        MetaData expectedMetaData = MetaData.from(Map.of("key", "value"));

        MessageStream.Entry<EventMessage<?>> testSubject = testSubject();

        MessageStream.Entry<EventMessage<?>> result = testSubject.map(message -> message.withMetaData(expectedMetaData));

        assertNotEquals(TEST_MESSAGE, result.message());
        assertEquals(expectedMetaData, result.message().getMetaData());
        Optional<TrackingToken> optionalToken = TrackingToken.fromContext(testSubject);
        assertTrue(optionalToken.isPresent());
        assertEquals(TEST_TOKEN, optionalToken.get());
    }
}