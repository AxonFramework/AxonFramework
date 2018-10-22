/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.integrationtests.loopbacktest;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.Test;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

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
