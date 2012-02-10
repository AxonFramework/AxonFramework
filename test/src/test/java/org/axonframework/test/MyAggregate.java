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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;

/**
 * @author Allard Buijze
 */
class MyAggregate extends AbstractAnnotatedAggregateRoot {

    @SuppressWarnings("UnusedDeclaration")
    private transient int counter;
    private Integer lastNumber;

    public MyAggregate(AggregateIdentifier aggregateIdentifier) {
        super(aggregateIdentifier);
    }

    @EventHandler
    public void handleMyEvent(MyEvent event) {
        lastNumber = event.getSomeValue();
    }

    @EventHandler
    public void handleAll(DomainEvent event) {
        // we don't care about events
    }

    public void doSomethingIllegal(Integer newIllegalValue) {
        apply(new MyEvent(lastNumber + 1));
        lastNumber = newIllegalValue;
    }

    public void doSomething() {
        // this state change should be accepted, since it happens on a transient value
        counter++;
        apply(new MyEvent(lastNumber + 1));
    }
}
