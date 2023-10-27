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
import java.util.Arrays;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Pojo for the data needed by the db-scheduler Scheduler. This will need to be serializable by the
 * {@link com.github.kagkarlsson.scheduler.serializer.Serializer} configured on the
 * {@link com.github.kagkarlsson.scheduler.Scheduler}. This one is used with the
 * {@link DbSchedulerEventScheduler#binaryTask()}
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class DbSchedulerBinaryEventData implements Serializable {

    private byte[] p;
    private String c;
    private String r;
    private byte[] m;

    /**
     * Crates a new {@link DbSchedulerHumanReadableEventData} with all the fields set.
     *
     * @param serializedPayload  The {@code byte[]} with the payload.
     * @param payloadClass       The {@link String} which tells what the class of the scope payload is.
     * @param revision           The {@link String} with the revision value of the payload.
     * @param serializedMetadata The {@code byte[]} containing the metadata about the deadline.
     */
    public DbSchedulerBinaryEventData(
            byte[] serializedPayload,
            String payloadClass,
            String revision,
            byte[] serializedMetadata
    ) {
        this.p = serializedPayload;
        this.c = payloadClass;
        this.r = revision;
        this.m = serializedMetadata;
    }

    @SuppressWarnings("unused")
    DbSchedulerBinaryEventData() {

    }

    /**
     * Gets the serialized payload.
     *
     * @return the payload as {@link String}
     */
    public byte[] getP() {
        return p;
    }

    /**
     * Sets the serialized payload.
     *
     * @param p as {@link String}
     */
    public void setP(byte[] p) {
        this.p = p;
    }

    /**
     * Gets the payload class.
     *
     * @return the payload class as {@link String}
     */
    public String getC() {
        return c;
    }

    /**
     * Sets the payload class.
     *
     * @param c as {@link String}
     */
    @SuppressWarnings("unused")
    public void setC(String c) {
        this.c = c;
    }

    /**
     * Gets the revision.
     *
     * @return the revision as {@link String}
     */
    public String getR() {
        return r;
    }

    /**
     * Sets the revision.
     *
     * @param r as {@link String}
     */
    public void setR(String r) {
        this.r = r;
    }

    /**
     * Gets the serialized metadata.
     *
     * @return the serialized meta data as {@link String}
     */
    public byte[] getM() {
        return m;
    }

    /**
     * Sets the serialized metadata.
     *
     * @param m as {@link String}
     */
    @SuppressWarnings("unused")
    public void setM(byte[] m) {
        this.m = m;
    }

    @Override
    public String toString() {
        return format("DbScheduler event data, serializedPayload: [%s], " +
                              "payloadClass: [%s], " +
                              "revision: [%s], " +
                              "serializedMetadata: [%s]",
                      Arrays.toString(p), c, r, Arrays.toString(m));
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(p), c, r, Arrays.hashCode(m));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DbSchedulerBinaryEventData other = (DbSchedulerBinaryEventData) obj;
        return Arrays.equals(this.p, other.p) &&
                Objects.equals(this.c, other.c) &&
                Objects.equals(this.r, other.r) &&
                Arrays.equals(this.m, other.m);
    }
}
