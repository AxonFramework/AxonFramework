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
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandHandlerInterceptor;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * The abstract parent aggregate in this polymorphic aggregate hierarchy. It represents the type of the aggregate as a
 * whole.
 *
 * @author Milan Savic
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class ParentAggregate {

    @AggregateIdentifier
    @Id
    protected String id;

    protected String state;

    public String getState() {
        return state;
    }

    @CommandHandler
    public String handle(CommonCommand cmd) {
        return this.getClass().getSimpleName() + cmd.getId();
    }

    @CommandHandler
    public static ParentAggregate create(CreateChildFactoryCommand cmd) {
        if (cmd.getChild() == 1) {
            Child1Aggregate child1Aggregate = new Child1Aggregate();
            child1Aggregate.handle(new CreateChild1Command(cmd.getId()));
            return child1Aggregate;
        } else {
            Child2Aggregate child2Aggregate = new Child2Aggregate();
            child2Aggregate.handle(new CreateChild2Command(cmd.getId()));
            return child2Aggregate;
        }
    }

    @EventHandler
    public void on(ParentEvent evt) {
        this.state = "parent" + evt.getId();
    }

    @CommandHandler
    public void handle(FireChildEventCommand cmd) {
        apply(new ChildEvent(cmd.getId()));
    }

    @CommandHandlerInterceptor
    public void intercept(InterceptedByParentCommand cmd) {
        cmd.setState(cmd.getState() + "InterceptedByParent");
    }

    @CommandHandler
    public String handle(InterceptedByChildCommand cmd) {
        return cmd.getState() + "HandledByParent";
    }

    @CommandHandler
    public abstract String handle(AbstractCommandHandlerCommand cmd);
}
