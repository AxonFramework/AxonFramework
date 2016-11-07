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

package org.axonframework.mongo.eventsourcing.eventstore.documentpercommit;

import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.EventEntry;

/**
 * Configuration for a mongo event store entry that contains an array of {@link EventEntry event entries} that are part
 * of the same UnitOfWork commit.
 *
 * @author Rene de Waele
 */
public class CommitEntryConfiguration {

    private final String firstTimestampProperty, lastTimestampProperty, firstSequenceNumberProperty,
            lastSequenceNumberProperty, eventsProperty;

    /**
     * Returns the default {@link CommitEntryConfiguration}.
     *
     * @return the default configuration
     */
    public static CommitEntryConfiguration getDefault() {
        return builder().build();
    }

    private CommitEntryConfiguration(Builder builder) {
        firstTimestampProperty = builder.firstTimestampProperty;
        lastTimestampProperty = builder.lastTimestampProperty;
        firstSequenceNumberProperty = builder.firstSequenceNumberProperty;
        lastSequenceNumberProperty = builder.lastSequenceNumberProperty;
        eventsProperty = builder.eventsProperty;
    }

    /**
     * Returns a new builder that is initialized with default values.
     *
     * @return a new builder with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the name of the property containing the timestamp of the first event entry of the commit.
     *
     * @return the property for the timestamp of the first event entry
     */
    public String firstTimestampProperty() {
        return firstTimestampProperty;
    }

    /**
     * Returns the name of the property containing the timestamp of the last event entry of the commit.
     *
     * @return the property for the timestamp of the last event entry
     */
    public String lastTimestampProperty() {
        return lastTimestampProperty;
    }

    /**
     * Returns the name of the property containing the sequence number of the first event entry.
     *
     * @return the property for the timestamp of the first sequence number
     */
    public String firstSequenceNumberProperty() {
        return firstSequenceNumberProperty;
    }

    /**
     * Returns the name of the property containing the sequence number of the last event entry.
     *
     * @return the property for the timestamp of the last sequence number
     */
    public String lastSequenceNumberProperty() {
        return lastSequenceNumberProperty;
    }

    /**
     * Returns the name of the property containing the array of event entries.
     *
     * @return the property for the array of event entries
     */
    public String eventsProperty() {
        return eventsProperty;
    }

    private static class Builder {

        private String firstTimestampProperty = "firstTimestamp", lastTimestampProperty = "lastTimestamp",
                firstSequenceNumberProperty = "firstSequenceNumber", lastSequenceNumberProperty = "lastSequenceNumber",
                eventsProperty = "events";

        public Builder withFirstTimestampProperty(String firstTimestampProperty) {
            this.firstTimestampProperty = firstTimestampProperty;
            return this;
        }

        public Builder withLastTimestampProperty(String lastTimestampProperty) {
            this.lastTimestampProperty = lastTimestampProperty;
            return this;
        }

        public Builder withFirstSequenceNumberProperty(String firstSequenceNumberProperty) {
            this.firstSequenceNumberProperty = firstSequenceNumberProperty;
            return this;
        }

        public Builder withLastSequenceNumberProperty(String lastSequenceNumberProperty) {
            this.lastSequenceNumberProperty = lastSequenceNumberProperty;
            return this;
        }

        public Builder withEventsProperty(String eventsProperty) {
            this.eventsProperty = eventsProperty;
            return this;
        }

        public CommitEntryConfiguration build() {
            return new CommitEntryConfiguration(this);
        }
    }
}
