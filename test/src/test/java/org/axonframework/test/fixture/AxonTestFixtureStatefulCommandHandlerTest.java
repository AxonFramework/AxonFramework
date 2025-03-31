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
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.Student;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;

import java.util.Objects;


class AxonTestFixtureStatefulCommandHandlerTest {

    private static final String TEST_CONTEXT = "TEST_CONTEXT";

    @Test
    void givenEventsWhenCommandThenNoEvents() {
        var configurer = MessagingConfigurer.create();
        registerSampleStatefulCommandHandler(configurer);

        // todo: add customization!
        var fixture = AxonTestFixture.with(configurer);

        var studentNameChanged = studentNameChangedEventMessage("my-studentId-1", "name-1", 1);
        var changeToTheSameName = new ChangeStudentNameCommand("my-studentId-1", "name-1");
        fixture.given()
               .event(studentNameChanged)
               .when()
               .command(changeToTheSameName)
               .then()
               .noEvents();
    }

    @Test
    void givenEventsWhenCommandThenEvents() {
        var configurer = MessagingConfigurer.create();
        registerSampleStatefulCommandHandler(configurer);

        var fixture = AxonTestFixture.with(configurer);

        var studentNameChanged = studentNameChangedEventMessage("my-studentId-1", "name-1", 1);
        var changeToTheAnotherName = new ChangeStudentNameCommand("my-studentId-1", "name-2");
        fixture.given()
               .events(studentNameChanged)
               .when()
               .command(changeToTheAnotherName)
               .then()
               .events(studentNameChangedEventMessage("my-studentId-1", "name-2", 2));
    }


    @Test
    void givenEventAndCommandWhenCommandThenEvents() {
        var configurer = MessagingConfigurer.create();
        registerSampleStatefulCommandHandler(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.given()
               .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-2"))
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
               .then()
               .events(new StudentNameChangedEvent("my-studentId-1", "name-3", 3));
    }

    @Test
    void givenEventAndMultipleCommandsWhenCommandThenEvents() {
        var configurer = MessagingConfigurer.create();
        registerSampleStatefulCommandHandler(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.given()
               .event(new StudentNameChangedEvent("my-studentId-1", "name-1", 1))
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-2"))
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-3"))
               .then()
               .events(new StudentNameChangedEvent("my-studentId-1", "name-4", 4))
               .and()
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-4"))
               .then()
               .events(new StudentNameChangedEvent("my-studentId-1", "name-4", 4));
    }


    @Test
    void givenEventsWhenCommandThenExpectEvents() {
        var configurer = MessagingConfigurer.create();
        registerSampleStatefulCommandHandler(configurer);

        var fixture = AxonTestFixture.with(configurer);

        fixture.given()
               .noPriorActivity()
               .when()
               .command(new ChangeStudentNameCommand("my-studentId-1", "name-1"))
               .then()
               .events(studentNameChangedEventMessage("my-studentId-1", "name-1", 1));
    }

    private static void registerSampleStatefulCommandHandler(MessagingConfigurer configurer) {
        configurer.registerComponent(
                StateManager.class,
                c -> {
                    var repository = new AsyncEventSourcingRepository<>(
                            String.class,
                            Student.class,
                            c.getComponent(AsyncEventStore.class),
                            id -> EventCriteria.match()
                                               .eventsOfAnyType()
                                               .withTags("Student", id),
                            new AnnotationBasedEventStateApplier<>(Student.class),
                            Student::new
                    );
                    return SimpleStateManager.builder("testfixture")
                                             .register(repository)
                                             .build();
                });

        configurer.registerComponent(AsyncEventStore.class,
                                     c -> new SimpleEventStore(new AsyncInMemoryEventStorageEngine(),
                                                               new AnnotationBasedTagResolver()))
                  .registerComponent(EventSink.class, c -> c.getComponent(AsyncEventStore.class));

        configurer.registerDecorator(CommandBus.class, 50, (c, name, delegate) -> {
            var stateManager = c.getComponent(StateManager.class);
            var statefulCommandHandler = StatefulCommandHandlingComponent
                    .create("mystatefulCH", stateManager)
                    .subscribe(
                            new QualifiedName(ChangeStudentNameCommand.class),
                            (cmd, sm, ctx) -> {
                                ChangeStudentNameCommand payload = (ChangeStudentNameCommand) cmd.getPayload();
                                var student = sm.loadEntity(Student.class, payload.id(), ctx).join();
                                if (!Objects.equals(student.getName(), payload.name())) {
                                    var eventSink = c.getComponent(EventSink.class);
                                    eventSink.publish(
                                            ctx,
                                            studentNameChangedEventMessage(payload.id(),
                                                                           payload.name(),
                                                                           student.getChanges() + 1)
                                    );
                                }
                                return MessageStream.empty().cast();
                            });
            delegate.subscribe(statefulCommandHandler);

            return delegate;
        });
    }

    private static GenericEventMessage<StudentNameChangedEvent> studentNameChangedEventMessage(
            String id,
            String name,
            int change
    ) {
        return new GenericEventMessage<>(new MessageType(StudentNameChangedEvent.class),
                                         new StudentNameChangedEvent(id, name, change));
    }
}