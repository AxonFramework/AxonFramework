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
import org.axonframework.messaging.eventhandling.sequencing.SequentialPolicy;
import org.junit.jupiter.api.*;

import java.util.concurrent.CopyOnWriteArrayList;
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

    private FailingRecordingEventHandlingComponent[] components;

    @BeforeEach
    void setUpComponents() {
        purgeEventStorage();

        components = new FailingRecordingEventHandlingComponent[]{
                new FailingRecordingEventHandlingComponent("component0", studentId -> studentId.contains("-fail-0")),
                new FailingRecordingEventHandlingComponent("component1", studentId -> studentId.contains("-fail-1"))
        };
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        var processorModule = EventProcessorModule
                .pooledStreaming(PROCESSOR_NAME)
                .eventHandlingComponents(c -> c
                        .declarative(cfg -> components[0])
                        .declarative(cfg -> components[1]))
                .customized((cfg, c) -> c.deadLetterQueue(dlq -> dlq.enabled()));

        return configurer.messaging(
                messaging -> messaging.eventProcessing(
                        ep -> ep.pooledStreaming(ps -> ps.processor(processorModule))
                )
        );
    }

    @Nested
    @DisplayName("Independent DLQs per Component")
    class IndependentDlqTests {

        @Test
        @DisplayName("When component0 fails an event, it should go to component0's DLQ only")
        void failedEventInComponent0ShouldOnlyGoToComponent0Dlq() {
            // given
            startApp();
            var failingStudentId = createId("student-fail-0");
            var successStudentId = createId("student-ok");
            var courseId = createId("course");

            // when
            studentEnrolledToCourse(failingStudentId, courseId);
            studentEnrolledToCourse(successStudentId, courseId);

            // then
            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudentId).join()).isTrue();
                assertThat(getDlq(0).size().join()).isEqualTo(1L);

                assertThat(getDlq(1).contains(failingStudentId).join()).isFalse();
                assertThat(getDlq(1).size().join()).isEqualTo(0L);

                assertThat(components[0].successfullyHandled()).containsOnly(successStudentId);
                assertThat(components[1].successfullyHandled()).contains(failingStudentId, successStudentId);
            });
        }
    }

    @Nested
    @DisplayName("Sequence Isolation")
    class SequenceIsolationTests {

        @Test
        @DisplayName("Subsequent events for a failed sequence (studentId) should be enqueued in the same component's DLQ")
        void subsequentEventsForFailedSequenceShouldBeEnqueuedInSameComponentDlq() {
            // given
            startApp();
            var failingStudentId = createId("student-fail-0");
            var courseId1 = createId("course-1");
            var courseId2 = createId("course-2");
            var courseId3 = createId("course-3");

            // when - first event fails, subsequent events for same sequence should be enqueued
            studentEnrolledToCourse(failingStudentId, courseId1);
            studentEnrolledToCourse(failingStudentId, courseId2);
            studentEnrolledToCourse(failingStudentId, courseId3);

            // then
            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudentId).join()).isTrue();
                assertThat(getDlq(0).sequenceSize(failingStudentId).join()).isEqualTo(3L);

                assertThat(getDlq(1).contains(failingStudentId).join()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("Both Components Can Fail Independently")
    class BothComponentsFailIndependentlyTests {

        @Test
        @DisplayName("Component0 fails on student-0, Component1 fails on student-1 - each goes to correct DLQ")
        void bothComponentsCanFailOnDifferentSequences() {
            // given
            startApp();
            var failingStudent0 = createId("student-fail-0");
            var failingStudent1 = createId("student-fail-1");
            var successStudent = createId("student-ok");
            var courseId = createId("course");

            // when
            studentEnrolledToCourse(failingStudent0, courseId);
            studentEnrolledToCourse(failingStudent1, courseId);
            studentEnrolledToCourse(successStudent, courseId);

            // then
            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudent0).join()).isTrue();
                assertThat(getDlq(0).contains(failingStudent1).join()).isFalse();

                assertThat(getDlq(1).contains(failingStudent1).join()).isTrue();
                assertThat(getDlq(1).contains(failingStudent0).join()).isFalse();

                assertThat(components[0].successfullyHandled()).contains(successStudent);
                assertThat(components[1].successfullyHandled()).contains(successStudent);
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
            var failingStudent0 = createId("student-fail-0");
            var failingStudent1 = createId("student-fail-1");
            var courseId = createId("course");

            // and - both components have dead letters
            studentEnrolledToCourse(failingStudent0, courseId);
            studentEnrolledToCourse(failingStudent1, courseId);

            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudent0).join()).isTrue();
                assertThat(getDlq(0).size().join()).isEqualTo(1L);

                assertThat(getDlq(1).contains(failingStudent1).join()).isTrue();
                assertThat(getDlq(1).size().join()).isEqualTo(1L);
            });

            // and - fix component0's failure condition
            components[0].stopFailing();

            // when - process dead letters from component0
            processDeadLetters(0);

            // then - component0's DLQ should be empty, component1's DLQ unchanged
            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudent0).join()).isFalse();
                assertThat(getDlq(0).size().join()).isEqualTo(0L);

                assertThat(getDlq(1).contains(failingStudent1).join()).isTrue();
                assertThat(getDlq(1).size().join()).isEqualTo(1L);

                assertThat(components[0].successfullyHandled()).contains(failingStudent0);
            });
        }

        @Test
        @DisplayName("Should be able to process dead letters from both components independently")
        void shouldProcessDeadLettersFromBothComponentsIndependently() {
            // given
            startApp();
            var failingStudent0 = createId("student-fail-0");
            var failingStudent1 = createId("student-fail-1");
            var courseId = createId("course");

            // and - both components have dead letters
            studentEnrolledToCourse(failingStudent0, courseId);
            studentEnrolledToCourse(failingStudent1, courseId);

            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudent0).join()).isTrue();
                assertThat(getDlq(0).size().join()).isEqualTo(1L);

                assertThat(getDlq(1).contains(failingStudent1).join()).isTrue();
                assertThat(getDlq(1).size().join()).isEqualTo(1L);
            });

            // and - fix both components' failure conditions
            components[0].stopFailing();
            components[1].stopFailing();

            // when - process dead letters from both components
            processDeadLetters(0);
            processDeadLetters(1);

            // then - both DLQs should be empty
            await().untilAsserted(() -> {
                assertThat(getDlq(0).contains(failingStudent0).join()).isFalse();
                assertThat(getDlq(0).size().join()).isEqualTo(0L);

                assertThat(getDlq(1).contains(failingStudent1).join()).isFalse();
                assertThat(getDlq(1).size().join()).isEqualTo(0L);

                assertThat(components[0].successfullyHandled()).contains(failingStudent0);
                assertThat(components[1].successfullyHandled()).contains(failingStudent1);
            });
        }
    }

    // --- Helper Methods ---

    @SuppressWarnings("unchecked")
    private CachingSequencedDeadLetterQueue<EventMessage> getDlq(int componentIndex) {
        return startedConfiguration.getModuleConfiguration(PROCESSOR_NAME)
                                   .flatMap(m -> m.getOptionalComponent(
                                           CachingSequencedDeadLetterQueue.class,
                                           "CachingDeadLetterQueue[" + PROCESSOR_NAME + "][" + componentIndex + "]"
                                   ))
                                   .orElseThrow(() -> new IllegalStateException(
                                           "DLQ not found for component " + componentIndex));
    }

    @SuppressWarnings("unchecked")
    private SequencedDeadLetterProcessor<EventMessage> getDeadLetterProcessor(int componentIndex) {
        return startedConfiguration.getModuleConfiguration(PROCESSOR_NAME)
                                   .flatMap(m -> m.getOptionalComponent(
                                           SequencedDeadLetterProcessor.class,
                                           "EventHandlingComponent[" + PROCESSOR_NAME + "][" + componentIndex + "]"
                                   ))
                                   .orElseThrow(() -> new IllegalStateException(
                                           "DeadLetterProcessor not found for component " + componentIndex));
    }

    private void processDeadLetters(int componentIndex) {
        UnitOfWork uow = unitOfWorkFactory.create();
        uow.runOnInvocation(context -> getDeadLetterProcessor(componentIndex).processAny(context));
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
            super(name, SequentialPolicy.INSTANCE);
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
