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

package org.axonframework.integrationtests.eventsourcing.conflictresolution;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.conflictresolution.ConflictResolver;
import org.axonframework.eventsourcing.conflictresolution.Conflicts;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.ConflictingAggregateVersionException;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateVersion;
import org.junit.jupiter.api.*;

import java.util.Objects;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

class ConflictResolutionIntegrationTest {

    // This ensure we do not wire Axon Server components
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;

    private CommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        Configuration configuration = DefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES)
                                                       .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                       .configureAggregate(StubAggregate.class)
                                                       .buildConfiguration();
        configuration.start();
        commandGateway = configuration.commandGateway();
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void nonConflictingEventsAllowed() {
        commandGateway.sendAndWait(new CreateCommand("1234"));
        commandGateway.sendAndWait(new UpdateCommand("1234", "update1", 0L));
        commandGateway.sendAndWait(new UpdateCommand("1234", "update2", 0L));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void unresolvedConflictCausesException() {
        commandGateway.sendAndWait(new CreateCommand("1234"));
        commandGateway.sendAndWait(new UpdateCommand("1234", "update1", 0L));
        assertThrows(
                ConflictingAggregateVersionException.class,
                () -> commandGateway.sendAndWait(new UpdateWithoutConflictDetectionCommand("1234", "update2", 0L))
        );
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void expressedConflictCausesException() {
        commandGateway.sendAndWait(new CreateCommand("1234"));
        commandGateway.sendAndWait(new UpdateCommand("1234", "update1", 0L));
        assertThrows(
                ConflictingAggregateVersionException.class,
                () -> commandGateway.sendAndWait(new UpdateCommand("1234", "update1", 0L))
        );
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    void noExpectedVersionIgnoresConflicts() {
        commandGateway.sendAndWait(new CreateCommand("1234"));
        commandGateway.sendAndWait(new UpdateCommand("1234", "update1", 0L));
        commandGateway.sendAndWait(new UpdateCommand("1234", "update1", null));
    }

    public static class StubAggregate {

        @SuppressWarnings("unused")
        @AggregateIdentifier
        private String aggregateId;

        public StubAggregate() {
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateCommand command) {
            apply(new CreatedEvent(command.getAggregateId()));
        }

        @CommandHandler
        public void handle(UpdateCommand command, ConflictResolver conflictResolver) {
            conflictResolver.detectConflicts(Conflicts.payloadMatching(UpdatedEvent.class,
                                                                       u -> Objects.equals(command.getUpdate(),
                                                                                           u.getUpdate())));
            apply(new UpdatedEvent(command.getUpdate()));
        }

        @CommandHandler
        public void handle(UpdateWithoutConflictDetectionCommand command) {
            apply(new UpdatedEvent(command.getUpdate()));
        }

        @EventSourcingHandler
        protected void on(CreatedEvent event) {
            this.aggregateId = event.getAggregateId();
        }

    }

    public static class UpdatedEvent {
        private final String update;

        public UpdatedEvent(String update) {
            this.update = update;
        }

        public String getUpdate() {
            return update;
        }
    }

    public static class CreatedEvent {
        private final String aggregateId;

        public CreatedEvent(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    public static class CreateCommand {
        @TargetAggregateIdentifier
        private final String aggregateId;

        public CreateCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    public static class UpdateCommand {
        @TargetAggregateIdentifier
        private final String aggregateId;
        private final String update;

        @SuppressWarnings("unused")
        @TargetAggregateVersion
        private final Long expectedVersion;

        private UpdateCommand(String aggregateId, String update, Long expectedVersion) {
            this.aggregateId = aggregateId;
            this.update = update;
            this.expectedVersion = expectedVersion;
        }

        public String getUpdate() {
            return update;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    public static class UpdateWithoutConflictDetectionCommand extends UpdateCommand {

        public UpdateWithoutConflictDetectionCommand(String aggregateId, String update, Long expectedVersion) {
            super(aggregateId, update, expectedVersion);
        }
    }
}
