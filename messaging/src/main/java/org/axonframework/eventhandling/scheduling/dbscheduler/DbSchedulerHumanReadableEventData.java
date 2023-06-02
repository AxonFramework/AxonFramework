/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.dbscheduler;

import java.io.Serializable;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Pojo for the data needed by the db-scheduler Scheduler. This will need to be serializable by the
 * {@link com.github.kagkarlsson.scheduler.serializer.Serializer} configured on the
 * {@link com.github.kagkarlsson.scheduler.Scheduler}. This one is used with the
 * {@link DbSchedulerEventScheduler#humanReadableTask()}
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class DbSchedulerHumanReadableEventData implements Serializable {

    private String serializedPayload;
    private String payloadClass;
    private String revision;
    private String serializedMetadata;

    /**
     * Crates a new {@link DbSchedulerHumanReadableEventData} with all the fields set.
     *
     * @param serializedPayload  The {@link String} with the payload.
     * @param payloadClass       The {@link String} which tells what the class of the scope payload is.
     * @param revision           The {@link String} with the revision value of the payload.
     * @param serializedMetadata The {@link String} containing the metadata about the deadline.
     */
    public DbSchedulerHumanReadableEventData(
            String serializedPayload,
            String payloadClass,
            String revision,
            String serializedMetadata
    ) {
        this.serializedPayload = serializedPayload;
        this.payloadClass = payloadClass;
        this.revision = revision;
        this.serializedMetadata = serializedMetadata;
    }

    @SuppressWarnings("unused")
    DbSchedulerHumanReadableEventData() {

    }

    /**
     * Gets the serialized payload.
     *
     * @return the payload as {@link String}
     */
    public String getSerializedPayload() {
        return serializedPayload;
    }

    /**
     * Sets the serialized payload.
     *
     * @param serializedPayload as {@link String}
     */
    public void setSerializedPayload(String serializedPayload) {
        this.serializedPayload = serializedPayload;
    }

    /**
     * Gets the payload class.
     *
     * @return the payload class as {@link String}
     */
    public String getPayloadClass() {
        return payloadClass;
    }

    /**
     * Sets the payload class.
     *
     * @param payloadClass as {@link String}
     */
    @SuppressWarnings("unused")
    public void setPayloadClass(String payloadClass) {
        this.payloadClass = payloadClass;
    }

    /**
     * Gets the revision.
     *
     * @return the revision as {@link String}
     */
    public String getRevision() {
        return revision;
    }

    /**
     * Sets the revision.
     *
     * @param revision as {@link String}
     */
    public void setRevision(String revision) {
        this.revision = revision;
    }

    /**
     * Gets the serialized metadata.
     *
     * @return the serialized meta data as {@link String}
     */
    public String getSerializedMetadata() {
        return serializedMetadata;
    }

    /**
     * Sets the serialized metadata.
     *
     * @param serializedMetadata as {@link String}
     */
    @SuppressWarnings("unused")
    public void setSerializedMetadata(String serializedMetadata) {
        this.serializedMetadata = serializedMetadata;
    }

    @Override
    public String toString() {
        return format("DbScheduler event data, serializedPayload: [%s], " +
                              "payloadClass: [%s], " +
                              "revision: [%s], " +
                              "serializedMetadata: [%s]",
                      serializedPayload,
                      payloadClass,
                      revision,
                      serializedMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serializedPayload, payloadClass, revision, serializedMetadata);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DbSchedulerHumanReadableEventData other = (DbSchedulerHumanReadableEventData) obj;
        return Objects.equals(this.serializedPayload, other.serializedPayload) &&
                Objects.equals(this.payloadClass, other.payloadClass) &&
                Objects.equals(this.revision, other.revision) &&
                Objects.equals(this.serializedMetadata, other.serializedMetadata);
    }
}
