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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.Repository;

class StubAggregateCommandHandler {

    private Repository<StubAggregate> repository;
    private EventBus eventBus;

    @CommandHandler
    public void handleStubAggregateCreated(CreateStubAggregateCommand command) throws Exception {
        repository.newInstance(() -> {
            StubAggregate aggregate = new StubAggregate();
            aggregate.handle(command.getAggregateId());
            return aggregate;
        });
    }

    @CommandHandler
    public void handleStubAggregateUpdated(UpdateStubAggregateCommand command) {
        repository.load(command.getAggregateId().toString(), command.getAggregateVersion())
                  .execute(StubAggregate::makeAChange);
    }

    @CommandHandler
    public void handleStubAggregateUpdatedWithExtraEvent(UpdateStubAggregateWithExtraEventCommand command) {
        Aggregate<StubAggregate> aggregate = repository.load(command.getAggregateId().toString());
        aggregate.execute(StubAggregate::makeAChange);
        eventBus.publish(new GenericEventMessage<>(new MessageType("event"), new MyEvent()));
        aggregate.execute(StubAggregate::makeAChange);
    }

    @CommandHandler
    public void handleStubAggregateLooping(LoopingCommand command) {
        repository.load(command.getAggregateId().toString())
                  .execute(StubAggregate::makeALoopingChange);
    }

    @CommandHandler
    public void handleProblematicCommand(ProblematicCommand command) {
        repository.load(command.getAggregateId().toString(), command.getAggregateVersion())
                  .execute(StubAggregate::causeTrouble);
    }

    public void setRepository(Repository<StubAggregate> repository) {
        this.repository = repository;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }
}
