package org.axonframework.integrationtests.loopbacktest;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.model.EntityId;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.Test;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

public class NestedUowRollbackTest {

    @Test
    public void testDispatchCommand() {
        Configuration c = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(TestAggregate.class)
                .registerCommandHandler(x -> new Handler())
                .configureEmbeddedEventStore(x -> new InMemoryEventStorageEngine())
                .buildConfiguration();

        c.start();
        CommandGateway gw = c.commandGateway();
        gw.sendAndWait(new TestCommand());
    }

    static class TestAggregate {
        @EntityId
        String id;

        @CommandHandler
        public TestAggregate(Create cmd) {
            apply(cmd);
        }

        private TestAggregate() {
        }

        @EventSourcingHandler
        public void handle(Create evt) {
            id = evt.id;
        }

        @CommandHandler
        public void handle(Crash cmd) {
            throw new RuntimeException("exception");
        }

        @CommandHandler
        public void cmd(Hello cmd) {
            System.out.println("hello");
        }
    }

    static class Create {
        @TargetAggregateIdentifier
        String id;

        public Create(String id) {
            this.id = id;
        }
    }

    static class Crash {
        @TargetAggregateIdentifier
        String id;

        public Crash(String id) {
            this.id = id;
        }
    }

    static class Hello {
        @TargetAggregateIdentifier
        String id;

        public Hello(String id) {
            this.id = id;
        }
    }

    static class TestCommand {
    }

    static class Handler {
        @CommandHandler
        public void handle(TestCommand cmd, CommandGateway gw) {
            gw.sendAndWait(new Create("1"));
            try {
                gw.sendAndWait(new Crash("1"));
            } catch (RuntimeException e) {
                System.out.println(e.getMessage());
            }
            gw.sendAndWait(new Hello("1"));
        }

    }
}
