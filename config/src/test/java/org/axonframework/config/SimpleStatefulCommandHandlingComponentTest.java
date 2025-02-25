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


import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.EventSourcingHandler;
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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.SimpleModelRegistry;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fluent interface for the configuration of a {@link StatefulCommandHandlingComponent}.
 */
class SimpleStatefulCommandHandlingComponentTest {

    public static final String DEFAULT_CONTEXT = "default";

    private final SimpleEventStore eventStore = new SimpleEventStore(
            new AsyncInMemoryEventStorageEngine(),
            DEFAULT_CONTEXT,
            new AnnotationBasedTagResolver()
    );

    private final EventStateApplier<Student> studentEventStateApplier = new AnnotationBasedEventStateApplier<>(Student.class);
    private final EventStateApplier<Course> courseEventStateApplier = new AnnotationBasedEventStateApplier<>(Course.class);

    private final AsyncEventSourcingRepository<String, Student> studentRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Student", myModelId)),
            studentEventStateApplier,
            Student::new,
            DEFAULT_CONTEXT
    );


    private final AsyncEventSourcingRepository<String, Course> courseRepository = new AsyncEventSourcingRepository<>(
            eventStore,
            myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Course", myModelId)),
            courseEventStateApplier,
            Course::new,
            DEFAULT_CONTEXT
    );

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a singular model command.
     */
    @Test
    void canHandleSingularModelCommand() throws ExecutionException, InterruptedException {
        var registry = SimpleModelRegistry
                .create()
                .registerModel(
                        String.class,
                        Student.class,
                        (id, context) ->
                                studentRepository.loadOrCreate(id, context)
                                                 .thenApply(ManagedEntity::entity)
                );

        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            Student student = model.getModel(Student.class, payload.id()).join();
                            appendEvent(context, new StudentNameChangedEvent(student.id, payload.name()));
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
        uow.executeWithResult((context) ->
                                      studentRepository
                                              .load(id, context)
                                              .thenAccept(student -> assertEquals(name, student.entity().name))
        ).join();
    }

    /**
     * Tests that the {@link StatefulCommandHandlingComponent} can handle a command that targets multiple models at the
     * same time, in the same transaction.
     */
    @Test
    void canHandleCommandThatTargetsMultipleModels() throws ExecutionException, InterruptedException {
        var registry = SimpleModelRegistry
                .create()
                .registerModel(
                        String.class,
                        Student.class,
                        (id, context) -> studentRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
                )
                .registerModel(
                        String.class,
                        Course.class,
                        (id, context) -> courseRepository.loadOrCreate(id, context).thenApply(ManagedEntity::entity)
                );

        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            Student student = model.getModel(Student.class, payload.id()).join();
                            appendEvent(context, new StudentNameChangedEvent(student.id, payload.name()));
                            return MessageStream.empty().cast();
                        })
                .subscribe(
                        new QualifiedName(EnrollStudentToCourseCommand.class),
                        (command, models, context) -> {
                            EnrollStudentToCourseCommand payload = (EnrollStudentToCourseCommand) command.getPayload();
                            Student student = models.getModel(Student.class, payload.studentId()).join();
                            Course course = models.getModel(Course.class, payload.courseId()).join();

                            if (student.getCoursesEnrolled().size() > 2) {
                                throw new IllegalArgumentException(
                                        "Student already enrolled in 3 courses");
                            }

                            if (course.getStudentsEnrolled().size() > 2) {
                                throw new IllegalArgumentException("Course already has 3 students");
                            }
                            appendEvent(context, new StudentEnrolledEvent(payload.studentId(), payload.courseId()));
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

        // Fourth can still enroll for other course
        enrollStudentToCourse(component, "my-studentId-4", "my-courseId-2");
        verifyStudentEnrolledInCourse("my-studentId-4", "my-courseId-2");

        // But five can not enroll for the first course
        assertThrows(ExecutionException.class,
                     () -> enrollStudentToCourse(component, "my-studentId-5", "my-courseId-1"
                     ));
    }

    private void appendEvent(ProcessingContext context, Object event) {
        eventStore.transaction(context, DEFAULT_CONTEXT)
                  .appendEvent(new GenericEventMessage<>(
                          new MessageType(event.getClass()),
                          event));
    }

    private void verifyStudentEnrolledInCourse(String id, String courseId) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> studentRepository
                .load(id, context)
                .thenAccept(student -> assertTrue(student.entity().getCoursesEnrolled().contains(courseId)))
                .thenCompose((__) -> courseRepository.load(courseId, context))
                .thenAccept(course -> assertTrue(course.entity().getStudentsEnrolled().contains(id))))
           .join();
    }

    private void updateStudentName(StatefulCommandHandlingComponent component, String id, String name)
            throws InterruptedException, ExecutionException {
        sendCommand(component, new ChangeStudentNameCommand(id, name));
    }


    private void enrollStudentToCourse(
            StatefulCommandHandlingComponent component,
            String studentId,
            String courseId
    )
            throws InterruptedException, ExecutionException {
        sendCommand(component, new EnrollStudentToCourseCommand(studentId, courseId));
    }

    private <T> void sendCommand(
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
        private List<String> coursesEnrolled = new ArrayList<>();

        public Student(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getCoursesEnrolled() {
            return coursesEnrolled;
        }

        @EventSourcingHandler
        public void handle(StudentEnrolledEvent event) {
            coursesEnrolled.add(event.courseId());
        }

        @EventSourcingHandler
        public void handle(StudentNameChangedEvent event) {
            name = event.name();
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

        @EventSourcingHandler
        public void handle(StudentEnrolledEvent event) {
            studentsEnrolled.add(event.studentId());
        }
    }
}