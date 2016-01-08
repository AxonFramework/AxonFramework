/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventstore.supporting;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventstore.EventVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Knut-Olav Hoven
 */
@SuppressWarnings("rawtypes")
public class CapturingEventVisitor implements EventVisitor {
    private final ArrayList<DomainEventMessage> visited = new ArrayList<DomainEventMessage>();

    @Override
    public synchronized void doWithEvent(DomainEventMessage domainEvent) {
        visited.add(domainEvent);
    }

    public synchronized List<DomainEventMessage> visited() {
        return new ArrayList<DomainEventMessage>(visited);
    }
}