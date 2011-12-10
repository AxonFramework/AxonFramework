/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.jpa;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;

import java.util.UUID;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * @author Allard Buijze
 */
@Entity
public class SimpleJpaEventSourcedAggregate extends AbstractAnnotatedAggregateRoot {

    @Basic
    private long counter;
    @Id
    private final String identifier;

    public SimpleJpaEventSourcedAggregate() {
        identifier = UUID.randomUUID().toString();
    }

    public SimpleJpaEventSourcedAggregate(String identifier) {
        this.identifier = identifier;
    }

    public void doSomething() {
        apply(new SomeEvent());
    }

    @EventHandler
    private void onSomeEvent(SomeEvent event) {
        counter++;
    }

    public long getInvocationCount() {
        return counter;
    }

    @Override
    public Object getIdentifier() {
        return identifier;
    }
}


