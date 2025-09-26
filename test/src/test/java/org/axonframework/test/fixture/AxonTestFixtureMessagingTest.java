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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

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
                   .resultMessageSatisfies(Assertions::assertNull);
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
        void whenCommandReturnsSingleThenSuccessWithValueConsumer() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .resultMessagePayloadSatisfies(result -> {
                       var payload = (CommandResult) result;
                       assertEquals("Result name-1", payload.message());
                       assertNull(payload.metadataSample());
                   }).success();
        }

        @Test
        void whenCommandWithMetadataThenSuccessWithTheMetadata() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"),
                            Metadata.with("sample", "metaValue"))
                   .then()
                   .success()
                   .resultMessagePayload(new CommandResult("Result name-1", "metaValue"));
        }

        @Test
        void whenCommandWithMetadataMapThenSuccessWithTheMetadata() {
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
        void whenCommandWithMetadataMapThenSuccessWithTheMetadataSatisfies() {
            var configurer = MessagingConfigurer.create();
            registerChangeStudentNameHandlerReturnsSingle(configurer);

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"), Map.of("sample", "metaValue"))
                   .then()
                   .success()
                   .resultMessageSatisfies(c -> assertEquals(new CommandResult("Result name-1", "metaValue"),
                                                             c.payload()));
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
            configurer.registerEventSink(c -> new TestEventSink((context, events) -> {
                studentEvents.addAll(events);
                return FutureUtils.emptyCompletedFuture();
            }));
            configurer.registerCommandBus(
                    c -> aCommandBus()
                            .subscribe(
                                    new QualifiedName(ChangeStudentNameCommand.class),
                                    (command, context) -> {
                                        ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.payload();
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

        @Nonnull
        private static SimpleCommandBus aCommandBus() {
            return new SimpleCommandBus(new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE),
                                        Collections.emptyList());
        }

        @Test
        void givenNoPriorActivityWhenCommandThenExpectException() {
            var configurer = MessagingConfigurer.create();
            configurer.componentRegistry(cr -> cr.registerDecorator(
                    CommandBus.class,
                    0,
                    (c, n, d) -> d.subscribe(
                            new QualifiedName(ChangeStudentNameCommand.class),
                            (command, context) -> MessageStream.failed(new RuntimeException("Test"))
                    )
            ));

            var fixture = AxonTestFixture.with(configurer);

            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .exception(RuntimeException.class)
                   .exceptionSatisfies(thrown -> assertEquals("Test", thrown.getMessage()));
        }

        @Test
        void givenNoPriorActivityWhenCommandHandlerThrowsThenExpectException() {
            var configurer = MessagingConfigurer.create();
            configurer.componentRegistry(cr -> cr.registerDecorator(
                    CommandBus.class,
                    0,
                    (c, n, d) -> d.subscribe(
                            new QualifiedName(ChangeStudentNameCommand.class),
                            (command, context) -> {
                                throw new RuntimeException("Simulated exception");
                            }
                    )
            ));

            var fixture = AxonTestFixture.with(configurer);

            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .then()
                   .exception(RuntimeException.class)
                   .exceptionSatisfies(thrown -> assertEquals("Simulated exception", thrown.getMessage()));
        }

        @Nested
        class AssertionErrors {

            @Test
            void ifThenNoEventsButThereAreSomeEvents() {
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
                assertTrue(assertionError.getMessage()
                                         .contains("The published events do not match the expected events"));
            }


            @Test
            void ifThenExpectEventsButWithDifferentPayload() {
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
            void ifExpectExceptionButSuccess() {
                var configurer = MessagingConfigurer.create();
                configurer.componentRegistry(cr -> cr.registerDecorator(
                        CommandBus.class,
                        0,
                        (c, n, d) -> d.subscribe(
                                new QualifiedName(ChangeStudentNameCommand.class),
                                (command, context) -> MessageStream.empty().cast()
                        )
                ));

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
                                         .contains(
                                                 "The message handler returned normally, but an exception was expected"));
            }

            @Test
            void ifExpectSuccessButException() {
                var configurer = MessagingConfigurer.create();
                configurer.componentRegistry(cr -> cr.registerDecorator(
                        CommandBus.class,
                        0,
                        (c, n, d) -> d.subscribe(
                                new QualifiedName(ChangeStudentNameCommand.class),
                                (command, context) -> MessageStream.failed(new RuntimeException("Test"))
                        )
                ));

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
    }

    @Nested
    class WhenEvent {

        @Test
        void whenEventThenExpectedCommand() {
            var configurer = MessagingConfigurer.create();
            AtomicBoolean eventHandled = new AtomicBoolean(false);
            registerChangeStudentNameHandlerReturnsSingle(configurer);
            configurer.registerEventSink(c -> new TestEventSink((context, events) -> {
                if (!eventHandled.getAndSet(true)) {
                    var commandGateway = c.getComponent(CommandGateway.class);
                    commandGateway.sendAndWait(new ChangeStudentNameCommand("id", "name"));
                }
                return FutureUtils.emptyCompletedFuture();
            }));

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                   .then()
                   .commands(new ChangeStudentNameCommand("id", "name"));
        }

        @Test
        void whenEventThenExpectedCommandCustomAssertion() {
            var configurer = MessagingConfigurer.create();
            AtomicBoolean eventHandled = new AtomicBoolean(false);
            registerChangeStudentNameHandlerReturnsSingle(configurer);
            configurer.registerEventSink(c -> new TestEventSink((context, events) -> {
                if (!eventHandled.getAndSet(true)) {
                    var commandGateway = c.getComponent(CommandGateway.class);
                    commandGateway.sendAndWait(new ChangeStudentNameCommand("id", "name"));
                }
                return FutureUtils.emptyCompletedFuture();
            }));

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                   .then()
                   .commandsSatisfy(commands -> {
                       assertEquals(1, commands.size());
                       var command = commands.getFirst();
                       assertEquals("id", ((ChangeStudentNameCommand) command.payload()).id());
                       assertEquals("name", ((ChangeStudentNameCommand) command.payload()).name());
                   }).commandsMatch(commands -> !commands.isEmpty())
                   .success();
        }

        @Test
        void whenEventHandlerFailsThenException() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> new TestEventSink(
                    CompletableFuture.failedFuture(new RuntimeException("Simulated failure"))
            ));

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                   .then()
                   .exception(RuntimeException.class)
                   .noCommands();
        }

        @Test
        void whenEventHandlerDoesNotFailThenSuccess() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> new TestEventSink(FutureUtils.emptyCompletedFuture()));

            var fixture = AxonTestFixture.with(configurer);

            fixture.when()
                   .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                   .then()
                   .noCommands()
                   .success();
        }

        @Test
        void whenEventsHandlerDoesNotFailThenSuccess() {
            var configurer = MessagingConfigurer.create();
            configurer.registerEventSink(c -> new TestEventSink(FutureUtils.emptyCompletedFuture()));

            var fixture = AxonTestFixture.with(configurer);

            var whenEvents = List.of(
                    new StudentNameChangedEvent("my-studentId-1", "name-1", 1),
                    new StudentNameChangedEvent("my-studentId-1", "name-2", 2)
            );
            fixture.when()
                   .events(whenEvents)
                   .then()
                   .success()
                   .noCommands();
        }

        @Nested
        class AssertionErrors {

            @Test
            void ifExpectSuccessButException() {
                var configurer = MessagingConfigurer.create();
                configurer.registerEventSink(c -> new TestEventSink(
                        CompletableFuture.failedFuture(new RuntimeException("Simulated failure"))
                ));

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
            void ifExpectExceptionButSuccess() {
                var configurer = MessagingConfigurer.create();
                configurer.registerEventSink(c -> new TestEventSink(FutureUtils.emptyCompletedFuture()));

                var fixture = AxonTestFixture.with(configurer);

                var assertionError = assertThrows(AxonAssertionError.class, () ->
                        fixture.when()
                               .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                               .then()
                               .exception(RuntimeException.class)
                );
                assertTrue(assertionError.getMessage()
                                         .contains(
                                                 "The message handler returned normally, but an exception was expected"));
            }

            @Test
            void whenEventHandlerFailsThenException() {
                var configurer = MessagingConfigurer.create();
                configurer.registerEventSink(c -> new TestEventSink(
                        CompletableFuture.failedFuture(new IllegalStateException("Simulated failure"))
                ));

                var fixture = AxonTestFixture.with(configurer);

                var assertionError = assertThrows(AxonAssertionError.class, () ->
                        fixture.when()
                               .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
                               .then()
                               .exception(IllegalStateException.class, "Simulated exception")
                );
                assertTrue(assertionError.getMessage().contains(
                        "Expected class java.lang.IllegalStateException with message 'Simulated exception' but got java.lang.IllegalStateException: Simulated failure"));
            }
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

    private static GenericEventMessage studentNameChangedEventMessage(
            String id,
            String name,
            int change
    ) {
        return new GenericEventMessage(
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
        configurer.componentRegistry(cr -> cr.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.payload();
                            var eventSink = c.getComponent(EventSink.class);
                            eventSink.publish(context,
                                              studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                            return MessageStream.empty().cast();
                        })
        ));
    }

    private static void registerChangeStudentNameHandlerReturnsSingle(MessagingConfigurer configurer) {
        configurer.componentRegistry(cr -> cr.registerDecorator(
                CommandBus.class,
                0,
                (c, n, d) -> d.subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.payload();
                            var metadataSample = command.metadata().get("sample");
                            var eventSink = c.getComponent(EventSink.class);
                            eventSink.publish(context, studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                            var resultMessage = new GenericCommandResultMessage(
                                    new MessageType(CommandResult.class),
                                    new CommandResult("Result " + payload.name(), metadataSample));
                            return MessageStream.just(resultMessage);
                        })
        ));
    }

    private record TestEventSink(
            BiFunction<ProcessingContext, List<EventMessage>, CompletableFuture<Void>> publishFunction
    ) implements EventSink {

        public TestEventSink(CompletableFuture<Void> publishResult) {
            this((context, events) -> publishResult);
        }

        @Override
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                               @Nonnull List<EventMessage> events) {
            return publishFunction.apply(context, events);
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            throw new UnsupportedOperationException("Unimportant for this test case");
        }
    }
}