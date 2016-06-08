/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.integrationtests.domain;

import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.eventsourcing.EventSourcedMember;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.util.UUID;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * @author Allard Buijze
 */
public class StructuredAggregateRoot {

    private int invocations;
    @EventSourcedMember
    private StructuredEntity entity;
    @AggregateIdentifier
    private UUID identifier;

    public StructuredAggregateRoot() {
        apply(new InvocationEvent(UUID.randomUUID(), 1));
    }

    public void invoke() {
        apply(new InvocationEvent(identifier, invocations + 1));
    }

    @EventSourcingHandler
    public void handleEvent(InvocationEvent event) {
        if (identifier == null) {
            identifier = event.getAggregateIdentifier();
        }
        this.invocations = event.getInvocationCount();
        if (invocations == 1) {
            entity = new StructuredEntity();
        }
    }

    int getInvocations() {
        return invocations;
    }

    StructuredEntity getEntity() {
        return entity;
    }
}
