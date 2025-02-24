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

package org.axonframework.config;


import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fluent interface for the configuration of a {@link StatefulCommandHandlingComponent}.
 */
class StatefulCommandHandlingComponentTest {

    public static final String DEFAULT_CONTEXT = "default";

    private final SimpleEventStore eventStore = new SimpleEventStore(
            new AsyncInMemoryEventStorageEngine(),
            DEFAULT_CONTEXT,
            new AnnotationBasedTagResolver()
    );

    private final EventStateApplier<Student> studentEventStateApplier = (model, em) -> {
        if (em.getPayload() instanceof StudentNameChangedEvent e) {
            model.handle(e);
        }
        if (em.getPayload() instanceof StudentEnrolledEvent e) {
            model.handle(e);
        }
        return model;
    };

    private final AsyncEventSourcingRepository<String, Student> studentRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Student", myModelId)),
            studentEventStateApplier,
            Student::new,
            DEFAULT_CONTEXT
    );


    private final EventStateApplier<Course> courseEventStateApplier = (model, em) -> {
        if (em.getPayload() instanceof StudentEnrolledEvent e) {
            model.handle(e);
        }
        return model;
    };
    private final AsyncEventSourcingRepository<String, Course> courseRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Course", myModelId)),
            courseEventStateApplier,
            Course::new,
            DEFAULT_CONTEXT
    );

    private final Function<CommandMessage<?>, String> studentCommandIdResolver = command -> {
        if (command.getPayload() instanceof ChangeStudentNameCommand(String id, String name)) {
            return id;
        }
        if (command.getPayload() instanceof EnrollStudentToCourseCommand(String studentId, String courseId)) {
            return studentId;
        }
        return null;
    };

    private final Function<CommandMessage<?>, String> courseCommandIdResolver = command -> {
        if (command.getPayload() instanceof EnrollStudentToCourseCommand esc) {
            return esc.courseId();
        }
        return null;
    };

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a singular model command.
     */
    @Test
    void canHandleSingularModelCommand() throws ExecutionException, InterruptedException {
        var component = StatefulCommandHandlingComponent
                .forName("MyStatefulCommandHandlingComponent")
                .loadModelsEagerly()
                .registerModel(
                        Student.class,
                        studentCommandIdResolver,
                        (id, context) -> studentRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
                )
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            Student student = model.modelOf(Student.class);
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            eventStore.transaction(context, DEFAULT_CONTEXT)
                                      .appendEvent(new GenericEventMessage<>(
                                              new MessageType(StudentNameChangedEvent.class),
                                              new StudentNameChangedEvent(student.id, payload.name())));
                            return MessageStream.empty().cast();
                        });

        updateStudentName(component, "my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");
        updateStudentName(component, "my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");
        updateStudentName(component, "my-studentId-1", "name-3");
        verifyStudentName("my-studentId-1", "name-3");
        updateStudentName(component, "my-studentId-1", "name-4");
        verifyStudentName("my-studentId-1", "name-4");

        updateStudentName(component, "my-studentId-2", "name-5");
        verifyStudentName("my-studentId-1", "name-4");
        verifyStudentName("my-studentId-2", "name-5");
    }

    private void verifyStudentName(String id, String name) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            return studentRepository.load(id, context).thenAccept(student -> {
                assertEquals(name, student.entity().name);
            });
        }).join();
    }

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a command that targets multiple models at
     * the same time, in the same transaction.
     */
    @Test
    void canHandleCommandThatTargetsMultipleModels() throws ExecutionException, InterruptedException {
        var component = StatefulCommandHandlingComponent
                .forName("MyStatefulCommandHandlingComponent")
                .loadModelsEagerly()
                .registerModel(
                        Student.class,
                        studentCommandIdResolver,
                        (id, context) -> studentRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
                )
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            Student student = model.modelOf(Student.class);
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            eventStore.transaction(context, DEFAULT_CONTEXT)
                                      .appendEvent(new GenericEventMessage<>(
                                              new MessageType(StudentNameChangedEvent.class),
                                              new StudentNameChangedEvent(
                                                      student.id,
                                                      payload.name())));
                            return MessageStream.empty().cast();
                        })
                .registerModel(
                        "CourseModel",
                        Course.class,
                        courseCommandIdResolver,
                        (id, context) -> courseRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
                )
                .subscribe(
                        new QualifiedName(EnrollStudentToCourseCommand.class),
                        (command, models, context) -> {
                            EnrollStudentToCourseCommand payload = (EnrollStudentToCourseCommand) command.getPayload();
                            Student student = models.modelOf(Student.class);
                            Course course = models.modelOf(Course.class);

                            if (student.getCoursedEnrolled().size() > 2) {
                                throw new IllegalArgumentException(
                                        "Student already enrolled in 3 courses");
                            }

                            if (course.getStudentsEnrolled().size() > 2) {
                                throw new IllegalArgumentException("Course already has 3 students");
                            }

                            eventStore.transaction(context, DEFAULT_CONTEXT)
                                      .appendEvent(new GenericEventMessage<>(new MessageType(
                                              StudentEnrolledEvent.class),
                                                                             new StudentEnrolledEvent(
                                                                                     payload.studentId(),
                                                                                     payload.courseId())));
                            return MessageStream.empty().cast();
                        });

        // First student
        enrollStudentToCourse(component, "my-studentId-2", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Second student
        enrollStudentToCourse(component, "my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Third and last possible student
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-3", "my-courseId-1");
        verifyStudentEnrolledInCourse("my-studentId-2", "my-courseId-1");

        // Fourth can still enroll for other couse
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-2");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-2");

        // But five can not enroll for the first course
        assertThrows(ExecutionException.class,
                     () -> enrollStudentToCourse(component, "my-studentId-5", "my-courseId-1"));
    }

    private void verifyStudentEnrolledInCourse(String id, String courseId) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            return studentRepository
                    .load(id, context)
                    .thenAccept(student -> {
                        assertTrue(student.entity().getCoursedEnrolled().contains(courseId));
                    }).thenCompose((__) -> {
                        return courseRepository.load(courseId, context);
                    }).thenAccept(course -> {
                        assertTrue(course.entity().getStudentsEnrolled().contains(id));
                    });
        }).join();
    }

    private static void updateStudentName(StatefulCommandHandlingComponent component, String id, String name)
            throws InterruptedException, ExecutionException {
        sendCommand(component, new ChangeStudentNameCommand(id, name));
    }


    private static void enrollStudentToCourse(
            StatefulCommandHandlingComponent component,
            String studentId,
            String courseId
    )
            throws InterruptedException, ExecutionException {
        sendCommand(component, new EnrollStudentToCourseCommand(studentId, courseId));
    }

    private static <T> void sendCommand(
            StatefulCommandHandlingComponent component,
            T payload
    ) throws ExecutionException, InterruptedException {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            GenericCommandMessage<T> command = new GenericCommandMessage<>(
                    new MessageType(payload.getClass()),
                    payload);
            return component.handle(command, context).first().asCompletableFuture();
        }).get();
    }


    record ChangeStudentNameCommand(
            String id,
            String name
    ) {

    }

    record EnrollStudentToCourseCommand(
            String studentId,
            String courseId
    ) {

    }


    record StudentNameChangedEvent(
            @EventTag(key = "Student")
            String id,
            String name
    ) {

    }

    record StudentEnrolledEvent(
            @EventTag(key = "Student")
            String studentId,
            @EventTag(key = "Course")
            String courseId
    ) {

    }

    /**
     * Event-sourced Student model
     */
    static class Student {
        private String id;
        private String name;
        private List<String> coursedEnrolled = new ArrayList<>();

        public Student(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getCoursedEnrolled() {
            return coursedEnrolled;
        }

        public void handle(StudentEnrolledEvent event) {
            coursedEnrolled.add(event.courseId());
        }

        public void handle(StudentNameChangedEvent event) {
            name = event.name();
        }


        @Override
        public String toString() {
            return "Student{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", coursedEnrolled=" + coursedEnrolled +
                    '}';
        }
    }

    static class Course {

        private String id;
        private List<String> studentsEnrolled = new ArrayList<>();

        public Course(String id) {
            this.id = id;
        }

        public List<String> getStudentsEnrolled() {
            return studentsEnrolled;
        }

        public void handle(StudentEnrolledEvent event) {
            studentsEnrolled.add(event.studentId());
        }

        @Override
        public String toString() {
            return "Course{" +
                    "id='" + id + '\'' +
                    ", studentsEnrolled=" + studentsEnrolled +
                    '}';
        }
    }
}