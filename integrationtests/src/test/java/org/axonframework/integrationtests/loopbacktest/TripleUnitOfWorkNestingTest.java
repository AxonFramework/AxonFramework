/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.integrationtests.loopbacktest;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.DomainEventStream;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventstore.EventStore;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.repository.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Rather extensive test that shows the existence of <a href="http://code.google.com/p/axonframework/issues/detail?id=204">issue
 * #204</a>. This bug causes application to hang when using triply nested unit of work. The cleanup callbacks in the
 * 3rd
 * level of the hierarchy wasn't called, causing reentrant locks to stay active.
 * <p/>
 * This test does nesting to up to 4 levels, with 2 units of work on the same (second) level.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/contexts/triple_uow_nesting_test.xml"})
public class TripleUnitOfWorkNestingTest implements EventListener {

    private static String aggregateAIdentifier = "A";
    private static String aggregateBIdentifier = "B";
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    @Autowired
    private CommandBus commandBus;
    @Autowired
    private EventStore eventStore;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private List<EventMessage<?>> handledMessages;

    @Before
    public void setUp() throws Exception {
        handledMessages = new ArrayList<>();
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testLoopbackScenario() throws InterruptedException {
        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionAttribute());
        eventStore.appendEvents(Arrays.asList(new DomainEventMessage[]{new GenericDomainEventMessage<>(
                aggregateAIdentifier,
                (long) 0,
                new CreateEvent(aggregateAIdentifier),
                MetaData.emptyInstance())}));
        eventStore.appendEvents(Arrays.asList(new DomainEventMessage[]{new GenericDomainEventMessage<>(
                aggregateBIdentifier,
                (long) 0,
                new CreateEvent(aggregateBIdentifier),
                MetaData.emptyInstance())}));
        transactionManager.commit(tx);
        assertEquals(1, toList(eventStore.readEvents(aggregateAIdentifier)).size());
        assertEquals(1, toList(eventStore.readEvents(aggregateBIdentifier)).size());
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("hello"));

        final List<DomainEventMessage> messages = toList(eventStore.readEvents(aggregateAIdentifier));
        assertEquals(5, messages.size());
        assertEquals(2, toList(eventStore.readEvents(aggregateBIdentifier)).size());

        for (int t = 0; t < 10; t++) {
            executor.submit(new SendCommandTask());
        }
        executor.shutdown();
        assertTrue("Commands did not execute in a reasonable time. Are there any unreleased locks remaining?",
                   executor.awaitTermination(2, TimeUnit.SECONDS));

        assertEquals(new HashSet<>(handledMessages).size(), handledMessages.size());
    }

    private List<DomainEventMessage> toList(DomainEventStream eventStream) {
        List<DomainEventMessage> events = new ArrayList<>();
        while (eventStream.hasNext()) {
            events.add(eventStream.next());
        }
        return events;
    }

    @Override
    public void handle(EventMessage event) {
        this.handledMessages.add(event);
    }

    public static class MyCommandHandler {

        @Autowired
        @Qualifier("aggregateARepository")
        private Repository<AggregateA> aggregateARepository;

        @Autowired
        @Qualifier("aggregateBRepository")
        private Repository<AggregateB> aggregateBRepository;

        @Autowired
        private EventBus eventBus;

        @CommandHandler
        public void handle(String stringCommand) {
            eventBus.publish(new GenericEventMessage<>("Mock"));
            aggregateARepository.load(aggregateAIdentifier).doSomething(stringCommand);
        }

        @CommandHandler
        public void handle(Object objectCommand) {
            eventBus.publish(new GenericEventMessage<>("Mock"));
            aggregateBRepository.load(aggregateBIdentifier).doSomething();
        }
    }

    public static class LoopbackSaga {

        @Autowired
        private CommandBus commandBus;

        @EventHandler
        public void handle(FirstEvent event) {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage(new Object()));
            commandBus.dispatch(GenericCommandMessage.asCommandMessage("first"));
        }

        @EventHandler
        public void handle(SecondEvent event) {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage("second"));
        }

        @EventHandler
        public void handle(ThirdEvent event) {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage("third"));
        }
    }

    public static class FirstEvent {

    }

    public static class SecondEvent {

    }

    public static class ThirdEvent {

    }

    public static class AggregateA extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String identifier;

        public AggregateA() {
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @EventSourcingHandler
        private void handle(CreateEvent event) {
            this.identifier = event.getIdentifier();
        }

        public void doSomething(String stringCommand) {
            if ("hello".equalsIgnoreCase(stringCommand)) {
                apply(new FirstEvent());
            } else if ("second".equalsIgnoreCase(stringCommand)) {
                apply(new ThirdEvent());
            } else {
                apply(new StubDomainEvent());
            }
        }
    }

    public static class AggregateB extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String identifier;

        public AggregateB() {
        }

        @EventSourcingHandler
        private void handle(CreateEvent event) {
            this.identifier = event.getIdentifier();
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        public void doSomething() {
            apply(new SecondEvent());
        }
    }

    private static class CreateEvent {

        private String identifier;

        public CreateEvent(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    private class SendCommandTask implements Runnable {

        @Override
        public void run() {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage("hello"));
        }
    }
}
