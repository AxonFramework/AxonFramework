/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.*;
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Duplicate command handling members were detected unittests
 *
 * @author leechedan
 * @since 4.5
 */
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
        Object updateResult = commandGateway.sendAndWait(new GenericCommandMessage(GenericCommandMessage.asCommandMessage(new MyCommand("test")), "update"));
        assertEquals(ReturnType.UPDATE, updateResult, "UPDATE is expected");
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

    static enum ReturnType {
        CREATE, UPDATE, INVALIDATE;
    }

    static class MyCommand {

        @TargetAggregateIdentifier
        String id;

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

        @AggregateIdentifier
        String id;

        public MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(MyCommand myCommand) {
            AggregateLifecycle.apply(new MyEvent(myCommand.id));
        }

        @CommandHandler(commandName = "update")
        public ReturnType update(MyCommand myCommand) {
            AggregateLifecycle.apply(new MyEvent(myCommand.id));
            return ReturnType.UPDATE;
        }


        @CommandHandler
        public ReturnType invalidate(MyCommand myCommand) {
            AggregateLifecycle.apply(new MyEvent(myCommand.id));
            return ReturnType.INVALIDATE;
        }

        @EventSourcingHandler
        public void handle(MyEvent myEvent) {
            this.id = myEvent.id;
        }
    }
}
