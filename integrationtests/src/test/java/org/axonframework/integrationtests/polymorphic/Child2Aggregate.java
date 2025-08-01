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

package org.axonframework.integrationtests.polymorphic;

import jakarta.persistence.Entity;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.modelling.command.CreationPolicy;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * The first level child aggregate in polymorphic aggregate hierarchy.
 *
 * @author Milan Savic
 */
@Entity
public class Child2Aggregate extends ParentAggregate {

    public Child2Aggregate() {
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    public void handle(CreateChild2Command cmd) {
        apply(new CreatedEvent(cmd.getId()));
    }

    @EventSourcingHandler
    public void on(CreatedEvent evt) {
        this.id = evt.getId();
    }

    @EventHandler
    public void on(ChildEvent evt) {
        this.state = "child2" + evt.getId();
    }

    @CommandHandler
    public String handle(InterceptedByParentCommand cmd) {
        return cmd.getState() + "HandledByChild2";
    }

    @CommandHandlerInterceptor
    public void intercept(InterceptedByChildCommand cmd) {
        cmd.setState(cmd.getState() + "InterceptedByChild2");
    }

    @Override
    public String handle(AbstractCommandHandlerCommand cmd) {
        return "handledByChild2";
    }
}
