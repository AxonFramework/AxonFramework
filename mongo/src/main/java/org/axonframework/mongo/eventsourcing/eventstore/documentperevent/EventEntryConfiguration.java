/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore.documentperevent;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.mongo.eventsourcing.eventstore.StorageStrategy;
import org.bson.Document;

/**
 * Implementation of a {@link StorageStrategy} that stores one {@link Document} per commit of a list of {@link
 * EventMessage events}.
 *
 * @author Rene de Waele
 */
public class EventEntryConfiguration {

    private final String timestampProperty, eventIdentifierProperty, aggregateIdentifierProperty,
            sequenceNumberProperty, typeProperty, payloadTypeProperty, payloadRevisionProperty, payloadProperty,
            metaDataProperty;

    /**
     * Returns the default {@link EventEntryConfiguration}.
     *
     * @return the default {@link EventEntryConfiguration}
     */
    public static EventEntryConfiguration getDefault() {
        return builder().build();
    }

    private EventEntryConfiguration(Builder builder) {
        timestampProperty = builder.timestampProperty;
        eventIdentifierProperty = builder.eventIdentifierProperty;
        aggregateIdentifierProperty = builder.aggregateIdentifierProperty;
        sequenceNumberProperty = builder.sequenceNumberProperty;
        typeProperty = builder.typeProperty;
        payloadTypeProperty = builder.payloadTypeProperty;
        payloadRevisionProperty = builder.payloadRevisionProperty;
        payloadProperty = builder.payloadProperty;
        metaDataProperty = builder.metaDataProperty;
    }

    /**
     * Returns a new Builder for an {@link EventEntryConfiguration} initialized with default settings.
     *
     * @return a new Builder with default settings
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the name of the property with the timestamp of the event.
     *
     * @return the name of the property with the timestamp
     */
    public String timestampProperty() {
        return timestampProperty;
    }

    /**
     * Get the name of the property with the identifier of the event.
     *
     * @return the name of the propery with the event identifier
     */
    public String eventIdentifierProperty() {
        return eventIdentifierProperty;
    }

    /**
     * Get the name of the property with the aggregate identifier of the event.
     *
     * @return the name of the property with the aggregate identifier
     */
    public String aggregateIdentifierProperty() {
        return aggregateIdentifierProperty;
    }

    /**
     * Get the name of the property with the aggregate sequence number of the event.
     *
     * @return the name of the property with the aggregate sequence number
     */
    public String sequenceNumberProperty() {
        return sequenceNumberProperty;
    }

    /**
     * Get the name of the property with the aggregate type.
     *
     * @return the name of the property with the aggregate type
     */
    public String typeProperty() {
        return typeProperty;
    }

    /**
     * Get the name of the property with the payload type.
     *
     * @return the name of the property with the payload type
     */
    public String payloadTypeProperty() {
        return payloadTypeProperty;
    }

    /**
     * Get the name of the property with the payload revision.
     *
     * @return the name of the property with the payload revision
     */
    public String payloadRevisionProperty() {
        return payloadRevisionProperty;
    }

    /**
     * Get the name of the property with the payload data.
     *
     * @return the name of the property with the payload data
     */
    public String payloadProperty() {
        return payloadProperty;
    }

    /**
     * Get the name of the property with the metadata.
     *
     * @return the name of the property with the metadata
     */
    public String metaDataProperty() {
        return metaDataProperty;
    }

    private static class Builder {

        private String timestampProperty = "timestamp", eventIdentifierProperty = "eventIdentifier",
                aggregateIdentifierProperty = "aggregateIdentifier", sequenceNumberProperty = "sequenceNumber",
                typeProperty = "type", payloadTypeProperty = "payloadType", payloadRevisionProperty = "payloadRevision",
                payloadProperty = "serializedPayload", metaDataProperty = "serializedMetaData";

        public Builder withTimestampProperty(String timestampProperty) {
            this.timestampProperty = timestampProperty;
            return this;
        }

        public Builder withEventIdentifierProperty(String eventIdentifierProperty) {
            this.eventIdentifierProperty = eventIdentifierProperty;
            return this;
        }

        public Builder withAggregateIdentifierProperty(String aggregateIdentifierProperty) {
            this.aggregateIdentifierProperty = aggregateIdentifierProperty;
            return this;
        }

        public Builder withSequenceNumberProperty(String sequenceNumberProperty) {
            this.sequenceNumberProperty = sequenceNumberProperty;
            return this;
        }

        public Builder withTypeProperty(String typeProperty) {
            this.typeProperty = typeProperty;
            return this;
        }

        public Builder withPayloadTypeProperty(String payloadTypeProperty) {
            this.payloadTypeProperty = payloadTypeProperty;
            return this;
        }

        public Builder withPayloadRevisionProperty(String payloadRevisionProperty) {
            this.payloadRevisionProperty = payloadRevisionProperty;
            return this;
        }

        public Builder withPayloadProperty(String payloadProperty) {
            this.payloadProperty = payloadProperty;
            return this;
        }

        public Builder withMetaDataProperty(String metaDataProperty) {
            this.metaDataProperty = metaDataProperty;
            return this;
        }

        public EventEntryConfiguration build() {
            return new EventEntryConfiguration(this);
        }
    }
}
