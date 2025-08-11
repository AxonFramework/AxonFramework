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

package org.axonframework.modelling.event;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating real-world usage of StatefulEventHandling components. This test shows how
 * StatefulEventHandling can be used to build event projections that maintain state.
 */
class StatefulEventHandlingIntegrationTest {

    private StateManager stateManager;
    private StatefulEventHandlingComponent eventHandlingComponent;

    @BeforeEach
    void setUp() {
        stateManager = SimpleStateManager.named("integration-test")
                                         .register(String.class, Student.class,
                                                   this::loadStudent,
                                                   this::saveStudent)
                                         .register(String.class, StudentStatistics.class,
                                                   this::loadStatistics,
                                                   this::saveStatistics);

        eventHandlingComponent = StatefulEventHandlingComponent.create("student-projection", stateManager);
    }

    @Nested
    class StudentProjectionScenarios {

        @Test
        void studentNameChangedEventUpdatesProjection() {
            // given
            AtomicReference<Student> updatedStudent = new AtomicReference<>();
            AtomicReference<StudentStatistics> updatedStats = new AtomicReference<>();

            eventHandlingComponent.subscribe(
                    new QualifiedName(StudentNameChangedEvent.class),
                    (event, state, ctx) -> {
                        StudentNameChangedEvent payload = (StudentNameChangedEvent) event.payload();

                        // Load and update student entity
                        Student student = state.loadEntity(Student.class, payload.studentId(), ctx).join();
                        student.updateName(payload.newName());
                        updatedStudent.set(student);

                        // Load and update statistics
                        StudentStatistics stats = state.loadEntity(StudentStatistics.class, "global", ctx).join();
                        stats.recordNameChange();
                        updatedStats.set(stats);

                        return MessageStream.empty().cast();
                    }
            );

            // when
            StudentNameChangedEvent event = new StudentNameChangedEvent("student-123", "John Doe");
            EventMessage<StudentNameChangedEvent> eventMessage = EventTestUtils.asEventMessage(event);
            ProcessingContext context = StubProcessingContext.forMessage(eventMessage);

            eventHandlingComponent.handle(eventMessage, context).asCompletableFuture().join();

            // then
            assertNotNull(updatedStudent.get());
            assertEquals("John Doe", updatedStudent.get().getName());

            assertNotNull(updatedStats.get());
            assertEquals(1, updatedStats.get().getNameChanges());
        }

        @Test
        void multipleEventTypesHandledByDifferentHandlers() {
            // given
            AtomicInteger handlerInvocations = new AtomicInteger(0);

            // Register multiple event handlers
            eventHandlingComponent
                    .subscribe(new QualifiedName(StudentNameChangedEvent.class), (event, state, ctx) -> {
                        handlerInvocations.incrementAndGet();
                        return MessageStream.empty().cast();
                    })
                    .subscribe(new QualifiedName(StudentEnrolledEvent.class), (event, state, ctx) -> {
                        StudentEnrolledEvent payload = (StudentEnrolledEvent) event.payload();
                        Student student = state.loadEntity(Student.class, payload.studentId(), ctx).join();
                        student.enroll(payload.course());
                        handlerInvocations.incrementAndGet();
                        return MessageStream.empty().cast();
                    });

            // when
            EventMessage<StudentNameChangedEvent> nameChangedEvent = EventTestUtils.asEventMessage(
                    new StudentNameChangedEvent("student-123", "Jane Smith"));
            EventMessage<StudentEnrolledEvent> enrolledEvent = EventTestUtils.asEventMessage(
                    new StudentEnrolledEvent("student-123", "Math 101"));

            eventHandlingComponent.handle(nameChangedEvent, StubProcessingContext.forMessage(nameChangedEvent))
                                  .asCompletableFuture().join();
            eventHandlingComponent.handle(enrolledEvent, StubProcessingContext.forMessage(enrolledEvent))
                                  .asCompletableFuture().join();

            // then
            assertEquals(2, handlerInvocations.get());
        }

