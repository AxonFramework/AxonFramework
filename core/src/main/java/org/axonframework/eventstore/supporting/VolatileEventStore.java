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

import java.util.ArrayList;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.eventstore.EventVisitor;
import org.axonframework.eventstore.management.Criteria;
import org.axonframework.eventstore.management.CriteriaBuilder;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.joda.time.DateTime;

/**
 * @author Knut-Olav Hoven
 */
public class VolatileEventStore implements EventStore, EventStoreManagement {

    static class AggregateTypedEventMessage {
        String type;
        DomainEventMessage<?> eventMessage;
    }

    private final ArrayList<AggregateTypedEventMessage> volatileEvents = new ArrayList<AggregateTypedEventMessage>();

    @Override
    public synchronized void visitEvents(EventVisitor visitor) {
        for (AggregateTypedEventMessage eventMessage : volatileEvents) {
            visitor.doWithEvent(eventMessage.eventMessage);
        }
    }

    @Override
    public synchronized void visitEvents(Criteria criteria, EventVisitor visitor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CriteriaBuilder newCriteriaBuilder() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public synchronized void appendEvents(String type, DomainEventStream events) {
        while (events.hasNext()) {
            AggregateTypedEventMessage obj = new AggregateTypedEventMessage();
            obj.type = type;
            obj.eventMessage = events.next();
            volatileEvents.add(obj);
        }
    }

    @Override
    public synchronized DomainEventStream readEvents(String type, Object identifier) {
        ArrayList<DomainEventMessage<?>> selection = new ArrayList<DomainEventMessage<?>>();
        for (AggregateTypedEventMessage typedMessage : volatileEvents) {
            if (typedMessage.type.equals(type)) {
                DomainEventMessage<?> evMsg = typedMessage.eventMessage;
                if (identifier.equals(evMsg.getAggregateIdentifier())) {
                    selection.add(typedMessage.eventMessage);
                }
            }
        }

        return new SimpleDomainEventStream(selection);
    }

    public TimestampCutoffReadonlyEventStore cutoff(DateTime cutOffTimestamp) {
        return new TimestampCutoffReadonlyEventStore(this, this, cutOffTimestamp);
    }
}
