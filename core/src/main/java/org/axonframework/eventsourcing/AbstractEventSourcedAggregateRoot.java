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

package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
import org.axonframework.domain.*;

import javax.persistence.Basic;
import javax.persistence.MappedSuperclass;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Abstract convenience class to be extended by all aggregate roots. The AbstractEventSourcedAggregateRoot tracks all
 * uncommitted events. It also provides convenience methods to initialize the state of the aggregate root based on a
 * {@link org.axonframework.domain.DomainEventStream}, which can be used for event sourcing.
 *
 * @author Allard Buijze
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractEventSourcedAggregateRoot extends AbstractAggregateRoot
        implements EventSourcedAggregateRoot {

    private static final long serialVersionUID = 5868786029296883724L;
    private transient boolean inReplay = false;

    private transient boolean applyingEvents = false;
    private transient Queue<PayloadAndMetaData> eventsToApply = new ArrayDeque<>();

    @Basic(optional = true)
    private Long lastEventSequenceNumber;

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation is aware of a special type of <code>DomainEvents</code>: the <code>AggregateSnapshot</code>,
     * which is a snapshot event, containing the actual aggregate inside.
     * <p/>
     * <code>AggregateSnapshot</code> events are used to initialize the aggregate with the correct version ({@link
     * #getVersion()}).
     *
     * @throws IllegalStateException if this aggregate was already initialized.
     */
    @Override
    public void initializeState(DomainEventStream domainEventStream) {
        Assert.state(getLastEventSequenceNumber() == null, "Aggregate is already initialized");
        inReplay = true;
        long lastSequenceNumber = -1;
        while (domainEventStream.hasNext()) {
            DomainEventMessage event = domainEventStream.next();
            lastSequenceNumber = event.getSequenceNumber();
            handleRecursively(event);
        }
        lastEventSequenceNumber = lastSequenceNumber >= 0 ? lastSequenceNumber : null;
        inReplay = false;
    }

    @Override
    protected <T> EventMessage<T> registerEvent(T payload) {
        return registerEvent(MetaData.emptyInstance(), payload);
    }

    @Override
    protected <T> EventMessage<T> registerEvent(MetaData metaData, T payload) {
        final GenericDomainEventMessage<T> message = new GenericDomainEventMessage<T>(getIdentifier(),
                                                                                      nextSequenceNumber(),
                                                                                      payload, metaData);
        registerEventMessage(message);
        return message;
    }

    @Override
    protected <T> void registerEventMessage(EventMessage<T> message) {
        super.registerEventMessage(message);
        if (message instanceof DomainEventMessage) {
            DomainEventMessage<T> domainEventMessage = (DomainEventMessage<T>) message;
            if (domainEventMessage.getSequenceNumber() > lastEventSequenceNumber) {
                lastEventSequenceNumber = domainEventMessage.getSequenceNumber();
            }
        }
    }

    /**
     * Apply the provided event. Applying events means they are added to the uncommitted event queue and forwarded to
     * the {@link #handle(org.axonframework.domain.EventMessage)} event handler method} for processing.
     * <p/>
     * The event is applied on all entities part of this aggregate.
     *
     * @param eventPayload The payload of the event to apply
     */
    protected void apply(Object eventPayload) {
        apply(eventPayload, MetaData.emptyInstance());
    }

    /**
     * Apply the provided event. Applying events means they are added to the uncommitted event queue and forwarded to
     * the {@link #handle(org.axonframework.domain.EventMessage)} event handler method} for processing.
     * <p/>
     * The event is applied on all entities part of this aggregate.
     *
     * @param eventPayload The payload of the event to apply
     * @param metaData     any meta-data that must be registered with the Event
     */
    protected void apply(Object eventPayload, MetaData metaData) {
        if (inReplay) {
            return;
        }
        // ensure that nested invocations know they are nested
        boolean wasNested = applyingEvents;
        applyingEvents = true;
        try {
            if (getIdentifier() == null) {
                Assert.state(!wasNested,
                             "Applying an event in an @EventSourcingHandler is allowed, but only *after* the "
                                     + "aggregate identifier has been set");
                // workaround for aggregates that set the aggregate identifier in an Event Handler
                if (getVersion() != null) {
                    throw new IncompatibleAggregateException("The Aggregate Identifier has not been initialized. "
                                                                     + "It must be initialized at the latest when the "
                                                                     + "first event is applied.");
                }
                final GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>(null, 0,
                                                                                                  eventPayload,
                                                                                                  metaData);
                handleRecursively(message);
                registerEventMessage(message);
            } else {
                // eventsToApply may have been set to null by serialization
                if (eventsToApply == null) {
                    eventsToApply = new ArrayDeque<>();
                }
                eventsToApply.add(new PayloadAndMetaData(eventPayload, metaData));
            }

            while (!wasNested && eventsToApply != null && !eventsToApply.isEmpty()) {
                final PayloadAndMetaData payloadAndMetaData = eventsToApply.poll();
                handleRecursively(registerEvent(payloadAndMetaData.metaData,
                                                payloadAndMetaData.payload));
            }
        } finally {
            applyingEvents = wasNested;
            //resets the aggregate state in case an exception is thrown
            if (!applyingEvents && eventsToApply != null) {
                eventsToApply.clear();
            }
        }
    }

    /**
     * Returns the sequence number of the last committed event, or <code>null</code> if no events have been committed
     * before.
     *
     * @return the sequence number of the last committed event
     */
    protected Long getLastEventSequenceNumber() {
        return lastEventSequenceNumber;
    }

    /**
     * Returns the sequence number for the next event message to be published by this aggregate. If no events have
     * been published this returns <code>0</code>, otherwise this method returns the sequence number of the last
     * event message incremented by 1.
     *
     * @return the sequence for the next event message
     */
    protected long nextSequenceNumber() {
        if (lastEventSequenceNumber == null) {
            lastEventSequenceNumber = 0L;
        }
        return lastEventSequenceNumber + 1L;
    }


    /**
     * Indicates whether this aggregate is in "live" mode. This is the case when an aggregate is fully initialized and
     * ready to handle commands.
     * <p/>
     * Typically, this method is used to check the state of the aggregate while events are being handled. When the
     * aggregate is handling an event to reconstruct its current state, <code>isLive()</code> returns
     * <code>false</code>. If an event is being handled because is was applied as a result of the current command being
     * executed, it returns <code>true</code>.
     * <p/>
     * <code>isLive()</code> can be used to prevent expensive calculations while event sourcing.
     *
     * @return <code>true</code> if the aggregate is live, <code>false</code> when the aggregate is relaying historic
     * events.
     */
    protected boolean isLive() {
        return !inReplay;
    }

    private void handleRecursively(EventMessage<?> event) {
        handle(event);
        Iterable<? extends EventSourcedEntity> childEntities = getChildEntities();
        if (childEntities != null) {
            for (EventSourcedEntity entity : childEntities) {
                if (entity != null) {
                    entity.registerAggregateRoot(this);
                    entity.handleRecursively(event);
                }
            }
        }
    }

    /**
     * Returns a collection of event sourced entities directly referenced by this entity. May return null or an empty
     * list to indicate no child entities are available. The collection may also contain null values.
     * <p/>
     * Events are propagated to the children in the order that the iterator of the return value provides.
     *
     * @return a list of event sourced entities contained in this aggregate
     */
    protected abstract Iterable<? extends EventSourcedEntity> getChildEntities();

    /**
     * Apply state changes based on the given event.
     * <p/>
     * Note: Implementations of this method should *not* perform validation.
     *
     * @param event The event to handle
     */
    protected abstract void handle(EventMessage event);

    @Override
    public Long getVersion() {
        return getLastEventSequenceNumber();
    }

    private static class PayloadAndMetaData {

        private final Object payload;
        private final MetaData metaData;

        private PayloadAndMetaData(Object payload, MetaData metaData) {
            this.payload = payload;
            this.metaData = metaData;
        }
    }
}
