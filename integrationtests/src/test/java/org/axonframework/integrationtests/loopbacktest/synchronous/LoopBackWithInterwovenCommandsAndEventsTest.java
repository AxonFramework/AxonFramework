/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.integrationtests.loopbacktest.synchronous;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.AggregateConfigurer;
import org.axonframework.config.LegacyConfiguration;
import org.axonframework.config.LegacyDefaultConfigurer;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.LegacyRepository;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating that command dispatched within a UnitOfWork that publishes events withholds to the desired
 * event ordering.
 *
 * @author Gerard de Leeuw
 * @author Allard Buijze
 */
class LoopBackWithInterwovenCommandsAndEventsTest {

    // This ensures we do not wire Axon Server components
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;

    private final String aggregateIdentifier = "Aggregate";
    private MyCommand command;
    private LegacyConfiguration configuration;

    @BeforeEach
    void setUp() {
        AggregateConfigurer<MyAggregate> aggregateConfigurer =
                AggregateConfigurer.defaultConfiguration(MyAggregate.class)
                                   .configureAggregateFactory(c -> new AggregateFactory<>() {
                                       @Override
                                       public MyAggregate createAggregateRoot(String aggregateIdentifier,
                                                                              DomainEventMessage<?> firstEvent) {
                                           return new MyAggregate(aggregateIdentifier);
                                       }

                                       @Override
                                       public Class<MyAggregate> getAggregateType() {
                                           return MyAggregate.class;
                                       }
                                   });
        configuration = LegacyDefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES)
                                               .configureEmbeddedEventStore(c -> new LegacyInMemoryEventStorageEngine())
                                               .configureAggregate(aggregateConfigurer)
                                               .registerCommandHandler(c -> new MyCommandHandler(
                                                 c.repository(MyAggregate.class), c.commandGateway()
                                         ))
                                               .buildConfiguration();
        configuration.start();

        command = new MyCommand("outer", aggregateIdentifier,
                                new MyCommand("middle", aggregateIdentifier,
                                              new MyCommand("inner", aggregateIdentifier)));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void orderInCommandHandlerAggregate() {
        MyAggregate commandHandlerAggregate = configuration.commandGateway().sendAndWait(command, MyAggregate.class);

        assertEquals(expectedDescriptions(command), commandHandlerAggregate.getHandledCommands());
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void orderInEventSourcedAggregate() {
        LegacyRepository<MyAggregate> repository = configuration.repository(MyAggregate.class);
        configuration.commandGateway().sendAndWait(command);

        CommandMessage<String> testCommand =
                new GenericCommandMessage<>(new MessageType("command"), "loading");
        LegacyUnitOfWork<CommandMessage<?>> unitOfWork = LegacyDefaultUnitOfWork.startAndGet(testCommand);
        MyAggregate loadedAggregate = repository.load(aggregateIdentifier).invoke(Function.identity());
        unitOfWork.commit();

        assertEquals(expectedDescriptions(command), loadedAggregate.getHandledCommands());
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void orderInEventStore() {
        configuration.commandGateway().sendAndWait(command);
        assertEquals(expectedDescriptions(command), configuration.eventStore()
                                                                 .readEvents(aggregateIdentifier)
                                                                 .asStream()
                                                                 .map(Message::payload)
                                                                 .map(MyEvent.class::cast)
                                                                 .map(MyEvent::getDescription)
                                                                 .collect(Collectors.toList()));
    }

    private List<String> expectedDescriptions(MyCommand command) {
        List<String> descriptions = new ArrayList<>();
        descriptions.add(command.startDescription());
        if (command.getInnerCommand() != null) {
            descriptions.addAll(expectedDescriptions(command.getInnerCommand()));
        }
        descriptions.add(command.doneDescription());
        return descriptions;
    }

    @AggregateRoot
    public static class MyAggregate {

        private final List<String> handledCommands;
        @AggregateIdentifier
        private String aggregateIdentifier;

        public MyAggregate(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.handledCommands = new ArrayList<>();
        }

        public void handle(MyCommand command, CommandGateway commandGateway) {
            apply(new MyEvent(aggregateIdentifier, command.startDescription()));
            if (command.getInnerCommand() != null) {
                commandGateway.sendAndWait(command.getInnerCommand());
            }
            apply(new MyEvent(aggregateIdentifier, command.doneDescription()));
        }

        @EventSourcingHandler
        public void handle(MyEvent event) {
            this.aggregateIdentifier = event.getAggregateIdentifier();
            handledCommands.add(event.getDescription());
        }

        public List<String> getHandledCommands() {
            return handledCommands;
        }
    }

    /**
     * @author Gerard de Leeuw
     * @since 0.1.0 on 4-1-2017
     */
    public static class MyCommand {

        private final String name;
        private final String aggregateIdentifier;
        private final MyCommand innerCommand;

        public MyCommand(String name, String aggregateIdentifier) {
            this(name, aggregateIdentifier, null);
        }

        public MyCommand(String name, String aggregateIdentifier, MyCommand innerCommand) {
            this.name = name;
            this.aggregateIdentifier = aggregateIdentifier;
            this.innerCommand = innerCommand;
        }

        public String getName() {
            return name;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public MyCommand getInnerCommand() {
            return innerCommand;
        }

        public String startDescription() {
            return "Start handling command " + name;
        }

        public String doneDescription() {
            return "Done handling command " + name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * @author Gerard de Leeuw
     * @since 0.1.0 on 4-1-2017
     */
    public static class MyCommandHandler {

        private final LegacyRepository<MyAggregate> repository;
        private final CommandGateway commandGateway;

        public MyCommandHandler(LegacyRepository<MyAggregate> repository, CommandGateway commandGateway) {
            this.repository = repository;
            this.commandGateway = commandGateway;
        }

        @CommandHandler
        public MyAggregate handle(MyCommand command) throws Exception {
            Aggregate<MyAggregate> aggregate;
            try {
                aggregate = repository.load(command.getAggregateIdentifier());
            } catch (AggregateNotFoundException e) {
                aggregate = repository.newInstance(() -> new MyAggregate(command.getAggregateIdentifier()));
            }
            aggregate.execute(a -> a.handle(command, commandGateway));
            return aggregate.invoke(Function.identity());
        }
    }

    /**
     * @author Gerard de Leeuw
     * @since 0.1.0 on 4-1-2017
     */
    public static class MyEvent {

        private final String aggregateIdentifier;
        private final String description;

        public MyEvent(String aggregateIdentifier, String description) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.description = description;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
