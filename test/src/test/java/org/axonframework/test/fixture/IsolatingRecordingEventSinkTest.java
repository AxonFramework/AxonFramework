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

package org.axonframework.test.fixture;

import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

class IsolatingRecordingEventSinkTest {

    private RecordingEventSink shared;

    @BeforeEach
    void setUp() {
        shared = new RecordingEventSink(new SimpleEventBus());
    }

    @Nested
    class Recorded {

        @Test
        void filtersEventsByTestId() {
            // given
            var filterA = new IsolatingRecordingEventSink(shared, "test-A");

            var eventA = new GenericEventMessage(
                    new MessageType("event"), "payloadA",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );
            var eventB = new GenericEventMessage(
                    new MessageType("event"), "payloadB",
                    Map.of(TEST_ID_METADATA_KEY, "test-B")
            );

            // when
            shared.publish(null, List.of(eventA, eventB));

            // then
            assertThat(filterA.recorded()).hasSize(1);
            assertThat(filterA.recorded().getFirst().payload()).isEqualTo("payloadA");
        }

        @Test
        void returnsEmptyWhenNoMatchingEvents() {
            // given
            var filterC = new IsolatingRecordingEventSink(shared, "test-C");

            var eventA = new GenericEventMessage(
                    new MessageType("event"), "payload",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );

            // when
            shared.publish(null, List.of(eventA));

            // then
            assertThat(filterC.recorded()).isEmpty();
        }

        @Test
        void returnsAllEventsForMatchingTestId() {
            // given
            var filterA = new IsolatingRecordingEventSink(shared, "test-A");

            var event1 = new GenericEventMessage(
                    new MessageType("event1"), "payload1",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );
            var event2 = new GenericEventMessage(
                    new MessageType("event2"), "payload2",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );

            // when
            shared.publish(null, List.of(event1, event2));

            // then
            assertThat(filterA.recorded()).hasSize(2);
        }
    }

    @Nested
    class Reset {

        @Test
        void delegatesResetToShared() {
            // given
            var filterA = new IsolatingRecordingEventSink(shared, "test-A");
            var event = new GenericEventMessage(
                    new MessageType("event"), "payload",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );
            shared.publish(null, List.of(event));

            // when
            filterA.reset();

            // then
            assertThat(shared.recorded()).isEmpty();
            assertThat(filterA.recorded()).isEmpty();
        }
    }
}
