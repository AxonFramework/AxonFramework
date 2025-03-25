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

package org.axonframework.test.fixture;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;


class AxonTestFixtureMessagingTest {

    private static final String TEST_CONTEXT = "TEST_CONTEXT";

    @Test
    void givenNoPriorActivityWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        configurer.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            var eventSink = c.getComponent(EventSink.class);
                            eventSink.publish(context,
                                              TEST_CONTEXT,
                                              studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                            return MessageStream.empty().cast();
                        })
        );

        var fixture = AxonTestFixture.with(configurer);

        fixture.givenNoPriorActivity()
               .when(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .expectEvents(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
    }

    @Test
    void givenEventsWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        var studentEvents = new ArrayList<>();
        configurer.registerEventSink(c -> (context, events) -> {
            studentEvents.addAll(events);
            return CompletableFuture.completedFuture(null);
        });
        configurer.registerCommandBus(
                c -> new SimpleCommandBus()
                        .subscribe(
                                new QualifiedName(ChangeStudentNameCommand.class),
                                (command, context) -> {
                                    ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                                    var eventSink = c.getComponent(EventSink.class);
                                    var changeNo = studentEvents.size() + 1;
                                    eventSink.publish(context,
                                                      TEST_CONTEXT,
                                                      studentNameChangedEventMessage(payload.id(), payload.name(),
                                                                                     changeNo));
                                    return MessageStream.empty().cast();
                                }));

        var fixture = AxonTestFixture.with(configurer);

        fixture.givenEvents(studentNameChangedEventMessage("my-studentId-1", "name-1", 1))
               .when(new ChangeStudentNameCommand("my-studentId-1", "name-1")).expectEvents(
                       studentNameChangedEventMessage("my-studentId-1", "name-1", 2));
    }

    @Test
    void giveNoPriorActivityWhenCommandThenExpectException() {
        var configurer = MessagingConfigurer.create();
        configurer.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> MessageStream.failed(new RuntimeException("Test"))
                )
        );

        var fixture = AxonTestFixture.with(configurer);

        fixture.givenNoPriorActivity()
               .when(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .expectException(RuntimeException.class);
    }

    private static GenericEventMessage<StudentNameChangedEvent> studentNameChangedEventMessage(
            String id,
            String name,
            int change
    ) {
        return new GenericEventMessage<>(
                new MessageType(StudentNameChangedEvent.class),
                new StudentNameChangedEvent(id, name, change)
        );
    }
}