        @Test
        void statefulHandlerCanLoadMultipleEntities() {
            // given
            AtomicBoolean allEntitiesLoaded = new AtomicBoolean(false);

            eventHandlingComponent.subscribe(
                    new QualifiedName(StudentNameChangedEvent.class),
                    (event, state, ctx) -> {
                        StudentNameChangedEvent payload = (StudentNameChangedEvent) event.payload();

                        // Load multiple entities in single handler
                        CompletableFuture<Student> studentFuture = state.loadEntity(Student.class,
                                                                                    payload.studentId(),
                                                                                    ctx);
                        CompletableFuture<StudentStatistics> statsFuture = state.loadEntity(StudentStatistics.class,
                                                                                            "global",
                                                                                            ctx);

                        CompletableFuture.allOf(studentFuture, statsFuture)
                                         .thenRun(() -> allEntitiesLoaded.set(true))
                                         .join();

                        return MessageStream.empty().cast();
                    }
            );

            // when
            StudentNameChangedEvent event = new StudentNameChangedEvent("student-456", "Alice Cooper");
            EventMessage<StudentNameChangedEvent> eventMessage = EventTestUtils.asEventMessage(event);

            eventHandlingComponent.handle(eventMessage, StubProcessingContext.forMessage(eventMessage))
                                  .asCompletableFuture().join();

            // then
            assertTrue(allEntitiesLoaded.get());
        }
    }

    @Nested
    class ErrorHandlingScenarios {

        @Test
        void exceptionInHandlerResultsInFailedStream() {
            // given
            eventHandlingComponent.subscribe(
                    new QualifiedName(StudentNameChangedEvent.class),
                    (event, state, ctx) -> {
                        throw new RuntimeException("Processing failed");
                    }
            );

            // when / then
            StudentNameChangedEvent event = new StudentNameChangedEvent("student-789", "Bob Wilson");
            EventMessage<StudentNameChangedEvent> eventMessage = EventTestUtils.asEventMessage(event);

            assertThrows(Exception.class, () -> {
                eventHandlingComponent.handle(eventMessage, StubProcessingContext.forMessage(eventMessage))
                                      .asCompletableFuture().join();
            });
        }

        @Test
        void entityNotFoundHandledGracefully() {
            // given
            AtomicBoolean handlerCompleted = new AtomicBoolean(false);

            eventHandlingComponent.subscribe(
                    new QualifiedName(StudentNameChangedEvent.class),
                    (event, state, ctx) -> {
                        state.loadEntity(Student.class, "non-existent", ctx).join();
                        handlerCompleted.set(true);
                        return MessageStream.empty().cast();
                    }
            );

            // when
            StudentNameChangedEvent event = new StudentNameChangedEvent("non-existent", "Test Name");
            EventMessage<StudentNameChangedEvent> eventMessage = EventTestUtils.asEventMessage(event);

            eventHandlingComponent.handle(eventMessage, StubProcessingContext.forMessage(eventMessage))
                                  .asCompletableFuture().join();

            // then
            assertTrue(handlerCompleted.get());
        }
    }

    // Helper methods for entity loading/saving
    private CompletableFuture<Student> loadStudent(String id, ProcessingContext context) {
        return CompletableFuture.completedFuture(new Student(id));
    }

    private CompletableFuture<Void> saveStudent(String id, Student student, ProcessingContext context) {
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<StudentStatistics> loadStatistics(String id, ProcessingContext context) {
        return CompletableFuture.completedFuture(new StudentStatistics());
    }

    private CompletableFuture<Void> saveStatistics(String id, StudentStatistics stats, ProcessingContext context) {
        return CompletableFuture.completedFuture(null);
    }

    // Sample domain objects for the integration test
    public record StudentNameChangedEvent(String studentId, String newName) {

    }

    public record StudentEnrolledEvent(String studentId, String course) {

    }

    public static class Student {

        private final String id;
        private String name;
        private String course;

        public Student(String id) {
            this.id = id;
        }

        public void updateName(String name) {
            this.name = name;
        }

        public void enroll(String course) {
            this.course = course;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCourse() {
            return course;
        }
    }

    public static class StudentStatistics {

        private int nameChanges = 0;
        private int enrollments = 0;

        public void recordNameChange() {
            nameChanges++;
        }

        public void recordEnrollment() {
            enrollments++;
        }

        public int getNameChanges() {
            return nameChanges;
        }

        public int getEnrollments() {
            return enrollments;
        }
    }
}