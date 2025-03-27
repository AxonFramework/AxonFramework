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

package org.axonframework.modelling.configuration;

import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.CommandResult;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StatefulCommandHandlingModuleTest {

    private AtomicBoolean courseLoaded;
    private AtomicBoolean coursePersisted;
    private AtomicBoolean courseHandled;
    private AtomicBoolean studentLoaded;
    private AtomicBoolean studentPersisted;
    private AtomicBoolean studentHandled;

    private AxonConfiguration config;
    private CommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        courseLoaded = new AtomicBoolean(false);
        coursePersisted = new AtomicBoolean(false);
        courseHandled = new AtomicBoolean(false);
        studentLoaded = new AtomicBoolean(false);
        studentPersisted = new AtomicBoolean(false);
        studentHandled = new AtomicBoolean(false);

        config = ModellingConfigurer.create()
                                    .registerStatefulCommandHandlingModule(buildModule())
                                    .start();
        commandGateway = config.getComponent(CommandGateway.class);
    }

    private ModuleBuilder<StatefulCommandHandlingModule> buildModule() {
return StatefulCommandHandlingModule.named("my-command-module")
                                    .entities()
                                    .entity(StateBasedEntityBuilder.entity(CourseId.class, Course.class)
                                                                   .loader(c -> (id, context) -> {
                                                                       courseLoaded.set(true);
                                                                       return CompletableFuture.completedFuture(
                                                                               new Course(id)
                                                                       );
                                                                   })
                                                                   .persister(c -> (id, entity, context) -> {
                                                                       coursePersisted.set(true);
                                                                       return CompletableFuture.completedFuture(
                                                                               null
                                                                       );
                                                                   }))
                                    .entity(StateBasedEntityBuilder.entity(StudentId.class, Student.class)
                                                                   .loader(c -> (id, context) -> {
                                                                       studentLoaded.set(true);
                                                                       return CompletableFuture.completedFuture(
                                                                               new Student(id));
                                                                   })
                                                                   .persister(c -> (id, entity, context) -> {
                                                                       studentPersisted.set(true);
                                                                       return CompletableFuture.completedFuture(
                                                                               null
                                                                       );
                                                                   }))
                                    .commandHandlers()
                                    .commandHandler(
                                            new QualifiedName(RenameCourseCommand.class),
                                            (cmd, stateManager, context) -> {
                                                Course course = stateManager.loadEntity(
                                                        Course.class,
                                                        ((RenameCourseCommand) cmd.getPayload()).id(),
                                                        context
                                                ).join();
                                                course.handle(((RenameCourseCommand) cmd.getPayload()));
                                                return MessageStream.just(new GenericCommandResultMessage<>(
                                                        new MessageType("singleEntity"), "singleEntity"
                                                ));
                                            }
                                    )
                                    .commandHandler(
                                            new QualifiedName(SubscribeStudentToCourse.class),
                                            (cmd, stateManager, context) -> {
                                                Course course = stateManager.loadEntity(
                                                        Course.class,
                                                        ((SubscribeStudentToCourse) cmd.getPayload()).courseId,
                                                        context
                                                ).join();
                                                course.handle(((SubscribeStudentToCourse) cmd.getPayload()));

                                                Student student = stateManager.loadEntity(
                                                        Student.class,
                                                        ((SubscribeStudentToCourse) cmd.getPayload()).studentId,
                                                        context
                                                ).join();
                                                student.handle(((SubscribeStudentToCourse) cmd.getPayload()));
                                                return MessageStream.just(new GenericCommandResultMessage<>(
                                                        new MessageType("multiEntity"), "multiEntity"
                                                ));
                                            }
                                    );
}

    @Test
    void singleEntitySendAndWaitCommandMessage() {
        RenameCourseCommand testCommand = new RenameCourseCommand(new CourseId("courseID"));

        String handlerResponse = commandGateway.sendAndWait(testCommand, String.class);

        assertTrue(courseLoaded.get());
        assertTrue(coursePersisted.get());
        assertTrue(courseHandled.get());
        assertEquals("singleEntity", handlerResponse);
    }

    @Test
    void singleEntitySendCommandMessage() {
        RenameCourseCommand command = new RenameCourseCommand(new CourseId("courseID"));

        CommandResult result = commandGateway.send(command, null);

        String handlerResponse = (String) result.getResultMessage().join().getPayload();

        assertTrue(courseLoaded.get());
        assertTrue(coursePersisted.get());
        assertTrue(courseHandled.get());
        assertEquals("singleEntity", handlerResponse);
    }

    @Test
    void multiEntitySendAndWaitCommandMessage() {
        SubscribeStudentToCourse testCommand =
                new SubscribeStudentToCourse(new StudentId("studentID"), new CourseId("courseID"));

        String handlerResponse = commandGateway.sendAndWait(testCommand, String.class);

        assertTrue(courseLoaded.get());
        assertTrue(coursePersisted.get());
        assertTrue(courseHandled.get());
        assertTrue(studentLoaded.get());
        assertTrue(studentPersisted.get());
        assertTrue(studentHandled.get());
        assertEquals("multiEntity", handlerResponse);
    }

    @Test
    void multiEntitySendCommandMessage() {
        SubscribeStudentToCourse testCommand =
                new SubscribeStudentToCourse(new StudentId("studentID"), new CourseId("courseID"));

        CommandResult result = commandGateway.send(testCommand, null);

        String handlerResponse = (String) result.getResultMessage().join().getPayload();

        assertTrue(courseLoaded.get());
        assertTrue(coursePersisted.get());
        assertTrue(courseHandled.get());
        assertTrue(studentLoaded.get());
        assertTrue(studentPersisted.get());
        assertTrue(studentHandled.get());
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
            courseHandled.set(true);
        }

        void handle(SubscribeStudentToCourse cmd) {
            courseHandled.set(true);
        }
    }

    record RenameCourseCommand(CourseId id) {

    }

    record StudentId(String id) {

    }

    class Student {

        private final StudentId id;

        Student(StudentId id) {
            this.id = id;
        }

        void handle(SubscribeStudentToCourse cmd) {
            studentHandled.set(true);
        }
    }

    record SubscribeStudentToCourse(StudentId studentId, CourseId courseId) {

    }
}