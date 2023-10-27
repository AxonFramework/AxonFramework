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

package org.axonframework.integrationtests.polymorphic;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;

import javax.persistence.Entity;
import javax.persistence.Id;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.createNew;

/**
 * Non-polymorphic aggregate that creates a polymorphic aggregate.
 *
 * @author Milan Savic
 */
@Entity
public class SimpleAggregate {

    @Id
    private String id;

    public SimpleAggregate() {
    }

    @CommandHandler
    public SimpleAggregate(CreateSimpleAggregateCommand cmd) throws Exception {
        apply(new SimpleAggregateCreatedEvent(cmd.getId()));
        createNew(ParentAggregate.class, () -> new Child1Aggregate(new CreateChild1Command("child1" + cmd.getId())));
    }

    @EventSourcingHandler
    public void on(SimpleAggregateCreatedEvent evt) {
        this.id = evt.getId();
    }
}
