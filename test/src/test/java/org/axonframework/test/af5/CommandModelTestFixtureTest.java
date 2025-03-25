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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.test.af5.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.af5.sampledomain.Student;
import org.axonframework.test.af5.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;


class CommandModelTestFixtureTest {

    private static final String TEST_CONTEXT = "TEST_CONTEXT";

    @Test
    void givenNoPriorActivityWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        configurer.registerCommandBus(c -> new SimpleCommandBus()
                .subscribe(new QualifiedName(ChangeStudentNameCommand.class), (command, context) -> {
                    ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                    var eventSink = c.getComponent(EventSink.class);
                    eventSink.publish(context,
                                      TEST_CONTEXT,
                                      studentNameChangedEventMessage(payload.id(), payload.name(), 1));
                    return MessageStream.empty().cast();
                })
        );

        var fixture = CommandModelTestFixture.with(configurer);

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
        configurer.registerCommandBus(c -> new SimpleCommandBus()
                .subscribe(new QualifiedName(ChangeStudentNameCommand.class), (command, context) -> {
                    ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                    var eventSink = c.getComponent(EventSink.class);
                    eventSink.publish(context,
                                      TEST_CONTEXT,
                                      studentNameChangedEventMessage(payload.id(),
                                                                     payload.name(),
                                                                     studentEvents.size() + 1));
                    return MessageStream.empty().cast();
                })
        );

        var fixture = CommandModelTestFixture.with(configurer);

        fixture.givenEvents(studentNameChangedEventMessage("my-studentId-1", "name-1", 1))
               .when(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .expectEvents(studentNameChangedEventMessage("my-studentId-1", "name-1", 2));
    }

    @Disabled
    @Test
    void giveNoPriorActivityWhenCommandThenExpectException() {
        var configurer = MessagingConfigurer.create();
        configurer.registerCommandBus(c -> new SimpleCommandBus()
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, context) -> MessageStream.failed(new RuntimeException("Test"))
                )
        );

        var fixture = CommandModelTestFixture.with(configurer);

        fixture.givenNoPriorActivity()
               .when(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .expectException(RuntimeException.class);
    }

    @Nested
    class StatefulCommandHandler {

        @Test
        void statefulTest() {
            var configurer = MessagingConfigurer.create();
            configurer.registerComponent(StateManager.class, c -> SimpleStateManager
                    .builder("testfixture")
                    .register(new AsyncEventSourcingRepository<>(
                            String.class,
                            Student.class,
                            c.getComponent(AsyncEventStore.class),
                            id -> EventCriteria.match().eventsOfAnyType().withTags("Student", id),
                            new AnnotationBasedEventStateApplier<>(Student.class),
                            Student::new,
                            TEST_CONTEXT
                    ))
                    .build());

            configurer.registerComponent(AsyncEventStore.class, c -> {
                          return new SimpleEventStore(
                                  new AsyncInMemoryEventStorageEngine(),
                                  TEST_CONTEXT,
                                  new AnnotationBasedTagResolver()
                          );
                      })
                      .registerComponent(EventSink.class, c -> {
                          return c.getComponent(AsyncEventStore.class);
                      });

            configurer.registerDecorator(CommandBus.class, 50, (c, name, delegate) -> {
                var stateManager = c.getComponent(StateManager.class);
                var chc = StatefulCommandHandlingComponent.create("mystatefulCH", stateManager)
                                                          .subscribe(new QualifiedName(ChangeStudentNameCommand.class),
                                                                     (cmd, sm, ctx) -> {
                                                                         ChangeStudentNameCommand payload = (ChangeStudentNameCommand) cmd.getPayload();
                                                                         var student = sm.loadEntity(Student.class,
                                                                                                     payload.id(),
                                                                                                     ctx).join();
                                                                         if (!Objects.equals(student.getName(),
                                                                                             payload.name())) {
                                                                             var eventSink = c.getComponent(EventSink.class);
                                                                             eventSink.publish(ctx,
                                                                                               TEST_CONTEXT,
                                                                                               studentNameChangedEventMessage(
                                                                                                       payload.id(),
                                                                                                       payload.name(),
                                                                                                       1));
                                                                         }
                                                                         return MessageStream.empty().cast();
                                                                     });
                delegate.subscribe(chc);

                return delegate;
            });


            var fixture = CommandModelTestFixture.with(configurer);
            fixture.givenNoPriorActivity()
                   .when(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                   .expectEvents(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));


            var fixture2 = CommandModelTestFixture.with(configurer);
            fixture2.givenEvents(
                            studentNameChangedEventMessage("my-studentId-1", "name-1", 1)
                    )
                    .when(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
                    .expectNoEvents();
        }
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