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
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.aggregate.DeleteCommand;
import org.axonframework.test.aggregate.MyAggregateDeletedEvent;
import org.axonframework.test.aggregate.MyEvent;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


class AxonTestFixtureMessagingTest {

    @Nested
    class WhenCommand {

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
                   .events(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
        }

        @Test
        void assertionErrorIfThenNoEventsButThereAreSomeEvents() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsEmpty(configurer);

            var fixture = AxonTestFixture.with(configurer);

            var assertionError = assertThrows(AxonAssertionError.class, () ->
                    fixture.given()
                           .noPriorActivity()
                           .when()
                           .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                           .then()
                           .noEvents()
            );
            assertTrue(assertionError.getMessage().contains("The published events do not match the expected events"));
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
                   .events(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
        }

        @Test
        void assertionErrorIfThenExpectEventsButWithDifferentPayload() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsEmpty(configurer);

            var fixture = AxonTestFixture.with(configurer);

            var assertionError = assertThrows(AxonAssertionError.class, () ->
                    fixture.given()
                           .when()
                           .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                           .then()
                           .events(studentNameChangedEventMessage("my-studentId-1", "name-2", 1))
            );
            assertTrue(assertionError.getMessage()
                                     .contains("One of the messages contained a different payload than expected"));
        }

        @Test
        void whenCommandThenExpectEvents() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsEmpty(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .events(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
        }

        @Test
        void whenCommandThenSuccess() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsEmpty(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .success();
        }

        @Test
        void whenCommandReturnsEmptyThenSuccessWithNullValue() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsEmpty(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .success()
                   .resultMessage(Matchers.nullValue());
        }

        @Test
        void whenCommandReturnsSingleThenSuccessWithValue() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .success()
                   .resultMessagePayload(new CommandResult("Result name-1"));
        }

        @Test
        void whenCommandWithMetaDataThenSuccessWithTheMetaData() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"),
                            MetaData.with("sample", "metaValue"))
                   .then()
                   .success()
                   .resultMessagePayload(new CommandResult("Result name-1", "metaValue"));
        }

        @Test
        void whenCommandWithMetaDataMapThenSuccessWithTheMetaData() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"), Map.of("sample", "metaValue"))
                   .then()
                   .success()
                   .resultMessagePayload(new CommandResult("Result name-1", "metaValue"));
        }

        @Test
        void givenEventsWhenCommandReturnsSingleThenSuccessWithValue() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            var givenEvents = List.of(
                    new StudentNameChangedEvent("my-studentId-1", "name-1", 1),
                    new StudentNameChangedEvent("my-studentId-1", "name-2", 2)
            );
            fixture.given()
                   .events(givenEvents)
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
                   .then()
                   .success()
                   .resultMessagePayload(new CommandResult("Result name-3"));
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
                   .events(studentNameChangedEventMessage("my-studentId-1", "name-1", 2));
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
                   .exception(RuntimeException.class);
        }

        @Test
        void assertionErrorIfExpectExceptionButSuccess() {
            var configurer = MessagingConfigurer.create();
            configurer.registerDecorator(
                    CommandBus.class,
                    0,
                    (c, n, d) -> d.subscribe(
                            new QualifiedName(ChangeStudentNameCommand.class),
                            (command, context) -> MessageStream.empty().cast()
                    )
            );

            var fixture = AxonTestFixture.with(configurer);

            var assertionError = assertThrows(AxonAssertionError.class, () ->
                    fixture.given()
                           .noPriorActivity()
                           .when()
                           .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                           .then()
                           .exception(RuntimeException.class)
            );
            assertTrue(assertionError.getMessage()
                                     .contains("The message handler returned normally, but an exception was expected"));
        }


        @Test
        void assertionErrorIfExpectSuccessButException() {
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

            var assertionError = assertThrows(AxonAssertionError.class, () ->
                    fixture.given()
                           .noPriorActivity()
                           .when()
                           .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                           .then()
                           .success()
            );
            assertTrue(assertionError.getMessage().contains("The message handler threw an unexpected exception"));
        }
    }

    @Nested
    class WhenEvent {

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
                   .commands(new ChangeStudentNameCommand("id", "name"));
        }

        @Test
        void whenEventHandlerFailsThenException() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> (events) -> CompletableFuture.failedFuture(new RuntimeException(
                    "Simulated failure")));

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                   .then()
                   .exception(RuntimeException.class)
                   .noCommands();
        }

        @Test
        void assertionErrorIfExpectSuccessButException() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> (events) -> CompletableFuture.failedFuture(new RuntimeException(
                    "Simulated failure")));

            var fixture = AxonTestFixture.with(configurer);

            var assertionError = assertThrows(AxonAssertionError.class, () ->
                    fixture.when()
                           .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                           .then()
                           .success()
            );
            assertTrue(assertionError.getMessage().contains("The message handler threw an unexpected exception"));
        }

        @Test
        void whenEventHandlerDoesNotFailThenSuccess() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> (events) -> CompletableFuture.completedFuture(null));

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                   .then()
                   .success()
                   .noCommands();
        }

        @Test
        void assertionErrorIfExpectExceptionButSuccess() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> (events) -> CompletableFuture.completedFuture(null));

            var fixture = AxonTestFixture.with(configurer);

            var assertionError = assertThrows(AxonAssertionError.class, () ->
                    fixture.when()
                           .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                           .then()
                           .exception(RuntimeException.class)
            );
            assertTrue(assertionError.getMessage()
                                     .contains("The message handler returned normally, but an exception was expected"));
        }
    }

    @Nested
    class AndChaining {

        @Test
        void whenThenAndWhenThen() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.given()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-2"))
                   .then()
                   .resultMessagePayload(new AxonTestFixtureMessagingTest.CommandResult("Result name-2"))
                   .events(studentNameChangedEvent("my-studentId-1", "name-2", 1))
                   .and()
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
                   .then()
                   .success()
                   .resultMessagePayload(new AxonTestFixtureMessagingTest.CommandResult("Result name-3"));
        }

        @Test
        void chainSameFixturePhaseTwice() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            var then = fixture.given()
                              .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                              .when()
                              .command(new ChangeStudentNameCommand("my-studentId-1", "name-2"))
                              .then()
                              .resultMessagePayload(new AxonTestFixtureMessagingTest.CommandResult("Result name-2"))
                              .events(studentNameChangedEvent("my-studentId-1", "name-2", 1));

            then.and()
                .when()
                .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
                .then()
                .success()
                .events(studentNameChangedEvent("my-studentId-1", "name-3", 1));

            then.and()
                .when()
                .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
                .then()
                .success()
                .events(studentNameChangedEvent("my-studentId-1", "name-3", 1));
        }
    }

    @Nested
    class Customized {

        @Test
        void setupRegisterIgnoredFieldThenShouldPassWithDifferentNameInExpectedEvents() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsEmpty(configurer);

            var fixture = AxonTestFixture.with(
                    configurer,
                    c -> c.registerIgnoredField(StudentNameChangedEvent.class, "name")
            );

            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .events(studentNameChangedEventMessage("my-studentId-1", "another-name", 1));
        }
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

    record CommandResult(String message, String metadataSample) {

        CommandResult(String message) {
            this(message, null);
        }
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
                            var metadataSample = (String) command.getMetaData().get("sample");
                            var eventSink = c.getComponent(EventSink.class);
                            eventSink.publish(context, studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                            var resultMessage = new GenericCommandResultMessage<>(
                                    new MessageType(CommandResult.class),
                                    new CommandResult("Result " + payload.name(), metadataSample));
                            return MessageStream.just(resultMessage);
                        })
        );
    }
}