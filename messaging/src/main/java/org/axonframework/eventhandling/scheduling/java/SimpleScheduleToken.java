/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.scheduling.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.eventhandling.scheduling.ScheduleToken;

import java.beans.ConstructorProperties;
import java.util.Objects;

/**
 * ScheduleToken for tasks event scheduled using the SimpleEventScheduler.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SimpleScheduleToken implements ScheduleToken {

    private final String tokenId;

    /**
     * Creates a SimpleScheduleToken with the given {@code tokenId}.
     *
     * @param tokenId The identifier referencing the scheduled task.
     */
    @JsonCreator
    @ConstructorProperties({"tokenId"})
    public SimpleScheduleToken(@JsonProperty("tokenId") String tokenId) {
        this.tokenId = tokenId;
    }

    /**
     * Returns the identifier of the scheduled task.
     *
     * @return the identifier of the scheduled task
     */
    public String getTokenId() {
        return tokenId;
    }

    @Override
    public String toString() {
        return "SimpleScheduleToken{" +
                "tokenId='" + tokenId + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SimpleScheduleToken other = (SimpleScheduleToken) obj;
        return Objects.equals(this.tokenId, other.tokenId);
    }
}
