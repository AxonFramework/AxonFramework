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

package org.axonframework.test.af5;

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.test.af5.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.af5.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;


class CommandModelTestFixtureTest {

    private static final String TEST_CONTEXT = "TEST_CONTEXT";

    @Test
    void test() {
        var configurer = MessagingConfigurer.create();
        configurer.registerCommandBus(c -> new SimpleCommandBus()
                .subscribe(new QualifiedName(ChangeStudentNameCommand.class), (command, context) -> {
                    ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                    var eventSink = c.getComponent(EventSink.class);
                    eventSink.publish(context, TEST_CONTEXT, new GenericEventMessage<>(
                            new MessageType(StudentNameChangedEvent.class),
                            new StudentNameChangedEvent(payload.id(), payload.name())
                    ));
                    return MessageStream.empty().cast();
                })
        );

        var fixture = CommandModelTestFixture.with(configurer);
        var command = new ChangeStudentNameCommand("my-studentId-1", "name-1");
        fixture.givenNoPriorActivity()
               .when(command)
               .expectEvents(new GenericEventMessage<>(
                       new MessageType(StudentNameChangedEvent.class),
                       new StudentNameChangedEvent("my-studentId-2", "name-1")
               ));
    }
}