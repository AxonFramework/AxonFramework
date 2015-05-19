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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.MetaData;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Specialist UnitOfWorkListenerAdapter that allows modification of Meta Data of Events during the "beforeCommit" phase
 * of a Unit of Work. This is the phase where all events to publish are known, but haven't been persisted or published
 * yet.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public abstract class MetaDataMutatingUnitOfWorkListenerAdapter extends UnitOfWorkListenerAdapter {

    @Override
    public final <T> EventMessage<T> onEventRegistered(UnitOfWork unitOfWork, EventMessage<T> event) {
        return wrapped(event);
    }

    @Override
    public void onPrepareCommit(UnitOfWork unitOfWork, Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
        int i = 0;
        for (EventMessage<?> event : events) {
            final Map<String, ?> additionalMetaData = assignMetaData(event, events, i++);
            if (additionalMetaData != null) {
                EventMessage<?> changedEvent = event.andMetaData(additionalMetaData);
                if (changedEvent instanceof MutableEventMessage) {
                    ((MutableEventMessage) changedEvent).makeImmutable();
                }
            }
        }
    }

    /**
     * Defines the additional meta data to assign to the given <code>event</code>. For reference, the entire list of
     * <code>events</code> is provided, where the given <code>event</code> can be found at given <code>index</code>.
     * <p/>
     * Any entries returned that already exist are overwritten by the entry in the Map returned. Other entries are
     * added.
     * <p/>
     * This method is invoked for each of the <code>events</code>.
     *
     * @param event  The event for which to assign additional meta data
     * @param events The entire list of events part of this unit of work
     * @param index  The index at which the event can be found in the list of events
     * @return a Map with entries to add to the Meta Data of the given event
     */
    protected abstract Map<String, ?> assignMetaData(EventMessage event, List<EventMessage> events, int index);

    private <T> EventMessage<T> wrapped(EventMessage<T> event) {
        if (event instanceof DomainEventMessage) {
            return new MutableDomainEventMessage<T>((DomainEventMessage<T>) event);
        } else {
            return new MutableEventMessage<T>(event);
        }
    }

    private static class MutableEventMessage<T> implements EventMessage<T> {

        private static final long serialVersionUID = -5697283646053267959L;
        private final EventMessage<T> event;
        private volatile MetaData metaData;
        private volatile boolean fixed;

        public MutableEventMessage(EventMessage<T> event) {
            this.event = event;
            this.metaData = event.getMetaData();
        }

        @Override
        public String getIdentifier() {
            return event.getIdentifier();
        }

        @Override
        public MetaData getMetaData() {
            return metaData;
        }

        @Override
        public T getPayload() {
            return event.getPayload();
        }

        @Override
        public Class getPayloadType() {
            return event.getPayloadType();
        }

        @Override
        public ZonedDateTime getTimestamp() {
            return event.getTimestamp();
        }

        @Override
        public EventMessage<T> withMetaData(Map<String, ?> metaData) {
            if (fixed) {
                return event.withMetaData(metaData);
            } else {
                this.metaData = new MetaData(metaData);
                return this;
            }
        }

        @Override
        public EventMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
            if (fixed) {
                return event.withMetaData(this.metaData).andMetaData(additionalMetaData);
            } else {
                Map<String, Object> newMetaData = new HashMap<String, Object>(additionalMetaData);
                newMetaData.putAll(this.metaData);
                this.metaData = new MetaData(newMetaData);
                return this;
            }
        }

        protected EventMessage<T> getWrappedEvent() {
            return event;
        }

        /**
         * Returns the wrapped event, with the meta data currently configured on this instance. This makes sure that
         * an immutable version of the event is actually serialized.
         *
         * @return an immutable representation of the current state of this object
         */
        protected Object writeReplace() {
            return event.withMetaData(metaData);
        }

        public void makeImmutable() {
            this.fixed = true;
        }
    }

    private static class MutableDomainEventMessage<T> extends MutableEventMessage<T> implements DomainEventMessage<T> {

        private static final long serialVersionUID = -1814502500382189837L;

        public MutableDomainEventMessage(DomainEventMessage<T> event) {
            super(event);
        }

        @Override
        public long getSequenceNumber() {
            return getWrappedEvent().getSequenceNumber();
        }

        @Override
        public Object getAggregateIdentifier() {
            return getWrappedEvent().getAggregateIdentifier();
        }

        @Override
        protected DomainEventMessage<T> getWrappedEvent() {
            return (DomainEventMessage<T>) super.getWrappedEvent();
        }

        @Override
        public DomainEventMessage<T> withMetaData(Map<String, ?> metaData) {
            return (DomainEventMessage<T>) super.withMetaData(metaData);
        }

        @Override
        public DomainEventMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
            return (DomainEventMessage<T>) super.andMetaData(additionalMetaData);
        }
    }
}
