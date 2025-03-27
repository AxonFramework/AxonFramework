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
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


class AxonTestFixtureMessagingTest {

    @Test
    void givenNoPriorActivityWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsEmpty(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.given()
               .noPriorActivity()
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .publishedEvents()
               .allOf(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
    }

    @Test
    void givenNothingWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsEmpty(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.given()
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .publishedEvents()
               .allOf(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
    }

    @Test
    void whenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsEmpty(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .publishedEvents()
               .allOf(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
    }

    @Test
    void whenCommandThenSuccess() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsEmpty(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .commandResult()
               .isSuccess();
    }

    @Test
    void whenCommandReturnsEmptyThenSuccessWithNullValue() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsEmpty(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .commandResult()
               .isMessage(Matchers.nullValue());
    }

    @Test
    void whenCommandReturnsSingleThenSuccessWithValue() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsSingle(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .commandResult()
               .isSuccess()
               .withPayload(new CommandResult("Result name-1"));
    }

    @Test
    void chainingWhenThen() {
        var configurer = MessagingConfigurer.create();
        registerChangeStudentNameHandlerReturnsSingle(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-2"))
               .then()
               .commandResult()
               .withPayload(new CommandResult("Result name-2"))
               .then()
               .publishedEvents()
               .allOf(
                       studentNameChangedEvent("my-studentId-1", "name-1", 1),
                       studentNameChangedEvent("my-studentId-1", "name-2", 1)
               ).and()
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
               .then()
               .commandResult()
               .isSuccess()
               .withPayload(new CommandResult("Result name-3"));
    }

    @Test
    void givenEventWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        var studentEvents = new ArrayList<>();
        configurer.registerEventSink(c -> (events) -> {
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
                                                      studentNameChangedEventMessage(payload.id(), payload.name(),
                                                                                     changeNo));
                                    return MessageStream.empty().cast();
                                }));

        var fixture = AxonTestFixture.with(configurer);

        fixture.given()
               .event(studentNameChangedEventMessage("my-studentId-1", "name-1", 1))
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .publishedEvents()
               .allOf(studentNameChangedEventMessage("my-studentId-1", "name-1", 2));
    }

    @Test
    void whenEventThenExpectedCommand() {
        var configurer = MessagingConfigurer.create();
        AtomicBoolean eventHandled = new AtomicBoolean(false);
        registerChangeStudentNameHandlerReturnsSingle(configurer);
        configurer.registerEventSink(c -> (events) -> {
            if (!eventHandled.getAndSet(true)) {
                var commandGateway = c.getComponent(CommandGateway.class);
                commandGateway.sendAndWait(new ChangeStudentNameCommand("id", "name"));
            }
            return CompletableFuture.completedFuture(null);
        });

        var fixture = AxonTestFixture.with(configurer);

        fixture.when()
               .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
               .then()
               .dispatchedCommands()
               .commands(new ChangeStudentNameCommand("id", "name"));
    }

    @Test
    void givenNoPriorActivityWhenCommandThenExpectException() {
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

        fixture.given()
               .noPriorActivity()
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .commandResult()
               .isException(RuntimeException.class);
    }

    private static GenericEventMessage<StudentNameChangedEvent> studentNameChangedEventMessage(
            String id,
            String name,
            int change
    ) {
        return new GenericEventMessage<>(
                new MessageType(StudentNameChangedEvent.class),
                studentNameChangedEvent(id, name, change)
        );
    }

    private static StudentNameChangedEvent studentNameChangedEvent(String id, String name, int change) {
        return new StudentNameChangedEvent(id, name, change);
    }

    record CommandResult(String message) {

    }

    private static void registerChangeStudentNameHandlerReturnsEmpty(MessagingConfigurer configurer) {
        configurer.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            var eventSink = c.getComponent(EventSink.class);
                            eventSink.publish(context,
                                              studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                            return MessageStream.empty().cast();
                        })
        );
    }

    private static void registerChangeStudentNameHandlerReturnsSingle(MessagingConfigurer configurer) {
        configurer.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            var eventSink = c.getComponent(EventSink.class);
                            eventSink.publish(context,
                                              studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                            return MessageStream.just(new GenericCommandResultMessage<>(new MessageType(CommandResult.class),
                                                                                        new CommandResult("Result "
                                                                                                                  + payload.name())));
                        })
        );
    }
}