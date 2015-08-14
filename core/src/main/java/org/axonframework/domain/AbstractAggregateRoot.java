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

package org.axonframework.domain;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;

import java.io.Serializable;

/**
 * Abstract implementation of the AggregateRoot interface. It provides the mechanism to keep track of uncommitted
 * events and maintains a version number based on the number of events generated.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AbstractAggregateRoot implements AggregateRoot, Serializable {

    private static final long serialVersionUID = 6330592271927197888L;

    private boolean deleted = false;

    /**
     * Registers an event to be published when the aggregate is saved, containing the given <code>payload</code> and no
     * (additional) meta-data.
     *
     * @param payload the payload of the event to register
     * @param <T>     The type of payload
     * @return The Event holding the given <code>payload</code>
     */
    protected <T> EventMessage<T> registerEvent(T payload) {
        return registerEvent(MetaData.emptyInstance(), payload);
    }

    /**
     * Registers an event to be published when the aggregate is saved.
     *
     * @param metaData The meta data of the event to register
     * @param payload  the payload of the event to register
     * @param <T>      The type of payload
     * @return The Event holding the given <code>payload</code>
     */
    protected <T> EventMessage<T> registerEvent(MetaData metaData, T payload) {
        final EventMessage<T> message = new GenericEventMessage<>(payload, metaData);
        registerEventMessage(message);
        return message;
    }

    /**
     * Registers an event message to be published when the aggregate is saved.
     *
     * @param message The message to publish
     * @param <T>     The payload type carried by the message
     */
    protected <T> void registerEventMessage(EventMessage<T> message) {
        if (CurrentUnitOfWork.isStarted()) {
            EventBus eventBus = CurrentUnitOfWork.get().getResource(EventBus.KEY);
            Assert.state(eventBus != null, "The Unit of Work did not supply an Event Bus");
            eventBus.publish(message);
        }
    }

    /**
     * Marks this aggregate as deleted, instructing a Repository to remove that aggregate at an appropriate time.
     * <p/>
     * Note that different Repository implementation may react differently to aggregates marked for deletion.
     * Typically,
     * Event Sourced Repositories will ignore the marking and expect deletion to be provided as part of Event
     * information.
     */
    protected void markDeleted() {
        this.deleted = true;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

}
