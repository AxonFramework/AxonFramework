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

package org.axonframework.modelling.entity.annotations;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.entity.domain.pullrequest.ClosedPullRequest;
import org.axonframework.modelling.entity.domain.pullrequest.DraftPullRequest;
import org.axonframework.modelling.entity.domain.pullrequest.MergedPullRequest;
import org.axonframework.modelling.entity.domain.pullrequest.OpenedPullRequest;
import org.axonframework.modelling.entity.domain.pullrequest.PullRequest;
import org.axonframework.modelling.entity.domain.pullrequest.events.PullRequestClosed;
import org.axonframework.modelling.entity.domain.pullrequest.events.PullRequestMarkedAsReadyForReview;
import org.axonframework.modelling.entity.domain.pullrequest.events.PullRequestMerged;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the sealed hierarchy polymorphic entity support using a Pull Request state machine pattern.
 * This demonstrates how an entity can transition between different types (states) through event handling,
 * where each state is a different concrete implementation of a sealed interface.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class PullRequestStateMachineTest extends AbstractAnnotatedEntityMetamodelTest<PullRequest> {

    @Override
    protected AnnotatedEntityMetamodel<PullRequest> getMetamodel() {
        return AnnotatedEntityMetamodel.forPolymorphicType(
                PullRequest.class,
                Set.of(DraftPullRequest.class, OpenedPullRequest.class, MergedPullRequest.class, ClosedPullRequest.class),
                parameterResolverFactory,
                messageTypeResolver,
                messageConverter,
                eventConverter
        );
    }

    @Override
    protected org.axonframework.messaging.annotations.ParameterResolverFactory createParameterResolverFactory() {
        var appender = new EntityEvolvingEventAppenderForImmutableEntities();
        return new org.axonframework.messaging.annotations.MultiParameterResolverFactory(
                org.axonframework.messaging.annotations.ClasspathParameterResolverFactory.forClass(getClass()),
                new org.axonframework.messaging.annotations.SimpleResourceParameterResolverFactory(Set.of(appender))
        );
    }

    /**
     * Custom event appender that properly captures the evolved state for immutable entities (records).
     * The base class implementation doesn't capture the return value of evolve(), which is required for records.
     */
    private class EntityEvolvingEventAppenderForImmutableEntities implements org.axonframework.eventhandling.gateway.EventAppender {

        @Override
        public void append(@jakarta.annotation.Nonnull List<?> events) {
            publishedEvents.addAll(events);
            if (entityState == null) {
                return;
            }
            events.forEach(event -> {
                EventMessage eventMessage;
                if (event instanceof EventMessage) {
                    eventMessage = (EventMessage) event;
                } else {
                    eventMessage = createEvent(event);
                }
                // Capture the evolved state for immutable entities
                entityState = metamodel.evolve(entityState, eventMessage, StubProcessingContext.forMessage(eventMessage));
            });
        }

        @Override
        public void describeTo(@jakarta.annotation.Nonnull org.axonframework.common.infra.ComponentDescriptor descriptor) {
            throw new UnsupportedOperationException("Not required for testing");
        }
    }

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        void transitionsFromDraftToOpened() {
            // given
            entityState = new DraftPullRequest("pr-123", "Add feature X", "alice");

            // when
            var event = new PullRequestMarkedAsReadyForReview("pr-123", "Add feature X", "alice");
            entityState = metamodel.evolve(entityState, createEvent(event), StubProcessingContext.forMessage(createEvent(event)));

            // then
            assertThat(entityState).isInstanceOf(OpenedPullRequest.class);
            OpenedPullRequest openedPR = (OpenedPullRequest) entityState;
            assertThat(openedPR.getId()).isEqualTo("pr-123");
            assertThat(openedPR.getTitle()).isEqualTo("Add feature X");
            assertThat(openedPR.getAuthor()).isEqualTo("alice");
        }

        @Test
        void transitionsFromOpenedToMerged() {
            // given
            entityState = new OpenedPullRequest("pr-123", "Add feature X", "alice");

            // when
            var mergedAt = Instant.now();
            var event = new PullRequestMerged("pr-123", "Add feature X", "alice", "bob", mergedAt);
            entityState = metamodel.evolve(entityState, createEvent(event), StubProcessingContext.forMessage(createEvent(event)));

            // then
            assertThat(entityState).isInstanceOf(MergedPullRequest.class);
            MergedPullRequest mergedPR = (MergedPullRequest) entityState;
            assertThat(mergedPR.getId()).isEqualTo("pr-123");
            assertThat(mergedPR.getTitle()).isEqualTo("Add feature X");
            assertThat(mergedPR.getAuthor()).isEqualTo("alice");
            assertThat(mergedPR.mergedBy()).isEqualTo("bob");
            assertThat(mergedPR.mergedAt()).isEqualTo(mergedAt);
        }

        @Test
        void transitionsFromOpenedToClosed() {
            // given
            entityState = new OpenedPullRequest("pr-123", "Add feature X", "alice");

            // when
            var closedAt = Instant.now();
            var event = new PullRequestClosed("pr-123", "Add feature X", "alice", "alice", closedAt);
            entityState = metamodel.evolve(entityState, createEvent(event), StubProcessingContext.forMessage(createEvent(event)));

            // then
            assertThat(entityState).isInstanceOf(ClosedPullRequest.class);
            ClosedPullRequest closedPR = (ClosedPullRequest) entityState;
            assertThat(closedPR.getId()).isEqualTo("pr-123");
            assertThat(closedPR.getTitle()).isEqualTo("Add feature X");
            assertThat(closedPR.getAuthor()).isEqualTo("alice");
            assertThat(closedPR.closedBy()).isEqualTo("alice");
            assertThat(closedPR.closedAt()).isEqualTo(closedAt);
        }

        @Test
        void completesFullLifecycleDraftToOpenedToMerged() {
            // given
            entityState = new DraftPullRequest("pr-123", "Add feature X", "alice");

            // when - mark as ready for review
            var readyEvent = new PullRequestMarkedAsReadyForReview("pr-123", "Add feature X", "alice");
            entityState = metamodel.evolve(entityState, createEvent(readyEvent), StubProcessingContext.forMessage(createEvent(readyEvent)));

            // then - should be opened
            assertThat(entityState).isInstanceOf(OpenedPullRequest.class);

            // when - merge
            var mergedAt = Instant.now();
            var mergedEvent = new PullRequestMerged("pr-123", "Add feature X", "alice", "bob", mergedAt);
            entityState = metamodel.evolve(entityState, createEvent(mergedEvent), StubProcessingContext.forMessage(createEvent(mergedEvent)));

            // then - should be merged (terminal state)
            assertThat(entityState).isInstanceOf(MergedPullRequest.class);
            MergedPullRequest mergedPR = (MergedPullRequest) entityState;
            assertThat(mergedPR.getId()).isEqualTo("pr-123");
            assertThat(mergedPR.getTitle()).isEqualTo("Add feature X");
            assertThat(mergedPR.getAuthor()).isEqualTo("alice");
            assertThat(mergedPR.mergedBy()).isEqualTo("bob");
            assertThat(mergedPR.mergedAt()).isEqualTo(mergedAt);
        }

        @Test
        void mergedPullRequestRemainsInMergedState() {
            // given - a merged PR (terminal state)
            var mergedAt = Instant.now();
            entityState = new MergedPullRequest("pr-123", "Add feature X", "alice", "bob", mergedAt);

            // when - trying to apply another event (terminal state should not change)
            var anotherEvent = new PullRequestClosed("pr-123", "Add feature X", "alice", "alice", Instant.now());
            PullRequest result = metamodel.evolve(entityState, createEvent(anotherEvent), StubProcessingContext.forMessage(createEvent(anotherEvent)));

            // then - should remain in merged state (no handler for this event)
            assertThat(result).isInstanceOf(MergedPullRequest.class);
            assertThat(result).isSameAs(entityState);
        }

        @Test
        void closedPullRequestRemainsInClosedState() {
            // given - a closed PR (terminal state)
            var closedAt = Instant.now();
            entityState = new ClosedPullRequest("pr-123", "Add feature X", "alice", "alice", closedAt);

            // when - trying to apply another event (terminal state should not change)
            var anotherEvent = new PullRequestMerged("pr-123", "Add feature X", "alice", "bob", Instant.now());
            PullRequest result = metamodel.evolve(entityState, createEvent(anotherEvent), StubProcessingContext.forMessage(createEvent(anotherEvent)));

            // then - should remain in closed state (no handler for this event)
            assertThat(result).isInstanceOf(ClosedPullRequest.class);
            assertThat(result).isSameAs(entityState);
        }
    }

    @Nested
    @DisplayName("Sealed Hierarchy Discovery Tests")
    class SealedHierarchyDiscoveryTests {

        @Test
        void discoversEventHandlersOnConcreteTypes() {
            // given
            entityState = new DraftPullRequest("pr-123", "Add feature X", "alice");

            // when - applying event that has handler on DraftPullRequest
            var event = new PullRequestMarkedAsReadyForReview("pr-123", "Add feature X", "alice");
            entityState = metamodel.evolve(entityState, createEvent(event), StubProcessingContext.forMessage(createEvent(event)));

            // then - handler was found and executed, transitioning to OpenedPullRequest
            assertThat(entityState).isInstanceOf(OpenedPullRequest.class);
        }

        @Test
        void discoversEventHandlersOnMultipleConcreteTypes() {
            // given - OpenedPullRequest has two event handlers (for merge and close)
            entityState = new OpenedPullRequest("pr-123", "Add feature X", "alice");

            // when - applying merge event
            var mergedAt = Instant.now();
            var mergeEvent = new PullRequestMerged("pr-123", "Add feature X", "alice", "bob", mergedAt);
            PullRequest mergedState = metamodel.evolve(entityState, createEvent(mergeEvent), StubProcessingContext.forMessage(createEvent(mergeEvent)));

            // then - merge handler was found
            assertThat(mergedState).isInstanceOf(MergedPullRequest.class);

            // when - starting from opened again and applying close event
            entityState = new OpenedPullRequest("pr-123", "Add feature X", "alice");
            var closedAt = Instant.now();
            var closeEvent = new PullRequestClosed("pr-123", "Add feature X", "alice", "alice", closedAt);
            PullRequest closedState = metamodel.evolve(entityState, createEvent(closeEvent), StubProcessingContext.forMessage(createEvent(closeEvent)));

            // then - close handler was found
            assertThat(closedState).isInstanceOf(ClosedPullRequest.class);
        }
    }
}
