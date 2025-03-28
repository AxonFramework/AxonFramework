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

package org.axonframework.eventsourcing.configuration;

import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.CommandResult;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.StatefulCommandHandler;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class EventSourcedEntityModuleTest {

    private AtomicBoolean courseCommandHandled;
    private AtomicBoolean courseEventHandled;
    private AtomicBoolean studentCommandHandled;
    private AtomicBoolean studentEventHandled;

    private CommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        courseCommandHandled = new AtomicBoolean(false);
        courseEventHandled = new AtomicBoolean(false);
        studentCommandHandled = new AtomicBoolean(false);
        studentEventHandled = new AtomicBoolean(false);

        AxonConfiguration config =
                EventSourcingConfigurer.create()
                                       .registerStatefulCommandHandlingModule(buildEventSourcedModule())
                                       .start();
        commandGateway = config.getComponent(CommandGateway.class);
    }

    private ModuleBuilder<StatefulCommandHandlingModule> buildEventSourcedModule() {
        return StatefulCommandHandlingModule.named("event-sourced-stateful-command-handling-module")
                                            .entities()
                                            .entity(courseEntityBuilder())
                                            .entity(studentEntityBuilder())
                                            .commandHandlers()
                                            .commandHandler(
                                                    new QualifiedName(RenameCourseCommand.class),
                                                    this::renameCourseCommandHandler
                                            )
                                            .commandHandler(
                                                    new QualifiedName(SubscribeStudentToCourseCommand.class),
                                                    this::subscribeStudentToCourseCommandHandler
                                            );
    }

    private EventSourcedEntityBuilder<CourseId, Course> courseEntityBuilder() {
        return EventSourcedEntityBuilder.entity(CourseId.class, Course.class)
                                        .entityFactory(c -> Course::new)
                                        .criteriaResolver(c -> courseId -> EventCriteria.anyEvent())
                                        .eventStateApplier(c -> (model, event, processingContext) -> {
                                            if (event.getPayload() instanceof CourseRenamedEvent courseRenamedEvent) {
                                                model.on(courseRenamedEvent);
                                            } else if (event.getPayload() instanceof StudentSubscribedToCourseEvent studentSubscribedToCourseEvent) {
                                                model.on(studentSubscribedToCourseEvent);
                                            }
                                            return model;
                                        });
    }

    private EventSourcedEntityBuilder<StudentId, Student> studentEntityBuilder() {
        return EventSourcedEntityBuilder.entity(StudentId.class, Student.class)
                                        .entityFactory(c -> Student::new)
                                        .criteriaResolver(c -> studentId -> EventCriteria.anyEvent())
                                        .eventStateApplier(c -> (model, event, processingContext) -> {
                                            if (event.getPayload() instanceof StudentSubscribedToCourseEvent studentSubscribedToCourseEvent) {
                                                model.on(studentSubscribedToCourseEvent);
                                            }
                                            return model;
                                        });
    }

    private StatefulCommandHandler renameCourseCommandHandler(NewConfiguration config) {
        return (cmd, stateManager, context) -> {
            RenameCourseCommand commandPayload =
                    (RenameCourseCommand) cmd.getPayload();
            // Handle command
            Course course = stateManager.loadEntity(
                    Course.class,
                    commandPayload.id(),
                    context
            ).join();
            course.handle(commandPayload);
            // Publish event
            CourseRenamedEvent eventPayload =
                    new CourseRenamedEvent(commandPayload.id);
            GenericEventMessage<CourseRenamedEvent> eventMessage = new GenericEventMessage<>(
                    new MessageType(CourseRenamedEvent.class),
                    eventPayload
            );
            config.getComponent(AsyncEventStore.class)
                  .publish(context, eventMessage);

            return MessageStream.just(new GenericCommandResultMessage<>(
                    new MessageType("singleEntity"), "singleEntity"
            ));
        };
    }

    private StatefulCommandHandler subscribeStudentToCourseCommandHandler(NewConfiguration config) {
        return (cmd, stateManager, context) -> {
            SubscribeStudentToCourseCommand commandPayload =
                    (SubscribeStudentToCourseCommand) cmd.getPayload();
            // Handle command on FIRST entity
            Course course = stateManager.loadEntity(
                    Course.class,
                    commandPayload.courseId,
                    context
            ).join();
            course.handle(commandPayload);
            // Handle command on SECOND entity
            Student student = stateManager.loadEntity(
                    Student.class,
                    commandPayload.studentId,
                    context
            ).join();
            student.handle(commandPayload);
            // Publish event
            StudentSubscribedToCourseEvent eventPayload =
                    new StudentSubscribedToCourseEvent(
                            commandPayload.studentId,
                            commandPayload.courseId
                    );
            GenericEventMessage<StudentSubscribedToCourseEvent> eventMessage = new GenericEventMessage<>(
                    new MessageType(StudentSubscribedToCourseEvent.class),
                    eventPayload
            );
            config.getComponent(AsyncEventStore.class)
                  .publish(context, eventMessage);

            return MessageStream.just(new GenericCommandResultMessage<>(
                    new MessageType("multiEntity"), "multiEntity"
            ));
        };
    }

    @Test
    void singleEntitySendAndWaitCommandMessage() {
        RenameCourseCommand testCommand = new RenameCourseCommand(new CourseId("courseID"));

        String handlerResponse = commandGateway.sendAndWait(testCommand, String.class);

        assertTrue(courseCommandHandled.get());
        assertTrue(courseEventHandled.get());
        assertEquals("singleEntity", handlerResponse);
    }

    @Test
    void singleEntitySendCommandMessage() {
        RenameCourseCommand command = new RenameCourseCommand(new CourseId("courseID"));

        CommandResult result = commandGateway.send(command, null);

        String handlerResponse = (String) result.getResultMessage().join().getPayload();

        assertTrue(courseCommandHandled.get());
        assertTrue(courseEventHandled.get());
        assertEquals("singleEntity", handlerResponse);
    }

    @Test
    void multiEntitySendAndWaitCommandMessage() {
        SubscribeStudentToCourseCommand testCommand =
                new SubscribeStudentToCourseCommand(new StudentId("studentID"), new CourseId("courseID"));

        String handlerResponse = commandGateway.sendAndWait(testCommand, String.class);

        assertTrue(courseCommandHandled.get());
        assertTrue(courseEventHandled.get());
        assertTrue(studentCommandHandled.get());
        assertTrue(studentEventHandled.get());
        assertEquals("multiEntity", handlerResponse);
    }

    @Test
    void multiEntitySendCommandMessage() {
        SubscribeStudentToCourseCommand testCommand =
                new SubscribeStudentToCourseCommand(new StudentId("studentID"), new CourseId("courseID"));

        CommandResult result = commandGateway.send(testCommand, null);

        String handlerResponse = (String) result.getResultMessage().join().getPayload();

        assertTrue(courseCommandHandled.get());
        assertTrue(courseEventHandled.get());
        assertTrue(studentCommandHandled.get());
        assertTrue(studentEventHandled.get());
        assertEquals("multiEntity", handlerResponse);
    }

    record CourseId(String id) {

    }

    class Course {

        private final CourseId id;

        Course(CourseId id) {
            this.id = id;
        }

        void handle(RenameCourseCommand cmd) {
            courseCommandHandled.set(true);
        }

        void handle(SubscribeStudentToCourseCommand cmd) {
            courseCommandHandled.set(true);
        }

        void on(CourseRenamedEvent event) {
            courseEventHandled.set(true);
        }

        void on(StudentSubscribedToCourseEvent event) {
            courseEventHandled.set(true);
        }
    }

    record RenameCourseCommand(CourseId id) {

    }

    record CourseRenamedEvent(CourseId courseId) {

    }

    record StudentId(String id) {

    }

    class Student {

        private final StudentId id;

        Student(StudentId id) {
            this.id = id;
        }

        void handle(SubscribeStudentToCourseCommand cmd) {
            studentCommandHandled.set(true);
        }

        void on(StudentSubscribedToCourseEvent event) {
            studentEventHandled.set(true);
        }
    }

    record SubscribeStudentToCourseCommand(StudentId studentId, CourseId courseId) {

    }

    record StudentSubscribedToCourseEvent(StudentId studentId, CourseId courseId) {

    }
}