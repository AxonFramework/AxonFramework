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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

class IsolatingCommandBusTest {

    @Nested
    class Dispatch {

        @Test
        void addsTestIdMetadataToDispatchedCommand() {
            // given
            var delegate = new RecordingCommandBus(
                    new SimpleCommandBus(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE))
            );
            delegate.subscribe(
                    new QualifiedName("testCommand"),
                    (command, context) -> MessageStream.empty().cast()
            );
            var stamping = new IsolatingCommandBus(delegate, "test-123");

            // when
            var command = new GenericCommandMessage(new MessageType("testCommand"), "payload");
            stamping.dispatch(command, null);

            // then
            var recorded = delegate.recordedCommands().getFirst();
            assertThat(recorded.metadata().get(TEST_ID_METADATA_KEY)).isEqualTo("test-123");
        }

        @Test
        void preservesExistingMetadata() {
            // given
            var delegate = new RecordingCommandBus(
                    new SimpleCommandBus(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE))
            );
            delegate.subscribe(
                    new QualifiedName("testCommand"),
                    (command, context) -> MessageStream.empty().cast()
            );
            var stamping = new IsolatingCommandBus(delegate, "test-456");

            // when
            var command = new GenericCommandMessage(
                    new MessageType("testCommand"), "payload", Map.of("existing", "value")
            );
            stamping.dispatch(command, null);

            // then
            var recorded = delegate.recordedCommands().getFirst();
            assertThat(recorded.metadata().get("existing")).isEqualTo("value");
            assertThat(recorded.metadata().get(TEST_ID_METADATA_KEY)).isEqualTo("test-456");
        }
    }

    @Nested
    class Subscribe {

        @Test
        void delegatesSubscription() {
            // given
            var delegate = new RecordingCommandBus(
                    new SimpleCommandBus(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE))
            );
            var stamping = new IsolatingCommandBus(delegate, "test-789");

            // when
            stamping.subscribe(
                    new QualifiedName("testCommand"),
                    (command, context) -> MessageStream.empty().cast()
            );

            // then — dispatch through delegate succeeds (handler was subscribed)
            var command = new GenericCommandMessage(new MessageType("testCommand"), "payload");
            stamping.dispatch(command, null);
            assertThat(delegate.recordedCommands()).hasSize(1);
        }
    }
}
