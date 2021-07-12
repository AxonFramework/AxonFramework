package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.DuplicateCommandHandlingMemberException;
import org.axonframework.commandhandling.FailingDuplicateCommandHandlingMemberResolver;
import org.axonframework.commandhandling.LoggingDuplicateCommandHandlingMemberResolver;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.AggregateConfigurer;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class DuplicateCommandInSameAggregate {

    static Configuration configuration;

    @Test
    public void testLoggingDuplicateHandlingMemberShouldLogging() {

        configuration = DefaultConfigurer.defaultConfiguration()
                                         .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                         .configureAggregate(AggregateConfigurer.defaultConfiguration(MyAggregate.class)
                                                                                .configureDuplicateCommandHandlingMemberResolver(
                                                                                        c -> LoggingDuplicateCommandHandlingMemberResolver
                                                                                                .instance()))
                                         .start();
        CommandGateway commandGateway = configuration.commandGateway();
        commandGateway.sendAndWait(new MyCommand("test"));
//        commandGateway.sendAndWait(new GenericCommandMessage(GenericCommandMessage.asCommandMessage(new MyCommand("test")),"update"));
    }

    @Test
    public void testFailingDuplicateHandlingMemberShouldFailing() {

        Configurer configurer = DefaultConfigurer.defaultConfiguration()
                                                 .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                 .configureAggregate(AggregateConfigurer
                                                                             .defaultConfiguration(MyAggregate.class)
                                                                             .configureDuplicateCommandHandlingMemberResolver(
                                                                                     c -> FailingDuplicateCommandHandlingMemberResolver
                                                                                             .instance()));
        LifecycleHandlerInvocationException lifecycleHandlerInvocationException = assertThrows(
                LifecycleHandlerInvocationException.class,
                () -> configurer.start());
        assertTrue(DuplicateCommandHandlingMemberException.class
                           .isAssignableFrom(lifecycleHandlerInvocationException.getCause().getCause().getClass()),
                   "exception not expected");
    }


    static class MyCommand {

        @TargetAggregateIdentifier String id;

        public MyCommand(String id) {
            this.id = id;
        }
    }

    static class MyEvent {

        String id;

        public MyEvent(String id) {
            this.id = id;
        }
    }


    static class MyAggregate {

        @AggregateIdentifier String id = "";

        public MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(MyCommand myCommand) {
            System.out.println("MyAggregate MyAggregate command - " + myCommand);
            AggregateLifecycle.apply(new MyEvent(myCommand.id));
        }

        @CommandHandler(commandName="update")
        public void update(MyCommand myCommand) {
            System.out.println("MyAggregate update command - " + myCommand);
            AggregateLifecycle.apply(new MyEvent(myCommand.id));
        }


        @CommandHandler
        public void invalidate(MyCommand myCommand) {
            System.out.println("MyAggregate invalidate command - " + myCommand);
            AggregateLifecycle.apply(new MyEvent(myCommand.id));
        }

        @EventSourcingHandler
        public void handle(MyEvent myEvent) {
            System.out.println("MyAggregate EVENT - " + myEvent);
            this.id = myEvent.id;
        }
    }
}