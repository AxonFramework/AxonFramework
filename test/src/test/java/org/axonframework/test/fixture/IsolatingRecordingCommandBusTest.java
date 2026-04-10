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

import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.SimpleCommandBus;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

class IsolatingRecordingCommandBusTest {

    private RecordingCommandBus shared;

    @BeforeEach
    void setUp() {
        shared = new RecordingCommandBus(
                new SimpleCommandBus(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE))
        );
        shared.subscribe(
                new QualifiedName("testCommand"),
                (command, context) -> MessageStream.empty().cast()
        );
    }

    @Nested
    class Recorded {

        @Test
        void filtersCommandsByTestId() {
            // given
            var filterA = new IsolatingRecordingCommandBus(shared, "test-A");

            var commandA = new GenericCommandMessage(
                    new MessageType("testCommand"), "payloadA",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );
            var commandB = new GenericCommandMessage(
                    new MessageType("testCommand"), "payloadB",
                    Map.of(TEST_ID_METADATA_KEY, "test-B")
            );

            // when
            shared.dispatch(commandA, null).join();
            shared.dispatch(commandB, null).join();

            // then
            assertThat(filterA.recorded()).hasSize(1);
            assertThat(filterA.recorded().keySet().iterator().next().payload()).isEqualTo("payloadA");
        }

        @Test
        void returnsEmptyWhenNoMatchingCommands() {
            // given
            var filterC = new IsolatingRecordingCommandBus(shared, "test-C");

            var commandA = new GenericCommandMessage(
                    new MessageType("testCommand"), "payload",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );

            // when
            shared.dispatch(commandA, null).join();

            // then
            assertThat(filterC.recorded()).isEmpty();
        }
    }

    @Nested
    class RecordedCommands {

        @Test
        void filtersCommandsByTestId() {
            // given
            var filterA = new IsolatingRecordingCommandBus(shared, "test-A");

            var commandA = new GenericCommandMessage(
                    new MessageType("testCommand"), "payloadA",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );
            var commandB = new GenericCommandMessage(
                    new MessageType("testCommand"), "payloadB",
                    Map.of(TEST_ID_METADATA_KEY, "test-B")
            );

            // when
            shared.dispatch(commandA, null).join();
            shared.dispatch(commandB, null).join();

            // then
            assertThat(filterA.recordedCommands()).hasSize(1);
            assertThat(filterA.recordedCommands().getFirst().payload()).isEqualTo("payloadA");
        }
    }

    @Nested
    class Reset {

        @Test
        void delegatesResetToShared() {
            // given
            var filterA = new IsolatingRecordingCommandBus(shared, "test-A");
            var command = new GenericCommandMessage(
                    new MessageType("testCommand"), "payload",
                    Map.of(TEST_ID_METADATA_KEY, "test-A")
            );
            shared.dispatch(command, null).join();

            // when
            filterA.reset();

            // then
            assertThat(shared.recordedCommands()).isEmpty();
            assertThat(filterA.recordedCommands()).isEmpty();
        }
    }
}
