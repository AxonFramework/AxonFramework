/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.authorization;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.security.access.annotation.Secured;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Test Aggregate
 *
 * @author Roald Bankras
 */
@Aggregate
public class TestAggregate {

    @AggregateIdentifier
    private UUID aggregateId;

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    @Secured("ROLE_aggregate.create")
    public void create(CreateAggregateCommand cmd) {
        apply(new AggregateCreatedEvent(cmd.getAggregateId()));
    }

    @CommandHandler
    public void update(UpdateAggregateCommand cmd) {
        apply(new AggregateUpdatedEvent(cmd.getAggregateId()));
    }

    @EventSourcingHandler
    public void on(AggregateCreatedEvent evt) {
        aggregateId = evt.getAggregateId();
    }
}

