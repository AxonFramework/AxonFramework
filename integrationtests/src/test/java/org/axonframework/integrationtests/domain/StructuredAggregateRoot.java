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

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class StructuredAggregateRoot extends AbstractAnnotatedAggregateRoot {

    private int invocations;
    private StructuredEntity entity;
    private final Object identifier;

    public StructuredAggregateRoot() {
        this.identifier = UUID.randomUUID();
        apply(new InvocationEvent(1));
    }

    public StructuredAggregateRoot(Object identifier) {
        this.identifier = identifier;
    }

    public void invoke() {
        apply(new InvocationEvent(invocations + 1));
    }

    @Override
    public Object getIdentifier() {
        return identifier;
    }

    @EventHandler
    public void handleEvent(InvocationEvent event) {
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
