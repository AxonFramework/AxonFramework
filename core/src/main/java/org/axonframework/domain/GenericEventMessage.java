/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.joda.time.DateTime;

import java.util.Map;

/**
 * Generic implementation of the EventMessage interface. It simply keeps a reference to the payload and MetaData.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class GenericEventMessage<T> implements EventMessage<T> {

    private final T payload;
    private final String eventIdentifier;
    private final DateTime timestamp;
    private final MetaData metaData;

    /**
     * Returns the given event as an EventMessage. If <code>event</code> already implements EventMessage, it is
     * returned
     * as-is. Otherwise, the given <code>event</code> is wrapped into a GenericEventMessage as its payload.
     *
     * @param event the event to wrap as EventMessage
     * @return an EventMessage containing given <code>event</code> as payload, or <code>event</code> if it already
     *         implements EventMessage.
     */
    public static EventMessage asEventMessage(Object event) {
        if (EventMessage.class.isInstance(event)) {
            return (EventMessage) event;
        }
        return new GenericEventMessage<Object>(event);
    }

    /**
     * Creates a GenericEventMessage with given <code>payload</code>, and an empty MetaData.
     *
     * @param payload The payload for the message
     */
    public GenericEventMessage(T payload) {
        this(payload, MetaData.emptyInstance());
    }

    /**
     * Creates a GenericEventMessage with given <code>payload</code> and given <code>metaData</code>.
     *
     * @param payload  The payload of the EventMessage
     * @param metaData The MetaData for the EventMessage
     */
    public GenericEventMessage(T payload, MetaData metaData) {
        this.metaData = metaData != null ? metaData : MetaData.emptyInstance();
        this.timestamp = new DateTime();
        this.payload = payload;
        this.eventIdentifier = IdentifierFactory.getInstance().generateIdentifier();
    }

    /**
     * Copy constructor that allows creation of a new GenericEventMessage with modified metaData. All information
     * from the <code>original</code> is copied, except for the metaData.
     *
     * @param original The original message
     * @param metaData The MetaData for the new message
     */
    protected GenericEventMessage(GenericEventMessage<T> original, MetaData metaData) {
        this.metaData = metaData;
        this.timestamp = original.getTimestamp();
        this.payload = original.getPayload();
        this.eventIdentifier = original.getEventIdentifier();
    }

    /**
     * Creates a GenericEventMessage with given <code>payload</code> and given <code>metaData</code>. Note that a copy
     * of the given Map is made. Any modification of the given <code>metaData</code> Map does not influence the
     * EventMessage's MetaData.
     *
     * @param metaData The map providing MetaData for the EventMessage
     * @param payload  The payload of the EventMessage
     */
    public GenericEventMessage(Map<String, Object> metaData, T payload) {
        this(payload, new MetaData(metaData));
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Class getPayloadType() {
        return payload.getClass();
    }

    @Override
    public EventMessage<T> withMetaData(MetaData metaData) {
        if (getMetaData().equals(metaData)) {
            return this;
        }
        return new GenericEventMessage<T>(this, metaData);
    }

    @Override
    public String toString() {
        return String.format("GenericEventMessage[%s]", getPayload().toString());
    }
}
