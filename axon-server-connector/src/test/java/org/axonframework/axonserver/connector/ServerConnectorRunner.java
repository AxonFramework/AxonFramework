/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

public class ServerConnectorRunner {

    private static final Logger logger = LoggerFactory.getLogger(ServerConnectorRunner.class);

    public static void main(String[] args) {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerComponent(AxonServerConfiguration.class,
                                                                          c -> AxonServerConfiguration.builder()
                                                                                                      .servers(
                                                                                                              "localhost")
                                                                                                      .build())
                                                       .configureAggregate(MyAggregate.class)
                                                       .eventProcessing(ep -> ep.registerEventHandler(c -> new MyEventHandler()))
                                                       .start();

        try {
            logger.info("Sending command");
            CompletableFuture<Object> result = configuration.commandGateway()
                                                            .send(new CreateMyAggregateCommand(UUID.randomUUID().toString()),
                                                                  ProcessingContext.NONE,
                                                                  Object.class);
            logger.info("Command sent, awaiting response...");
            result.join();
            logger.info("Result received. Press enter to shut down");
            new Scanner(System.in).nextLine();
        } finally {
            configuration.shutdown();
        }
    }

    public static class MyAggregate {

        @AggregateIdentifier
        private String id;

        public MyAggregate() {
        }


        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateMyAggregateCommand command) {
            apply(new MyAggregateCreatedEvent(command.getId()));
        }

        @EventSourcingHandler
        protected void handle(MyAggregateCreatedEvent event) {
            this.id = event.getId();
        }
    }

    public static class MyAggregateCreatedEvent {

        private final String id;

        public MyAggregateCreatedEvent(String id) {

            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class CreateMyAggregateCommand {

        @TargetAggregateIdentifier
        private final String id;

        public CreateMyAggregateCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class MyEventHandler {

        @EventHandler
        public void handle(MyAggregateCreatedEvent event) {
            logger.info("Received event for aggregate: {}", event.getId());
        }
    }
}
