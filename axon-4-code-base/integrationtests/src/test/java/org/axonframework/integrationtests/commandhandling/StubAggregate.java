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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * @author Allard Buijze
 */
public class StubAggregate {

    private int changeCounter;

    @AggregateIdentifier
    private String identifier;

    public StubAggregate(Object aggregateId) {
        apply(new StubAggregateCreatedEvent(aggregateId));
    }

    StubAggregate() {
    }

    public void makeAChange() {
        apply(new StubAggregateChangedEvent());
    }

    public void causeTrouble() {
        throw new RuntimeException("That's problematic");
    }

    @EventSourcingHandler
    private void onCreated(StubAggregateCreatedEvent event) {
        this.identifier = event.getAggregateIdentifier().toString();
        changeCounter = 0;
    }

    @EventSourcingHandler
    private void onChange(StubAggregateChangedEvent event) {
        changeCounter++;
    }

    public void makeALoopingChange() {
        apply(new LoopingChangeDoneEvent(identifier));
    }
}
