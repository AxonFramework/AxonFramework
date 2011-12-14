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

package org.axonframework.test;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.AggregateInitializer;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;

/**
 * @author Allard Buijze
 */
class MyAggregate extends AbstractAnnotatedAggregateRoot {

    private int lastNumber;
    private final Object identifier;

    @AggregateInitializer
    public MyAggregate(Object identifier) {
        this.identifier = identifier;
    }

    @EventHandler
    public void handleMyEvent(MyEvent event) {
        lastNumber = event.getSomeValue();
    }

    @EventHandler
    public void handleAll(DomainEventMessage event) {
        // we don't care about events
    }

    public void doSomething() {
        apply(new MyEvent(lastNumber + 1));
    }

    @Override
    public Object getIdentifier() {
        return identifier;
    }
}
