/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventStreamNotFoundException;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/simple-disruptor-context.xml"})
public class DisruptorContextConfigurationTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private Snapshotter mockSnaphotter;

    @Before
    public void setUp() throws Exception {
        Mockito.reset(mockSnaphotter);
    }

    // Tests a scenario where the order in which command bus and event bus are declared could cause a circular dependency error in Spring
    @Test
    public void testCommandBus() throws Exception {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCreateCommand("test")));
    }

    @Test
    public void testSnapshotTriggeredAfterFiringCommands() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCreateCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")));

        FutureCallback<Object> callback = new FutureCallback<>();
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("snapshottest")), callback);
        callback.awaitCompletion(1, TimeUnit.SECONDS);

        Mockito.verify(mockSnaphotter).scheduleSnapshot("MyAggregate", "snapshottest");
    }

    public static class MyAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String id;

        public MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(StubCreateCommand command) {
            apply(new SimpleEvent(command.id));
        }

        @CommandHandler
        public MyAggregate(StubCommand command) {
            apply(new SimpleEvent(command.id));
        }

        @EventSourcingHandler
        public void on(SimpleEvent event) {
            this.id = event.getAggregateIdentifier();
        }
    }

    public static class StubCreateCommand {

        @TargetAggregateIdentifier
        private final String id;

        public StubCreateCommand(String id) {
            this.id = id;
        }
    }

    public static class StubCommand {

        @TargetAggregateIdentifier
        private final String id;

        public StubCommand(String id) {
            this.id = id;
        }
    }

    public static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage> storedEvents = new ConcurrentHashMap<>();

        @Override
        public void appendEvents(String type, DomainEventStream events) {
            if (!events.hasNext()) {
                return;
            }
            String key = events.peek().getAggregateIdentifier().toString();
            DomainEventMessage<?> lastEvent = null;
            while (events.hasNext()) {
                lastEvent = events.next();
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public DomainEventStream readEvents(String type, String identifier) {
            DomainEventMessage message = storedEvents.get(identifier.toString());
            if (message == null) {
                throw new EventStreamNotFoundException(type, identifier);
            }
            return new SimpleDomainEventStream(Collections.singletonList(message));
        }

        @Override
        public DomainEventStream readEvents(String type, String identifier, long firstSequenceNumber,
                                            long lastSequenceNumber) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}
