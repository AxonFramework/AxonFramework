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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

class IsolatingEventSinkTest {

    @Nested
    class Publish {

        @Test
        void addsTestIdMetadataToPublishedEvents() {
            // given
            var delegate = new RecordingEventSink(new SimpleEventBus());
            var stamping = new IsolatingEventSink(delegate, "test-abc");

            // when
            var event = new GenericEventMessage(new MessageType("testEvent"), "payload");
            stamping.publish(null, List.of(event));

            // then
            var recorded = delegate.recorded().getFirst();
            assertThat(recorded.metadata().get(TEST_ID_METADATA_KEY)).isEqualTo("test-abc");
        }

        @Test
        void preservesExistingMetadata() {
            // given
            var delegate = new RecordingEventSink(new SimpleEventBus());
            var stamping = new IsolatingEventSink(delegate, "test-def");

            // when
            var event = new GenericEventMessage(
                    new MessageType("testEvent"), "payload", Map.of("existing", "value")
            );
            stamping.publish(null, List.of(event));

            // then
            var recorded = delegate.recorded().getFirst();
            assertThat(recorded.metadata().get("existing")).isEqualTo("value");
            assertThat(recorded.metadata().get(TEST_ID_METADATA_KEY)).isEqualTo("test-def");
        }

        @Test
        void stampsAllEventsInBatch() {
            // given
            var delegate = new RecordingEventSink(new SimpleEventBus());
            var stamping = new IsolatingEventSink(delegate, "test-batch");

            // when
            var event1 = new GenericEventMessage(new MessageType("event1"), "payload1");
            var event2 = new GenericEventMessage(new MessageType("event2"), "payload2");
            stamping.publish(null, List.of(event1, event2));

            // then
            assertThat(delegate.recorded()).hasSize(2);
            assertThat(delegate.recorded()).allSatisfy(
                    e -> assertThat(e.metadata().get(TEST_ID_METADATA_KEY)).isEqualTo("test-batch")
            );
        }
    }
}
