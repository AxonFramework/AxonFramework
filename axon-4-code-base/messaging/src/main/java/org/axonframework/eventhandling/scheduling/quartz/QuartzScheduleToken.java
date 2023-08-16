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

package org.axonframework.eventhandling.scheduling.quartz;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.eventhandling.scheduling.ScheduleToken;

import java.util.Objects;

import static java.lang.String.format;

import java.beans.ConstructorProperties;

/**
 * ScheduleToken implementation representing a scheduled Quartz Job.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class QuartzScheduleToken implements ScheduleToken {

    private static final long serialVersionUID = 7798276124742118925L;

    private final String jobIdentifier;
    private final String groupIdentifier;

    /**
     * Initialize a token for the given {@code jobIdentifier} and {@code groupIdentifier}.
     *
     * @param jobIdentifier   The identifier used when registering the job with quartz.
     * @param groupIdentifier The identifier of the group the job is part of.
     */
    @JsonCreator
    @ConstructorProperties({"jobIdentifier", "groupIdentifier"})
    public QuartzScheduleToken(@JsonProperty("jobIdentifier") String jobIdentifier,
                               @JsonProperty("groupIdentifier") String groupIdentifier) {
        this.jobIdentifier = jobIdentifier;
        this.groupIdentifier = groupIdentifier;
    }

    /**
     * Returns the Quartz job identifier.
     *
     * @return the Quartz job identifier
     */
    public String getJobIdentifier() {
        return jobIdentifier;
    }

    /**
     * Returns the Quartz group identifier.
     *
     * @return the Quartz group identifier
     */
    public String getGroupIdentifier() {
        return groupIdentifier;
    }

    @Override
    public String toString() {
        return format("Quartz Schedule token for job [%s] in group [%s]", jobIdentifier, groupIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobIdentifier, groupIdentifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final QuartzScheduleToken other = (QuartzScheduleToken) obj;
        return Objects.equals(this.jobIdentifier, other.jobIdentifier)
                && Objects.equals(this.groupIdentifier, other.groupIdentifier);
    }
}
