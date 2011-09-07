package org.axonframework.unitofwork.nesting;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Rather extensive test that shows the existence of <a href="http://code.google.com/p/axonframework/issues/detail?id=204">issue
 * #204</a>. This bug causes application to hang when using triply nested unit of work. The cleanup callbacks in the 3rd
 * level of the hierarchy wasn't called, causing reentrant locks to stay active.
 * <p/>
 * This test does nesting to up to 4 levels, with 2 units of work on the same (second) level.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/contexts/triple_uow_nesting_test.xml"})
public class TripleUnitOfWorkNestingTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static AggregateIdentifier aggregateAIdentifier = new StringAggregateIdentifier("A");
    private static AggregateIdentifier aggregateBIdentifier = new StringAggregateIdentifier("B");

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Test
    public void testLoopbackScenario() throws InterruptedException {
        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionAttribute());
        eventStore.appendEvents("AggregateA", new SimpleDomainEventStream(new CreateEvent(aggregateAIdentifier)));
        eventStore.appendEvents("AggregateB", new SimpleDomainEventStream(new CreateEvent(aggregateBIdentifier)));
        transactionManager.commit(tx);
        commandBus.dispatch("hello");

        assertEquals(5, toList(eventStore.readEvents("AggregateA", aggregateAIdentifier)).size());
        assertEquals(2, toList(eventStore.readEvents("AggregateB", aggregateBIdentifier)).size());

        for (int t = 0; t < 10; t++) {
            executor.submit(new SendCommandTask());
        }
        executor.shutdown();
        assertTrue("Commands did not execute in a reasonable time. Are there any unreleased locks remaining?",
                   executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    private List<DomainEvent> toList(DomainEventStream eventStream) {
        List<DomainEvent> events = new ArrayList<DomainEvent>();
        while (eventStream.hasNext()) {
            events.add(eventStream.next());
        }
        return events;
    }

    public static class MyCommandHandler {

        @Autowired
        @Qualifier("aggregateARepository")
        private Repository<AggregateA> aggregateARepository;

        @Autowired
        @Qualifier("aggregateBRepository")
        private Repository<AggregateB> aggregateBRepository;

        @CommandHandler
        public void handle(String stringCommand) {
            aggregateARepository.load(aggregateAIdentifier).doSomething(stringCommand);
        }

        @CommandHandler
        public void handle(Object objectCommand) {
            aggregateBRepository.load(aggregateBIdentifier).doSomething();
        }

    }

    public static class LoopbackSaga {

        @Autowired
        private CommandBus commandBus;

        @EventHandler
        public void handle(FirstEvent event) {
            commandBus.dispatch(new Object());
            commandBus.dispatch("first");
        }

        @EventHandler
        public void handle(SecondEvent event) {
            commandBus.dispatch("second");
        }

        @EventHandler
        public void handle(ThirdEvent event) {
            commandBus.dispatch("third");
        }
    }

    public static class FirstEvent extends DomainEvent {

    }

    public static class SecondEvent extends DomainEvent {

    }

    public static class ThirdEvent extends DomainEvent {

    }

    public static class AggregateA extends AbstractAnnotatedAggregateRoot {

        public AggregateA(AggregateIdentifier identifier) {
            super(identifier);
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

        public AggregateB(AggregateIdentifier identifier) {
            super(identifier);
        }

        public void doSomething() {
            apply(new SecondEvent());
        }
    }

    private static class CreateEvent extends DomainEvent {

        public CreateEvent(
                AggregateIdentifier aggregateBIdentifier) {
            super(0, aggregateBIdentifier);
        }
    }

    private class SendCommandTask implements Runnable {

        @Override
        public void run() {
            commandBus.dispatch("hello");
        }
    }
}
