/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.scheduling.jobrunr;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.eventhandling.scheduling.ScheduleToken;

import java.beans.ConstructorProperties;
import java.util.UUID;

import static java.lang.String.format;

/**
 * ScheduleToken implementation representing a scheduled JobRunr Job.
 *
 * @author Gerard Klijs
 * @since 4.7.0
 */
public class JobRunrScheduleToken implements ScheduleToken {

    private static final long serialVersionUID = 7798276124742534225L;

    private final UUID jobIdentifier;

    /**
     * Initialize a token for the given {@code jobIdentifier}.
     *
     * @param jobIdentifier The identifier used when registering the job with JobRunr.
     */
    @JsonCreator
    @ConstructorProperties({"jobIdentifier", "groupIdentifier"})
    public JobRunrScheduleToken(@JsonProperty("jobIdentifier") UUID jobIdentifier) {
        this.jobIdentifier = jobIdentifier;
    }

    /**
     * Returns the JobRunr job identifier.
     *
     * @return the JobRunr job identifier
     */
    public UUID getJobIdentifier() {
        return jobIdentifier;
    }

    @Override
    public String toString() {
        return format("JobRunr Schedule token for job [%s]", jobIdentifier);
    }

    @Override
    public int hashCode() {
        return jobIdentifier.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final JobRunrScheduleToken other = (JobRunrScheduleToken) obj;
        return this.jobIdentifier.equals(other.jobIdentifier);
    }
}
