/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.mongo.documentpercommit;

/**
 * @author Rene de Waele
 */
public class CommitEntryConfiguration {

    private final String firstTimestampProperty, lastTimestampProperty, firstSequenceNumberProperty, lastSequenceNumberProperty,
            eventsProperty;

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

    public static Builder builder() {
        return new Builder();
    }

    public String firstTimestampProperty() {
        return firstTimestampProperty;
    }

    public String lastTimestampProperty() {
        return lastTimestampProperty;
    }

    public String firstSequenceNumberProperty() {
        return firstSequenceNumberProperty;
    }

    public String lastSequenceNumberProperty() {
        return lastSequenceNumberProperty;
    }

    public String eventsProperty() {
        return eventsProperty;
    }

    private static class Builder {

        private String firstTimestampProperty =
                "firstTimestamp", lastTimestampProperty = "lastTimestamp", firstSequenceNumberProperty =
                "firstSequenceNumber", lastSequenceNumberProperty = "lastSequenceNumber", eventsProperty = "events";

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
