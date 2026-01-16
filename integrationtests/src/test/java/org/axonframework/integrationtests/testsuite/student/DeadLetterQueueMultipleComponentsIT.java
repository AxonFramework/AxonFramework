/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.integrationtests.testsuite.student;

import jakarta.annotation.Nonnull;
import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.deadletter.CachingSequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfiguration;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPolicy;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test validating Dead Letter Queue (DLQ) behavior with multiple event handling components within a single
 * {@link org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor}.
 * <p>
 * Tests verify that each event handling component has its own independent DLQ, ensuring that:
 * <ul>
 *     <li>Failed events in one component do not affect the other component's DLQ</li>
 *     <li>Sequence isolation is maintained per component</li>
 *     <li>Dead letter processing can be performed independently per component</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DeadLetterQueueMultipleComponentsIT extends AbstractStudentIT {

    private static final String PROCESSOR_NAME = "dlq-multi-component-processor";

    private FailingRecordingEventHandlingComponent component1;
    private FailingRecordingEventHandlingComponent component2;

    /**
     * Unique identifier for each test run to ensure test isolation. Events from previous test runs will not match
     * the failure predicates of the current test.
     */
    private String testRunId;

    @BeforeEach
    void setUpComponents() {
        // Generate a unique test run ID to isolate events between tests
        testRunId = UUID.randomUUID().toString().substring(0, 8);

        component1 = new FailingRecordingEventHandlingComponent(
                "component1",
                studentId -> studentId.contains(failMarker1())
        );
        component2 = new FailingRecordingEventHandlingComponent(
                "component2",
                studentId -> studentId.contains(failMarker2())
        );
    }

    private String failMarker1() {
        return "-fail1-" + testRunId;
    }

    private String failMarker2() {
        return "-fail2-" + testRunId;
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        var processorModule = EventProcessorModule
                .pooledStreaming(PROCESSOR_NAME)
                .eventHandlingComponents(components -> components
                        .declarative(cfg -> component1)
                        .declarative(cfg -> component2))
                .customized((cfg, c) -> c
                        .deadLetterQueue(dlq -> dlq.enabled()));

        return configurer.messaging(
                messaging -> messaging.eventProcessing(
                        ep -> ep.pooledStreaming(
                                ps -> ps.processor(processorModule)
                        )
                )
        );
    }

    @Nested
    @DisplayName("Independent DLQs per Component")
    class IndependentDlqTests {

        @Test
        @DisplayName("When component1 fails an event, it should go to component1's DLQ only")
        void failedEventInComponent1ShouldOnlyGoToComponent1Dlq() {
            // given
            startApp();
            var failingStudentId = createId("student" + failMarker1());
            var successStudentId = createId("student-ok-" + testRunId);
            var courseId = createId("course");

            // when
            studentEnrolledToCourse(failingStudentId, courseId);
            studentEnrolledToCourse(successStudentId, courseId);

            // then
            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       // Component1's DLQ should contain the failed event
                       var dlq1 = getComponent1Dlq();
                       assertThat(dlq1.contains(failingStudentId).join()).isTrue();
                       assertThat(dlq1.size().join()).isEqualTo(1L);

                       // Component2's DLQ should be empty
                       var dlq2 = getComponent2Dlq();
                       assertThat(dlq2.contains(failingStudentId).join()).isFalse();
                       assertThat(dlq2.size().join()).isEqualTo(0L);

                       // Successful event should be handled by both components
                       assertThat(component1.successfullyHandled()).contains(successStudentId);
                       assertThat(component2.successfullyHandled()).contains(successStudentId);
                   });
        }

        @Test
        @DisplayName("When component2 fails an event, it should go to component2's DLQ only")
        void failedEventInComponent2ShouldOnlyGoToComponent2Dlq() {
            // given
            startApp();
            var failingStudentId = createId("student" + failMarker2());
            var successStudentId = createId("student-ok-" + testRunId);
            var courseId = createId("course");

            // when
            studentEnrolledToCourse(failingStudentId, courseId);
            studentEnrolledToCourse(successStudentId, courseId);

            // then
            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       // Component1's DLQ should be empty
                       var dlq1 = getComponent1Dlq();
                       assertThat(dlq1.contains(failingStudentId).join()).isFalse();
                       assertThat(dlq1.size().join()).isEqualTo(0L);

                       // Component2's DLQ should contain the failed event
                       var dlq2 = getComponent2Dlq();
                       assertThat(dlq2.contains(failingStudentId).join()).isTrue();
                       assertThat(dlq2.size().join()).isEqualTo(1L);

                       // Successful event should be handled by both components
                       assertThat(component1.successfullyHandled()).contains(successStudentId);
                       assertThat(component2.successfullyHandled()).contains(successStudentId);
                   });
        }
    }

    @Nested
    @DisplayName("Sequence Isolation")
    class SequenceIsolationTests {

        @Test
        @DisplayName("Subsequent events for a failed sequence should be enqueued in the same component's DLQ")
        void subsequentEventsForFailedSequenceShouldBeEnqueuedInSameComponentDlq() {
            // given
            startApp();
            var failingStudentId = createId("student" + failMarker1());
            var courseId1 = createId("course-1");
            var courseId2 = createId("course-2");
            var courseId3 = createId("course-3");

            // when - first event fails, subsequent events for same sequence should be enqueued
            studentEnrolledToCourse(failingStudentId, courseId1);
            studentEnrolledToCourse(failingStudentId, courseId2);
            studentEnrolledToCourse(failingStudentId, courseId3);

            // then
            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       // Component1's DLQ should contain all 3 events for the same sequence
                       var dlq1 = getComponent1Dlq();
                       assertThat(dlq1.contains(failingStudentId).join()).isTrue();
                       assertThat(dlq1.sequenceSize(failingStudentId).join()).isEqualTo(3L);

                       // Component2's DLQ should have no events (different fail marker)
                       var dlq2 = getComponent2Dlq();
                       assertThat(dlq2.contains(failingStudentId).join()).isFalse();
                   });
        }
    }

    @Nested
    @DisplayName("Both Components Can Fail Independently")
    class BothComponentsFailIndependentlyTests {

        @Test
        @DisplayName("Component1 fails on student-1, Component2 fails on student-2 - each goes to correct DLQ")
        void bothComponentsCanFailOnDifferentSequences() {
            // given
            startApp();
            var failingStudent1 = createId("student" + failMarker1());
            var failingStudent2 = createId("student" + failMarker2());
            var successStudent = createId("student-ok-" + testRunId);
            var courseId = createId("course");

            // when
            studentEnrolledToCourse(failingStudent1, courseId);
            studentEnrolledToCourse(failingStudent2, courseId);
            studentEnrolledToCourse(successStudent, courseId);

            // then
            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       // Component1's DLQ should have student1
                       var dlq1 = getComponent1Dlq();
                       assertThat(dlq1.contains(failingStudent1).join()).isTrue();
                       assertThat(dlq1.contains(failingStudent2).join()).isFalse();

                       // Component2's DLQ should have student2
                       var dlq2 = getComponent2Dlq();
                       assertThat(dlq2.contains(failingStudent2).join()).isTrue();
                       assertThat(dlq2.contains(failingStudent1).join()).isFalse();

                       // Success event should be handled by both
                       assertThat(component1.successfullyHandled()).contains(successStudent);
                       assertThat(component2.successfullyHandled()).contains(successStudent);
                   });
        }
    }

    @Nested
    @DisplayName("Dead Letter Processing")
    class DeadLetterProcessingTests {

        @Test
        @DisplayName("Processing dead letters from one component should not affect the other component's DLQ")
        void processingDeadLettersFromOneComponentShouldNotAffectOtherComponentDlq() {
            // given
            startApp();
            var failingStudent1 = createId("student" + failMarker1());
            var failingStudent2 = createId("student" + failMarker2());
            var courseId = createId("course");

            // and - both components have dead letters
            studentEnrolledToCourse(failingStudent1, courseId);
            studentEnrolledToCourse(failingStudent2, courseId);

            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       assertThat(getComponent1Dlq().size().join()).isEqualTo(1L);
                       assertThat(getComponent2Dlq().size().join()).isEqualTo(1L);
                   });

            // and - fix component1's failure condition
            component1.stopFailing();

            // when - process dead letters from component1
            processDeadLettersForComponent1();

            // then - component1's DLQ should be empty, component2's DLQ unchanged
            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       assertThat(getComponent1Dlq().size().join()).isEqualTo(0L);
                       assertThat(getComponent2Dlq().size().join()).isEqualTo(1L);

                       // Component1 should have now successfully processed the dead letter
                       assertThat(component1.successfullyHandled()).contains(failingStudent1);
                   });
        }

        @Test
        @DisplayName("Should be able to process dead letters from both components independently")
        void shouldProcessDeadLettersFromBothComponentsIndependently() {
            // given
            startApp();
            var failingStudent1 = createId("student" + failMarker1());
            var failingStudent2 = createId("student" + failMarker2());
            var courseId = createId("course");

            // and - both components have dead letters
            studentEnrolledToCourse(failingStudent1, courseId);
            studentEnrolledToCourse(failingStudent2, courseId);

            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       assertThat(getComponent1Dlq().size().join()).isEqualTo(1L);
                       assertThat(getComponent2Dlq().size().join()).isEqualTo(1L);
                   });

            // and - fix both components' failure conditions
            component1.stopFailing();
            component2.stopFailing();

            // when - process dead letters from both components
            processDeadLettersForComponent1();
            processDeadLettersForComponent2();

            // then - both DLQs should be empty
            await().atMost(10, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       assertThat(getComponent1Dlq().size().join()).isEqualTo(0L);
                       assertThat(getComponent2Dlq().size().join()).isEqualTo(0L);

                       // Both components should have successfully processed their dead letters
                       assertThat(component1.successfullyHandled()).contains(failingStudent1);
                       assertThat(component2.successfullyHandled()).contains(failingStudent2);
                   });
        }
    }

    // --- Helper Methods ---

    @SuppressWarnings("unchecked")
    private CachingSequencedDeadLetterQueue<EventMessage> getComponent1Dlq() {
        return startedConfiguration.getModuleConfiguration(PROCESSOR_NAME)
                                   .flatMap(m -> m.getOptionalComponent(
                                           CachingSequencedDeadLetterQueue.class,
                                           "CachingDeadLetterQueue[" + PROCESSOR_NAME + "][0]"
                                   ))
                                   .orElseThrow(() -> new IllegalStateException("Component1 DLQ not found"));
    }

    @SuppressWarnings("unchecked")
    private CachingSequencedDeadLetterQueue<EventMessage> getComponent2Dlq() {
        return startedConfiguration.getModuleConfiguration(PROCESSOR_NAME)
                                   .flatMap(m -> m.getOptionalComponent(
                                           CachingSequencedDeadLetterQueue.class,
                                           "CachingDeadLetterQueue[" + PROCESSOR_NAME + "][1]"
                                   ))
                                   .orElseThrow(() -> new IllegalStateException("Component2 DLQ not found"));
    }

    @SuppressWarnings("unchecked")
    private SequencedDeadLetterProcessor<EventMessage> getComponent1DeadLetterProcessor() {
        return startedConfiguration.getModuleConfiguration(PROCESSOR_NAME)
                                   .flatMap(m -> m.getOptionalComponent(
                                           SequencedDeadLetterProcessor.class,
                                           "EventHandlingComponent[" + PROCESSOR_NAME + "][0]"
                                   ))
                                   .orElseThrow(() -> new IllegalStateException("Component1 DLP not found"));
    }

    @SuppressWarnings("unchecked")
    private SequencedDeadLetterProcessor<EventMessage> getComponent2DeadLetterProcessor() {
        return startedConfiguration.getModuleConfiguration(PROCESSOR_NAME)
                                   .flatMap(m -> m.getOptionalComponent(
                                           SequencedDeadLetterProcessor.class,
                                           "EventHandlingComponent[" + PROCESSOR_NAME + "][1]"
                                   ))
                                   .orElseThrow(() -> new IllegalStateException("Component2 DLP not found"));
    }

    private void processDeadLettersForComponent1() {
        UnitOfWork uow = unitOfWorkFactory.create();
        uow.runOnInvocation(context -> getComponent1DeadLetterProcessor().processAny(context));
        uow.execute().join();
    }

    private void processDeadLettersForComponent2() {
        UnitOfWork uow = unitOfWorkFactory.create();
        uow.runOnInvocation(context -> getComponent2DeadLetterProcessor().processAny(context));
        uow.execute().join();
    }

    // --- Test Event Handling Component ---

    /**
     * A test {@link org.axonframework.messaging.eventhandling.EventHandlingComponent} that records handled events and
     * can be configured to fail for specific student IDs.
     */
    private static class FailingRecordingEventHandlingComponent extends SimpleEventHandlingComponent {

        private final String name;
        private final CopyOnWriteArrayList<String> successfullyHandled = new CopyOnWriteArrayList<>();
        private volatile Predicate<String> failurePredicate;

        FailingRecordingEventHandlingComponent(String name, Predicate<String> failurePredicate) {
            super(SequentialPolicy.INSTANCE);
            this.name = name;
            this.failurePredicate = failurePredicate;
            subscribe(new QualifiedName(StudentEnrolledEvent.class), this::handleStudentEnrolled);
        }

        private MessageStream.Empty<Message> handleStudentEnrolled(EventMessage event, ProcessingContext context) {
            var converter = context.component(Converter.class);
            var payload = event.payloadAs(StudentEnrolledEvent.class, converter);
            var studentId = payload.studentId();

            if (failurePredicate.test(studentId)) {
                throw new RuntimeException(name + ": Simulated failure for student: " + studentId);
            }

            successfullyHandled.add(studentId);
            return MessageStream.<Message>empty().cast();
        }

        @Nonnull
        @Override
        public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            // Use studentId as sequence identifier
            var converter = context.component(Converter.class);
            var payload = event.payloadAs(StudentEnrolledEvent.class, converter);
            return payload.studentId();
        }

        CopyOnWriteArrayList<String> successfullyHandled() {
            return successfullyHandled;
        }

        void stopFailing() {
            this.failurePredicate = studentId -> false;
        }
    }
}
