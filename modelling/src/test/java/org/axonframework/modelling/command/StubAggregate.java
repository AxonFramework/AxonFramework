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

package org.axonframework.modelling.command;

import org.axonframework.modelling.utils.StubDomainEvent;

import java.util.UUID;

/**
 * Sample aggregate used by, for example, the {@link LockingRepositoryTest}.
 *
 * @author Allard Buijze
 */
public class StubAggregate {

    @SuppressWarnings("FieldMayBeFinal")
    @AggregateIdentifier
    private String identifier;

    public StubAggregate() {
        identifier = UUID.randomUUID().toString();
    }

    public StubAggregate(Object identifier) {
        this.identifier = identifier != null ? identifier.toString() : null;
    }

    public void doSomething() {
        AggregateLifecycle.apply(new StubDomainEvent());
    }

    public String getIdentifier() {
        return identifier;
    }

    public void delete() {
        AggregateLifecycle.apply(new StubDomainEvent());
        AggregateLifecycle.markDeleted();
    }
}
