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

import jakarta.persistence.Entity;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.CommandHandlerInterceptor;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * The first level child aggregate in polymorphic aggregate hierarchy.
 *
 * @author Milan Savic
 */
@Entity
public class Child1Aggregate extends ParentAggregate {

    public Child1Aggregate() {
    }

    @CommandHandler
    public Child1Aggregate(CreateChild1Command cmd) {
        apply(new CreatedEvent(cmd.getId()));
    }

    @EventSourcingHandler
    public void on(CreatedEvent evt) {
        this.id = evt.getId();
    }

    @CommandHandler
    public String handle(Child1OnlyCommand cmd) {
        return getClass().getSimpleName() + cmd.getId();
    }

    @CommandHandler
    public void handle(FireParentEventCommand cmd) {
        apply(new ParentEvent(cmd.getId()));
    }

    @EventHandler
    public void on(ChildEvent evt) {
        this.state = "child1" + evt.getId();
    }

    @CommandHandler
    public String handle(InterceptedByParentCommand cmd) {
        return cmd.getState() + "HandledByChild1";
    }

    @CommandHandlerInterceptor
    public void intercept(InterceptedByChildCommand cmd) {
        cmd.setState(cmd.getState() + "InterceptedByChild1");
    }

    @Override
    public String handle(AbstractCommandHandlerCommand cmd) {
        return "handledByChild1";
    }
}
