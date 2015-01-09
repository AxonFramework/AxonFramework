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
import org.axonframework.domain.AbstractAggregateRoot;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;

import java.util.ArrayDeque;
import java.util.Queue;
import javax.persistence.MappedSuperclass;

/**
 * Abstract convenience class to be extended by all aggregate roots. The AbstractEventSourcedAggregateRoot tracks all
 * uncommitted events. It also provides convenience methods to initialize the state of the aggregate root based on a
 * {@link org.axonframework.domain.DomainEventStream}, which can be used for event sourcing.
 *
 * @param <I> The type of the identifier of this aggregate
 * @author Allard Buijze
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractEventSourcedAggregateRoot<I> extends AbstractAggregateRoot<I>
        implements EventSourcedAggregateRoot<I> {

    private static final long serialVersionUID = 5868786029296883724L;
    private transient boolean inReplay = false;

    private transient boolean applyingEvents = false;
    private transient Queue<PayloadAndMetaData> eventsToApply = new ArrayDeque<PayloadAndMetaData>();

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
        Assert.state(getUncommittedEventCount() == 0, "Aggregate is already initialized");
        inReplay = true;
        long lastSequenceNumber = -1;
        while (domainEventStream.hasNext()) {
            DomainEventMessage event = domainEventStream.next();
            lastSequenceNumber = event.getSequenceNumber();
            handleRecursively(event);
        }
        initializeEventStream(lastSequenceNumber);
        inReplay = false;
    }

    /**
     * Apply the provided event. Applying events means they are added to the uncommitted event queue and forwarded to
     * the {@link #handle(org.axonframework.domain.DomainEventMessage)} event handler method} for processing.
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
     * the {@link #handle(org.axonframework.domain.DomainEventMessage)} event handler method} for processing.
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
                if (getUncommittedEventCount() > 0 || getVersion() != null) {
                    throw new IncompatibleAggregateException("The Aggregate Identifier has not been initialized. "
                                                                     + "It must be initialized at the latest when the "
                                                                     + "first event is applied.");
                }
                final GenericDomainEventMessage<Object> event = new GenericDomainEventMessage<Object>(null, 0, eventPayload, metaData);
                handleRecursively(event);
                registerEvent(event);
            } else {
                // eventsToApply may heb been set to null by serialization
                if (eventsToApply == null) {
                    eventsToApply = new ArrayDeque<PayloadAndMetaData>();
                }
                eventsToApply.add(new PayloadAndMetaData(eventPayload, metaData));
            }

            while (!wasNested && eventsToApply != null && !eventsToApply.isEmpty()) {
                final PayloadAndMetaData payloadAndMetaData = eventsToApply.poll();
                handleRecursively(registerEvent(payloadAndMetaData.metaData, payloadAndMetaData.payload));
            }
        } finally {
            applyingEvents = wasNested;
        }
    }

    @Override
    public void commitEvents() {
        applyingEvents = false;
        if (eventsToApply != null) {
            eventsToApply.clear();
        }
        super.commitEvents();
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

    private void handleRecursively(DomainEventMessage event) {
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
    protected abstract void handle(DomainEventMessage event);

    @Override
    public Long getVersion() {
        return getLastCommittedEventSequenceNumber();
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
