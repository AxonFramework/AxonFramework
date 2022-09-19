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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class StubAggregateForCreation {

    @AggregateIdentifier
    String identifier;
    AggregateCreationPolicy aggregateCreationPolicy;

    @SuppressWarnings("unused")
    public StubAggregateForCreation() {
    }

    public StubAggregateForCreation(String identifier) {
        this.identifier = identifier;
    }

    @CommandHandler
    public StubAggregateForCreation(ConstructorCommand cmd) {
        AggregateLifecycle.apply(new CreatedEvent(cmd.aggregateId, null));
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    public void handle(CreateAlwaysCommand cmd) {
        AggregateLifecycle.apply(new CreatedEvent(cmd.aggregateId, AggregateCreationPolicy.ALWAYS));
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(CreateIfMissingCommand cmd) {
        AggregateLifecycle.apply(new CreatedEvent(cmd.aggregateId, AggregateCreationPolicy.CREATE_IF_MISSING));
    }

    @EventSourcingHandler
    public void on(CreatedEvent event) {
        this.identifier = event.aggregateIdentifier;
        aggregateCreationPolicy = event.aggregateCreationPolicy;
    }

    public static class ConstructorCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        public ConstructorCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    public static class CreateAlwaysCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        public CreateAlwaysCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    public static class CreateIfMissingCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        public CreateIfMissingCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }

        public String getAggregateId() {
            return aggregateId;
        }
    }

    @SuppressWarnings("unused")
    public static class CreatedEvent {

        private final String aggregateIdentifier;
        private final AggregateCreationPolicy aggregateCreationPolicy;

        public CreatedEvent(String aggregateIdentifier, AggregateCreationPolicy policy) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.aggregateCreationPolicy = policy;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public AggregateCreationPolicy getAggregateCreationPolicy() {
            return aggregateCreationPolicy;
        }
    }
}

