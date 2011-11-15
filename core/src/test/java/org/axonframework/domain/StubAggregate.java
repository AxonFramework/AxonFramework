/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.domain;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;

/**
 * @author Allard Buijze
 */
public class StubAggregate extends AbstractEventSourcedAggregateRoot {

    private int invocationCount;

    public StubAggregate() {
    }

    public StubAggregate(AggregateIdentifier identifier) {
        super(identifier);
    }

    public void doSomething() {
        apply(new StubDomainEvent());
    }

    @EventHandler
    @Override
    protected void handle(DomainEventMessage event) {
        invocationCount++;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public DomainEventMessage createSnapshotEvent() {
        return new GenericDomainEventMessage<StubDomainEvent>(getIdentifier(), (long) 5,
                                                              MetaData.emptyInstance(), new StubDomainEvent());
    }

    public void delete() {
        apply(new StubDomainEvent());
        markDeleted();
    }
}
