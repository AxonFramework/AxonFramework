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

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pojo that contains the information about a {@link org.jobrunr.jobs.Job}, will be serialized and deserialized using
 * the configured {@link Serializer} on the {@link JobRunrDeadlineManager}.
 *
 * @author Tom de Backer
 * @author Gerard Klijs
 * @since 4.7.0
 */
public class DeadlineDetails {

    private String deadlineName;
    private UUID deadlineId;
    private byte[] scopeDescriptor;
    private String scopeDescriptorClass;
    private byte[] payload;
    private String payloadClass;
    private String payloadRevision;
    private byte[] metaData;
    private Instant timestamp;

    private DeadlineDetails() {
        //private no-args constructor needed for deserialization
    }

    /**
     * Creates a new {@link DeadlineDetails} object, likely based on a
     * {@link org.axonframework.deadline.DeadlineMessage}.
     *
     * @param deadlineName         The {@link String} with the name of the deadline.
     * @param deadlineId           The {@link UUID} with the deadline id.
     * @param scopeDescriptor      The {@code byte[]} which tells what the scope is of the deadline.
     * @param scopeDescriptorClass The {@link String} which tells what the class of the scope descriptor is.
     * @param payload              The {@link Object} with the payload. This can be null.
     * @param payloadClass         The {@link String} which tells what the class of the scope payload is.
     * @param payloadRevision      The {@link String} which tells what the revision of the scope payload is.
     * @param metaData             The {@code byte[]} containing the metadata about the deadline.
     * @param timestamp            The {@link Instant} containing the timestamp of the deadline message.
     */
    @SuppressWarnings("squid:S107")
    public DeadlineDetails(@Nonnull String deadlineName, @Nonnull UUID deadlineId,
                           @Nonnull byte[] scopeDescriptor, @Nonnull String scopeDescriptorClass,
                           @Nullable byte[] payload, @Nullable String payloadClass, @Nullable String payloadRevision,
                           @Nonnull byte[] metaData, @Nonnull Instant timestamp) {
        this.deadlineName = deadlineName;
        this.deadlineId = deadlineId;
        this.scopeDescriptor = scopeDescriptor;
        this.scopeDescriptorClass = scopeDescriptorClass;
        this.payload = payload;
        this.payloadClass = payloadClass;
        this.payloadRevision = payloadRevision;
        this.metaData = metaData;
        this.timestamp = timestamp;
    }

    /**
     * @param deadlineName The {@link String} with the name of the deadline.
     * @param deadlineId   The {@link UUID} with the deadline id.
     * @param descriptor   The {@link ScopeDescriptor} which tells what the scope is of the deadline.
     * @param message      The {@link DeadlineMessage} containing the payload and metadata which needs to be
     *                     serialized.
     * @param serializer   The {@link Serializer} used to serialize the {@code descriptor}, {@code payload},
     *                     {@code metadata}, as well as the whole {@link DeadlineDetails}.
     * @return The serialized {@code byte[]} representation of the details.
     */
    @SuppressWarnings("rawtypes")
    static byte[] serialized(@Nonnull String deadlineName, @Nonnull UUID deadlineId,
                             @Nonnull ScopeDescriptor descriptor, @Nonnull DeadlineMessage message,
                             @Nonnull Serializer serializer) {
        SerializedObject<byte[]> serializedDescriptor = serializer.serialize(descriptor, byte[].class);
        SerializedObject<byte[]> serializedPayload = serializer.serialize(message.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetaData = serializer.serialize(message.getMetaData(), byte[].class);
        DeadlineDetails deadlineDetails = new DeadlineDetails(
                deadlineName,
                deadlineId,
                serializedDescriptor.getData(),
                serializedDescriptor.getType().getName(),
                serializedPayload.getData(),
                serializedPayload.getType().getName(),
                serializedPayload.getType().getRevision(),
                serializedMetaData.getData(),
                message.getTimestamp()
        );
        SerializedObject<byte[]> serializedDeadlineDetails = serializer.serialize(deadlineDetails, byte[].class);
        return serializedDeadlineDetails.getData();
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
     * Returns the serialized {@code byte[]} which tells what the scope is of the deadline.
     *
     * @return The serialized {@code byte[]} which tells what the scope is of the deadline.
     */
    public byte[] getScopeDescriptor() {
        return scopeDescriptor;
    }

    /**
     * Returns the {@link String} with the class of the scope descriptor.
     *
     * @return The {@link String} with the class of the scope descriptor.
     */
    public String getScopeDescriptorClass() {
        return scopeDescriptorClass;
    }

    /**
     * Returns the serialized {@code byte[]} of the payload. This can be null.
     *
     * @return The serialized {@code byte[]} of the payload. This can be null.
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Returns the {@link String} with the class of the payload. This can be null.
     *
     * @return The {@link String} with the class of the payload. This can be null.
     */
    public String getPayloadClass() {
        return payloadClass;
    }

    /**
     * Returns the {@link String} with the revision of the payload. This can be null.
     *
     * @return The {@link String} with the revision of the payload. This can be null.
     */
    public String getPayloadRevision() {
        return payloadRevision;
    }

    /**
     * Returns the {@code byte[]} containing the metadata about the deadline.
     *
     * @return The {@code byte[]} containing the metadata about the deadline.
     */
    public byte[] getMetaData() {
        return metaData;
    }

    /**
     * Returns the {@link Instant} containing the timestamp of the deadline.
     *
     * @return The {@link Instant} containing the timestamp of the deadline.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the {@link DeadlineDetails} as an {@link GenericDeadlineMessage}, with the {@code} timestamp set using
     * the {@code GenericEventMessage.clock} to set to the current time.
     *
     * @return the {@link GenericDeadlineMessage} with all the properties of this pojo, and a timestamp.
     */
    @SuppressWarnings("rawtypes")
    public GenericDeadlineMessage asDeadLineMessage(Serializer serializer) {
        return new GenericDeadlineMessage<>(
                deadlineName,
                deadlineId.toString(),
                getDeserializedPayload(serializer),
                getDeserializedMetaData(serializer),
                timestamp);
    }

    private Object getDeserializedPayload(Serializer serializer) {
        SimpleSerializedObject<byte[]> serializedDeadlinePayload = new SimpleSerializedObject<>(
                payload,
                byte[].class,
                payloadClass,
                payloadRevision
        );
        return serializer.deserialize(serializedDeadlinePayload);
    }

    private MetaData getDeserializedMetaData(Serializer serializer) {

        SimpleSerializedObject<byte[]> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                metaData, byte[].class, MetaData.class.getName(), null
        );
        return serializer.deserialize(serializedDeadlineMetaData);
    }

    public ScopeDescriptor getDeserializedScopeDescriptor(Serializer serializer) {
        SimpleSerializedObject<byte[]> serializedDeadlineScope = new SimpleSerializedObject<>(
                scopeDescriptor, byte[].class, scopeDescriptorClass, null
        );
        return serializer.deserialize(serializedDeadlineScope);
    }
}
