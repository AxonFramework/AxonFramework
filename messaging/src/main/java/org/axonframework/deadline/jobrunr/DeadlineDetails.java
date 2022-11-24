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

package org.axonframework.deadline.jobrunr;

import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.ScopeDescriptor;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pojo that contains the information about a {@link org.jobrunr.jobs.Job}, will typically be serialized using the
 * {@link JacksonJsonMapper}.
 *
 * @author Tom de Backer
 * @author Gerard Klijs
 * @since 4.7.0
 */
public class DeadlineDetails {

    private String deadlineName;
    private UUID deadlineId;
    private ScopeDescriptor scopeDescription;
    private Object payload;
    private Map<String, Object> metaData;

    private DeadlineDetails() {
        //private no-args constructor needed for Jackson
    }

    /**
     * Creates a new {@link DeadlineDetails} object, likely based on a
     * {@link org.axonframework.deadline.DeadlineMessage}.
     *
     * @param deadlineName     The {@link String} with the name of the deadline.
     * @param deadlineId       The {@link UUID} with the deadline id.
     * @param scopeDescription The {@link ScopeDescriptor} instance which tells what the scope is of the deadline.
     * @param payload          The {@link Object} with the payload. This can be null.
     * @param metaData         The {@link Map} containing the metadata about the deadline.
     */
    public DeadlineDetails(@Nonnull String deadlineName, @Nonnull UUID deadlineId,
                           @Nonnull ScopeDescriptor scopeDescription, @Nullable Object payload,
                           @Nonnull Map<String, Object> metaData) {
        //needed to optionally initialize the aggregate identifier
        scopeDescription.scopeDescription();
        this.deadlineName = deadlineName;
        this.deadlineId = deadlineId;
        this.scopeDescription = scopeDescription;
        this.payload = payload;
        this.metaData = metaData;
    }

    /**
     * Returns the {@link String} with the name of the deadline.
     *
     * @return The {@link String} with the name of the deadline.
     */
    public String getDeadlineName() {
        return deadlineName;
    }

    /**
     * Returns The {@link UUID} with the deadline id.
     *
     * @return The {@link UUID} with the deadline id.
     */
    public UUID getDeadlineId() {
        return deadlineId;
    }

    /**
     * Returns the {@link ScopeDescriptor} instance which tells what the scope is of the deadline.
     *
     * @return The {@link ScopeDescriptor} instance which tells what the scope is of the deadline.
     */
    public ScopeDescriptor getScopeDescription() {
        return scopeDescription;
    }

    /**
     * Returns the {@link Object} with the payload. This can be null.
     *
     * @return The {@link Object} with the payload. This can be null.
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * Returns the {@link Map} containing the metadata about the deadline.
     *
     * @return The {@link Map} containing the metadata about the deadline.
     */
    public Map<String, Object> getMetaData() {
        return metaData;
    }

    /**
     * Returns the {@link DeadlineDetails} as an {@link GenericDeadlineMessage}, with the {@code} timestamp set using
     * the {@code GenericEventMessage.clock} to set to the current time.
     *
     * @return the {@link GenericDeadlineMessage} with all the properties of this pojo, and a timestamp.
     */
    @SuppressWarnings("rawtypes")
    public GenericDeadlineMessage asDeadLineMessage() {
        return new GenericDeadlineMessage<>(
                deadlineName,
                deadlineId.toString(),
                payload,
                metaData,
                GenericEventMessage.clock.instant());
    }
}
