/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.AggregateConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.spring.config.SpringAxonAutoConfigurer;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests aggregate polymorphism configured with Spring.
 *
 * @author Milan Savic
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AggregatePolymorphismTest {

    @Autowired
    private org.axonframework.config.Configuration configuration;
    @Autowired
    private CommandGateway commandGateway;

    @Test
    void config() {
        List<AggregateConfigurer> configurers = configuration.findModules(AggregateConfigurer.class);
        assertEquals(3, configurers.size());

        String bId = commandGateway.sendAndWait(new CreateBCommand("123"));
        String cId = commandGateway.sendAndWait(new CreateCCommand("456"));
        String dId = commandGateway.sendAndWait(new CreateDCommand("789"));
        String fId = commandGateway.sendAndWait(new CreateFCommand("000"));
        String iId = commandGateway.sendAndWait(new CreateICommand("111"));
        String bResult = commandGateway.sendAndWait(new CommonCommand(bId));
        String cResult = commandGateway.sendAndWait(new CommonCommand(cId));
        String dResult = commandGateway.sendAndWait(new CommonCommand(dId));
        String fResult = commandGateway.sendAndWait(new FCommand(fId));
        String iResult = commandGateway.sendAndWait(new HCommand(iId));

        assertEquals("B123", bResult);
        assertEquals("C456", cResult);
        assertEquals("D789", dResult);
        assertEquals("F000", fResult);
        assertEquals("I111", iResult);
    }

    private static class CreateBCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateBCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateCCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateCCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateDCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateDCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateFCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateFCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class FCommand {

        @TargetAggregateIdentifier
        private final String id;

        private FCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreatedEvent {

        private final String id;

        private CreatedEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CommonCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CommonCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateICommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateICommand(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private static class HCommand {

        @TargetAggregateIdentifier
        private final String id;

        private HCommand(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    @Import(SpringAxonAutoConfigurer.ImportSelector.class)
    @Configuration
    public static class Context {

        @Component("abc")
        @Aggregate
        public abstract static class A {

            @AggregateIdentifier
            private String id;

            @EventSourcingHandler
            public void on(CreatedEvent evt) {
                this.id = evt.getId();
            }

            @CommandHandler
            public String handle(CommonCommand cmd) {
                return this.getClass().getSimpleName() + cmd.getId();
            }
        }

        @Component
        public static class B extends A {

            public B() {
            }

            @CommandHandler
            public B(CreateBCommand cmd) {
                apply(new CreatedEvent(cmd.getId()));
            }
        }

        @Aggregate
        public static class C extends A {

            public C() {
            }

            @CommandHandler
            public C(CreateCCommand cmd) {
                apply(new CreatedEvent(cmd.getId()));
            }
        }

        @Aggregate
        public static class D extends B {

            public D() {
            }

            @CommandHandler
            public D(CreateDCommand cmd) {
                apply(new CreatedEvent(cmd.getId()));
            }
        }

        public static class E {

            @AggregateIdentifier
            private String id;

            @EventSourcingHandler
            public void on(CreatedEvent evt) {
                this.id = evt.getId();
            }

            @CommandHandler
            public String handle(FCommand cmd) {
                return this.getClass().getSimpleName() + cmd.getId();
            }
        }

        @Aggregate
        public static class F extends E {

            public F() {
            }

            @CommandHandler
            public F(CreateFCommand cmd) {
                apply(new CreatedEvent(cmd.getId()));
            }
        }

        @Aggregate
        public abstract static class G {

            @AggregateIdentifier
            private String id;

            @EventSourcingHandler
            public void on(CreatedEvent evt) {
                this.id = evt.getId();
            }
        }

        public abstract static class H extends G {

            @CommandHandler
            public String handle(HCommand cmd) {
                return this.getClass().getSimpleName() + cmd.id();
            }
        }

        @Aggregate
        public static class I extends H {

            public I() {
            }

            @CommandHandler
            public I(CreateICommand cmd) {
                apply(new CreatedEvent(cmd.id()));
            }
        }

        @Bean
        public EventProcessingModule eventProcessingModule() {
            return new EventProcessingModule();
        }

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }
    }
}
