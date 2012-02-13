/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.CurrentUnitOfWork;

/**
 * @author Allard Buijze
 */
class MyCommandHandler {

    private Repository<MyAggregate> repository;
    private EventBus eventBus;

    MyCommandHandler(Repository<MyAggregate> repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    MyCommandHandler() {
    }

    @CommandHandler
    public void createAggregate(CreateAggregateCommand command) {
        if (command.getAggregateIdentifier() != null) {
            repository.add(new MyAggregate(0, command.getAggregateIdentifier()));
        } else {
            repository.add(new MyAggregate(0));
        }
    }

    @CommandHandler
    public void handleTestCommand(TestCommand testCommand) {
        MyAggregate aggregate = repository.load(testCommand.getAggregateIdentifier(), null);
        aggregate.doSomething();
    }

    @CommandHandler
    public void handleStrangeCommand(StrangeCommand testCommand) {
        MyAggregate aggregate = repository.load(testCommand.getAggregateIdentifier(), null);
        aggregate.doSomething();
        eventBus.publish(new MyApplicationEvent(this));
        CurrentUnitOfWork.get().publishEvent(new MyApplicationEvent(this), eventBus);
        throw new StrangeCommandReceivedException("Strange command received");
    }

    @CommandHandler
    public void handleIllegalStateChange(IllegalStateChangeCommand command) {
        MyAggregate aggregate = repository.load(command.getAggregateIdentifier());
        aggregate.doSomethingIllegal(command.getNewIllegalValue());
    }

    @CommandHandler
    public void handleDeleteAggregate(DeleteCommand command) {
        repository.load(command.getAggregateIdentifier()).delete();
    }

    public void setRepository(Repository<MyAggregate> repository) {
        this.repository = repository;
    }
}
