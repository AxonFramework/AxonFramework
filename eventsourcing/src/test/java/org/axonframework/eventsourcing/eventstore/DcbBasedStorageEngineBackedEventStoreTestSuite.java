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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Extension of {@link StorageEngineBackedEventStoreTestSuite} with tests that require Dynamic Consistency Boundary
 * (DCB) support. These tests validate criteria-based conflict detection, ORIGIN-based uniqueness checks, and
 * event-type narrowing — features that are not supported by aggregate-based storage engines.
 * <p>
 * DCB-capable engines (e.g., in-memory, Axon Server with DCB context) should extend this suite.
 * Aggregate-based engines should extend {@link AggregateBasedStorageEngineBackedEventStoreTestSuite} instead.
 *
 * @author Mateusz Nowak
 * @see StorageEngineBackedEventStoreTestSuite
 * @see AggregateBasedStorageEngineBackedEventStoreTestSuite
 */
public abstract class DcbBasedStorageEngineBackedEventStoreTestSuite<E extends EventStorageEngine>
        extends StorageEngineBackedEventStoreTestSuite<E> {

    @Nested
    protected class OverrideAppendCondition {

        /**
         * Uniqueness without sourcing — ensuring a course name is unique.
         * <p>
         * When creating a course, we want to guarantee no course with the same name already exists.
         * We don't need to source any events — we just need to check that no {@link CourseCreated}
         * event with a matching {@code courseName} tag has ever been appended
         * (since {@link ConsistencyMarker#ORIGIN}).
         * <p>
         * The first creation succeeds. A second attempt with the same name is rejected because a
         * matching event now exists since ORIGIN.
         */
        @Test
        protected void shouldAppendWithOverriddenConditionWithoutSourcing() {
            // given - a unique course name
            String courseName = UUID.randomUUID().toString();
            Tag courseNameTag = new Tag("courseName", courseName);
            EventCriteria courseNameCriteria = EventCriteria.havingTags(courseNameTag);

            // when - first course creation: no sourcing, just enforce name uniqueness
            UnitOfWork uow1 = unitOfWork();

            uow1.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                // no source() — we don't need state, just uniqueness
                tx.overrideAppendCondition(condition ->
                        // condition is AppendCondition.none(); override with ORIGIN-based check
                        AppendCondition.withCriteria(courseNameCriteria)
                );
                tx.appendEvent(message(new CourseCreated(courseName)));
            });

            execute(uow1);

            // then - event was persisted (sourcing finds 1 event with the courseName tag)
            assertThat(sourceCount(SourcingCondition.conditionFor(courseNameCriteria))).isEqualTo(1);

            // and - second attempt to create a course with the same name should fail
            // (CourseCreated with matching courseName tag now exists since ORIGIN)
            UnitOfWork uow2 = unitOfWork();

            uow2.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                tx.overrideAppendCondition(condition ->
                        AppendCondition.withCriteria(courseNameCriteria)
                );
                tx.appendEvent(message(new CourseCreated(courseName)));
            });

            assertThatThrownBy(() -> execute(uow2))
                    .isInstanceOf(AppendEventsTransactionRejectedException.class);
        }

        /**
         * Narrowing criteria — only conflict-relevant event types.
         * <p>
         * When handling a "subscribe student to course" command, we source both
         * {@link StudentSubscribedToCourse} and {@link StudentUnsubscribedFromCourse} events to build
         * state (enrolled count, remaining places). However, only {@code StudentSubscribedToCourse}
         * events can cause a conflict (e.g., duplicate subscription, no remaining places).
         * {@code StudentUnsubscribedFromCourse} events only broaden state (free up places) and never
         * conflict.
         * <p>
         * By narrowing the append condition to only check for {@code StudentSubscribedToCourse},
         * a concurrent unsubscription does not cause a false conflict.
         */
        @Test
        protected void narrowedCriteriaShouldAvoidFalseConflict() {
            // given - a course with subscriptions
            String courseId = UUID.randomUUID().toString();
            Tag courseIdTag = new Tag("courseId", courseId);
            EventCriteria subscribedAndUnsubscribedEvents = EventCriteria.havingTags(courseIdTag)
                    .andBeingOneOfTypes(RESOLVER, StudentSubscribedToCourse.class,
                                        StudentUnsubscribedFromCourse.class);
            EventCriteria onlySubscribedEvents = EventCriteria.havingTags(courseIdTag)
                    .andBeingOneOfTypes(RESOLVER, StudentSubscribedToCourse.class);

            // pre-populate: one student already subscribed
            UnitOfWork setup = unitOfWork();
            EventMessage initialSubscription = message(
                    new StudentSubscribedToCourse(courseId, "student-initial")
            );
            setup.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                tx.appendEvent(initialSubscription);
            });
            execute(setup);

            CountDownLatch sourcingFinished = new CountDownLatch(2);
            CountDownLatch latch1 = new CountDownLatch(1);
            CountDownLatch latch2 = new CountDownLatch(1);

            UnitOfWork uow1 = unitOfWork();
            UnitOfWork uow2 = unitOfWork();

            // TX1: subscribe a new student — source all subscription events for state,
            //      but narrow conflict detection to only StudentSubscribedToCourse
            EventMessage newSubscription = message(
                    new StudentSubscribedToCourse(courseId, "student-new")
            );
            uow1.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                MessageStream<? extends EventMessage> sourcing = tx.source(
                        SourcingCondition.conditionFor(subscribedAndUnsubscribedEvents)
                );
                // consume the sourced events (1 initial subscription)
                assertThat(sourcing.reduce(0, (c, m) -> ++c).join()).isEqualTo(1);

                // only StudentSubscribedToCourse can cause conflicts;
                // StudentUnsubscribedFromCourse just makes more places available
                tx.overrideAppendCondition(condition ->
                        condition.replacingCriteria(onlySubscribedEvents)
                );

                sourcingFinished.countDown();
                awaitLatch(latch1);

                tx.appendEvent(newSubscription);
            });

            // TX2: unsubscribe a student — this is a concurrent unsubscription
            EventMessage unsubscription = message(
                    new StudentUnsubscribedFromCourse(courseId, "student-initial")
            );
            uow2.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                MessageStream<? extends EventMessage> sourcing = tx.source(
                        SourcingCondition.conditionFor(subscribedAndUnsubscribedEvents)
                );
                assertThat(sourcing.reduce(0, (c, m) -> ++c).join()).isEqualTo(1);

                sourcingFinished.countDown();
                awaitLatch(latch2);

                tx.appendEvent(unsubscription);
            });

            // when - TX2 commits first (appends StudentUnsubscribedFromCourse), then TX1 commits
            CompletableFuture<Void> execute1 = CompletableFuture.runAsync(() -> execute(uow1));
            CompletableFuture<Void> execute2 = CompletableFuture.runAsync(() -> execute(uow2));

            awaitLatch(sourcingFinished);

            // let TX2 (unsubscription) commit first
            latch2.countDown();
            assertDoesNotThrow(execute2::join);

            // TX1 should also succeed: its narrowed criteria (StudentSubscribedToCourse only)
            // does not match the StudentUnsubscribedFromCourse appended by TX2
            latch1.countDown();
            assertDoesNotThrow(execute1::join);
        }

        /**
         * Bypassing conflict detection — creating two courses with the same name.
         * <p>
         * Demonstrates that returning {@link AppendCondition#none()} from the override completely
         * bypasses conflict detection. Here, two courses are created with the same name — normally
         * this would be rejected (as shown in
         * {@link #shouldAppendWithOverriddenConditionWithoutSourcing()}), but with the override
         * returning {@code none()}, both succeed.
         */
        @Test
        protected void overrideReturningNoneShouldBypassConflictDetection() {
            // given - a course name and first course already created with uniqueness check
            String courseName = UUID.randomUUID().toString();
            Tag courseNameTag = new Tag("courseName", courseName);
            EventCriteria courseNameCriteria = EventCriteria.havingTags(courseNameTag);

            UnitOfWork setup = unitOfWork();
            setup.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                tx.overrideAppendCondition(condition ->
                        AppendCondition.withCriteria(courseNameCriteria)
                );
                tx.appendEvent(message(new CourseCreated(courseName)));
            });
            execute(setup);

            // when - second course creation with same name, but override returns none()
            UnitOfWork uow = unitOfWork();
            uow.runOnInvocation(pc -> {
                EventStoreTransaction tx = eventStore.transaction(pc);
                // override returns none() — bypasses the uniqueness check entirely
                tx.overrideAppendCondition(condition -> AppendCondition.none());
                tx.appendEvent(message(new CourseCreated(courseName)));
            });

            // then - succeeds despite a CourseCreated with the same name already existing
            assertDoesNotThrow(() -> execute(uow));

            // verify both events are persisted
            assertThat(sourceCount(SourcingCondition.conditionFor(courseNameCriteria))).isEqualTo(2);
        }
    }

    @Event
    record CourseCreated(@EventTag String courseName) {}

    @Event
    record StudentSubscribedToCourse(@EventTag String courseId, @EventTag String studentId) {}

    @Event
    record StudentUnsubscribedFromCourse(@EventTag String courseId, @EventTag String studentId) {}
}
