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

package org.axonframework.config.testsuite.student;

import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.config.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.config.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.config.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.config.testsuite.student.state.Course;
import org.axonframework.config.testsuite.student.state.Student;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventStateApplier;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sets up the basics for the testsuite of the Student/Mentor/Course model. Can be customized by overriding the
 * relevant methods. By default uses a mix of different available options to validate the different ways of setting up
 * the event sourcing repository.
 */
public abstract class AbstractStudentTestSuite {

    protected final String DEFAULT_CONTEXT = "default";

    protected SimpleEventStore eventStore = new SimpleEventStore(
            new AsyncInMemoryEventStorageEngine(),
            DEFAULT_CONTEXT,
            getTagResolver()
    );

    protected EventStateApplier<Student> studentEventStateApplier = getStudentAnnotationBasedEventStateApplier();
    protected EventStateApplier<Course> courseEventStateApplier = getCourseEventStateApplier();

    protected AsyncEventSourcingRepository<String, Student> studentRepository = new AsyncEventSourcingRepository<>(
            String.class,
            Student.class,
            eventStore,
            getStudentCriteriaResolver(),
            studentEventStateApplier,
            Student::new,
            DEFAULT_CONTEXT
    );

    protected AsyncEventSourcingRepository<String, Course> courseRepository = new AsyncEventSourcingRepository<>(
            String.class,
            Course.class,
            eventStore,
            getCourseCriteriaResolver(),
            courseEventStateApplier,
            Course::new,
            DEFAULT_CONTEXT
    );


    protected StateManager registry = SimpleStateManager
            .builder("MyModelRegistry")
            .register(studentRepository)
            .register(courseRepository)
            .build();


    /**
     * Returns the {@link EventStateApplier} for the {@link Course} model. Defaults to manually calling the event
     * sourcing handlers on the model.
     */
    protected EventStateApplier<Course> getCourseEventStateApplier() {
        return (model, em, ctx) -> {
            if (em.getPayload() instanceof StudentEnrolledEvent e) {
                model.handle(e);
            }
            return model;
        };
    }

    /**
     * Returns the {@link EventStateApplier} for the {@link Student} model. Defaults to using the
     * {@link AnnotationBasedEventStateApplier} to use the annotations placed.
     */
    protected EventStateApplier<Student> getStudentAnnotationBasedEventStateApplier() {
        return new AnnotationBasedEventStateApplier<>(Student.class);
    }


    /**
     * Returns the {@link TagResolver} to use for the event store. Defaults to the {@link AnnotationBasedTagResolver}.
     */
    protected TagResolver getTagResolver() {
        return new AnnotationBasedTagResolver();
    }


    /**
     * Returns the {@link CriteriaResolver} for the {@link Student} model. Defaults to a criteria that matches any event
     * with the tag "Student" and the given model id.
     */
    protected CriteriaResolver<String> getStudentCriteriaResolver() {
        return myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Student", myModelId));
    }

    /**
     * Returns the {@link CriteriaResolver} for the {@link Course} model. Defaults to a criteria that matches any event
     * with the tag "Course" and the given model id.
     */
    protected CriteriaResolver<String> getCourseCriteriaResolver() {
        return myModelId -> EventCriteria.forAnyEventType().withTags(new Tag("Course", myModelId));
    }


    protected <T> void sendCommand(
            CommandHandlingComponent component,
            T payload
    ) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            GenericCommandMessage<T> command = new GenericCommandMessage<>(
                    new MessageType(payload.getClass()),
                    payload);
            return component.handle(command, context).first().asCompletableFuture();
        }).join();
    }

    protected void appendEvent(ProcessingContext context, Object event) {
        eventStore.transaction(context, DEFAULT_CONTEXT)
                  .appendEvent(new GenericEventMessage<>(
                          new MessageType(event.getClass()),
                          event));
    }

    protected void verifyStudentName(String id, String name) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult(context ->
                                      studentRepository
                                              .load(id, context)
                                              .thenAccept(student -> assertEquals(name, student.entity().getName()))
        ).join();
    }

    protected void verifyStudentEnrolledInCourse(String id, String courseId) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult(context -> studentRepository
                   .load(id, context)
                   .thenAccept(student -> assertTrue(student.entity().getCoursesEnrolled().contains(courseId)))
                   .thenCompose(v -> courseRepository.load(courseId, context))
                   .thenAccept(course -> assertTrue(course.entity().getStudentsEnrolled().contains(id))))
           .join();
    }

    protected void changeStudentName(CommandHandlingComponent component, String id, String name) {
        sendCommand(component, new ChangeStudentNameCommand(id, name));
    }


    protected void enrollStudentToCourse(
            CommandHandlingComponent component,
            String studentId,
            String courseId
    ) {
        sendCommand(component, new EnrollStudentToCourseCommand(studentId, courseId));
    }
}
