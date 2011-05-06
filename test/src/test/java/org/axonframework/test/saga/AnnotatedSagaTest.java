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
import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.hamcrest.CoreMatchers;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.*;

import static org.axonframework.test.matchers.Matchers.noEvents;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaTest {

    @Test
    public void testFixtureApi_WhenEventOccurs() {
        AggregateIdentifier aggregate1 = new UUIDAggregateIdentifier();
        AggregateIdentifier aggregate2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.givenAggregate(aggregate1).published(new TriggerSagaStartEvent(),
                                                     new TriggerExistingSagaEvent())
               .andThenAggregate(aggregate2).published(new TriggerSagaStartEvent())

               .whenAggregate(aggregate1).publishes(new TriggerSagaEndEvent())

               .expectActiveSagas(1)
               .expectAssociationWith("aggregateIdentifier", aggregate2)
               .expectNoAssociationWith("aggregateIdentifier", aggregate1)
               .expectScheduledEvent(Duration.standardMinutes(10), TimerTriggeredEvent.class)
               .expectScheduledEvent(Duration.standardMinutes(10), CoreMatchers.any(TimerTriggeredEvent.class))
               .expectScheduledEvent(Duration.standardMinutes(10), new TimerTriggeredEvent(null, aggregate1))
               .expectScheduledEvent(fixture.currentTime().plusMinutes(10), TimerTriggeredEvent.class)
               .expectScheduledEvent(fixture.currentTime().plusMinutes(10), CoreMatchers.any(TimerTriggeredEvent.class))
               .expectScheduledEvent(fixture.currentTime().plusMinutes(10), new TimerTriggeredEvent(null, aggregate1))
               .expectDispatchedCommands()
               .expectPublishedEvents(noEvents());
    }

    @Test
    public void testFixtureApi_WithApplicationEvents() {
        AggregateIdentifier aggregate1 = new UUIDAggregateIdentifier();
        AggregateIdentifier aggregate2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.givenAPublished(new TimerTriggeredEvent("Fake source", new UUIDAggregateIdentifier()))
               .andThenAPublished(new TimerTriggeredEvent("Another source", new UUIDAggregateIdentifier()))

               .whenPublishingA(new TimerTriggeredEvent("Yet another source", new UUIDAggregateIdentifier()))

               .expectActiveSagas(0)
               .expectNoAssociationWith("aggregateIdentifier", aggregate2)
               .expectNoAssociationWith("aggregateIdentifier", aggregate1)
               .expectNoScheduledEvents()
               .expectDispatchedCommands()
               .expectPublishedEvents();
    }

    @Test
    public void testFixtureApi_WhenEventIsPublishedToEventBus() {
        AggregateIdentifier aggregate1 = new UUIDAggregateIdentifier();
        AggregateIdentifier aggregate2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);
        fixture.givenAggregate(aggregate1).published(new TriggerSagaStartEvent(),
                                                     new TriggerExistingSagaEvent())
               .whenAggregate(aggregate1).publishes(new TriggerExistingSagaEvent())

               .expectActiveSagas(1)
               .expectAssociationWith("aggregateIdentifier", aggregate1)
               .expectNoAssociationWith("aggregateIdentifier", aggregate2)
               .expectScheduledEvent(Duration.standardMinutes(10), CoreMatchers.any(ApplicationEvent.class))
               .expectDispatchedCommands()
               .expectPublishedEvents(new SagaWasTriggeredEvent(null));
    }

    @Test
    public void testFixtureApi_WhenTimeElapses() {
        AggregateIdentifier identifier = new UUIDAggregateIdentifier();
        AggregateIdentifier identifier2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent())
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent())

               .whenTimeElapses(Duration.standardMinutes(35))

               .expectActiveSagas(1)
               .expectAssociationWith("aggregateIdentifier", identifier)
               .expectNoAssociationWith("aggregateIdentifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommands("Say hi!")
               .expectPublishedEvents(noEvents());
    }

    @Test
    public void testFixtureApi_WhenTimeAdvances() {
        AggregateIdentifier identifier = new UUIDAggregateIdentifier();
        AggregateIdentifier identifier2 = new UUIDAggregateIdentifier();
        AnnotatedSagaTestFixture fixture = new AnnotatedSagaTestFixture(StubSaga.class);

        fixture.givenAggregate(identifier).published(new TriggerSagaStartEvent())
               .andThenAggregate(identifier2).published(new TriggerExistingSagaEvent())

               .whenTimeAdvancesTo(new DateTime().plus(Duration.standardDays(1)))

               .expectActiveSagas(1)
               .expectAssociationWith("aggregateIdentifier", identifier)
               .expectNoAssociationWith("aggregateIdentifier", identifier2)
               .expectNoScheduledEvents()
               .expectDispatchedCommands("Say hi!");
    }
}
