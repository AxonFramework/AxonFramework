/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.saga;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.test.matchers.Matchers;
import org.hamcrest.CoreMatchers;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.*;

import static org.axonframework.test.matchers.Matchers.*;
import static org.hamcrest.CoreMatchers.any;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTest {

    @Test
    public void testFixtureApi_WhenEventOccurs() {
        AggregateIdentifier aggregate1 = new UUIDAggregateIdentifier();
        AggregateIdentifier aggregate2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        FixtureExecutionResult validator = fixture.givenAggregate(aggregate1).published(new TriggerSagaStartEvent(
                aggregate1.asString()),
                                                                                        new TriggerExistingSagaEvent(
                                                                                                aggregate1.asString()))
                                                  .andThenAggregate(aggregate2).published(new TriggerSagaStartEvent(
                        aggregate2.asString()))

                                                  .whenAggregate(aggregate1).publishes(new TriggerSagaEndEvent(
                        aggregate1.asString()));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate2);
        validator.expectNoAssociationWith("identifier", aggregate1);
        validator.expectScheduledEventOfType(Duration.standardMinutes(10), TimerTriggeredEvent.class);
        validator.expectScheduledEventMatching(Duration.standardMinutes(10), eventWithPayload(CoreMatchers.any(
                TimerTriggeredEvent.class)));
        validator.expectScheduledEvent(Duration.standardMinutes(10), new TimerTriggeredEvent(aggregate1.asString()));
        validator.expectScheduledEventOfType(fixture.currentTime().plusMinutes(10), TimerTriggeredEvent.class);
        validator.expectScheduledEventMatching(fixture.currentTime().plusMinutes(10),
                                               eventWithPayload(CoreMatchers.any(TimerTriggeredEvent.class)));
        validator.expectScheduledEvent(fixture.currentTime().plusMinutes(10), new TimerTriggeredEvent(aggregate1
                                                                                                              .asString()));
        validator.expectDispatchedCommandsEqualTo();
        validator.expectPublishedEventsMatching(noEvents());
    }

    @Test
    public void testFixtureApi_WithApplicationEvents() {
        AggregateIdentifier aggregate1 = new UUIDAggregateIdentifier();
        AggregateIdentifier aggregate2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.givenAPublished(new TimerTriggeredEvent(new UUIDAggregateIdentifier().asString()))
               .andThenAPublished(new TimerTriggeredEvent(new UUIDAggregateIdentifier().asString()))

               .whenPublishingA(new TimerTriggeredEvent(new UUIDAggregateIdentifier().asString()))

               .expectActiveSagas(0)
               .expectNoAssociationWith("identifier", aggregate2)
               .expectNoAssociationWith("identifier", aggregate1)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo()
               .expectPublishedEvents();
    }

    @Test
    public void testFixtureApi_WhenEventIsPublishedToEventBus() {
        AggregateIdentifier aggregate1 = new UUIDAggregateIdentifier();
        AggregateIdentifier aggregate2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        FixtureExecutionResult validator = fixture.givenAggregate(aggregate1).published(new TriggerSagaStartEvent(
                aggregate1.asString()),
                                                                                        new TriggerExistingSagaEvent(
                                                                                                aggregate1.asString()))
                                                  .whenAggregate(aggregate1).publishes(new TriggerExistingSagaEvent(
                        aggregate1.asString()));

        validator.expectActiveSagas(1);
        validator.expectAssociationWith("identifier", aggregate1);
        validator.expectNoAssociationWith("identifier", aggregate2);
        validator.expectScheduledEventMatching(Duration.standardMinutes(10),
                                               Matchers.eventWithPayload(CoreMatchers.any(Object.class)));
        validator.expectDispatchedCommandsEqualTo();
        validator.expectPublishedEventsMatching(listWithAllOf(eventWithPayload(any(SagaWasTriggeredEvent.class))));
    }

    @Test
    public void testFixtureApi_WhenTimeElapses() {
        AggregateIdentifier identifier = new UUIDAggregateIdentifier();
        AggregateIdentifier identifier2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier.asString()))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2.asString()))

               .whenTimeElapses(Duration.standardMinutes(35))

               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo("Say hi!")
               .expectPublishedEventsMatching(noEvents());
    }

    @Test
    public void testFixtureApi_WhenTimeAdvances() {
        AggregateIdentifier identifier = new UUIDAggregateIdentifier();
        AggregateIdentifier identifier2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent(identifier.asString()))
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent(identifier2.asString()))

               .whenTimeAdvancesTo(new DateTime().plus(Duration.standardDays(1)))

               .expectActiveSagas(1)
               .expectAssociationWith("identifier", identifier)
               .expectNoAssociationWith("identifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommandsEqualTo("Say hi!");
    }
}
