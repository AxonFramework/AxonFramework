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
    private String scopeDescriptor;
    private String scopeDescriptorClass;
    private String payload;
    private String payloadClass;
    private String payloadRevision;
    private String metaData;

    private DeadlineDetails() {
        //private no-args constructor needed for deserialization
    }

    /**
     * Creates a new {@link DeadlineDetails} object, likely based on a
     * {@link org.axonframework.deadline.DeadlineMessage}.
     *
     * @param deadlineName         The {@link String} with the name of the deadline.
     * @param scopeDescriptor      The {@link String} which tells what the scope is of the deadline.
     * @param scopeDescriptorClass The {@link String} which tells what the class of the scope descriptor is.
     * @param payload              The {@link String} with the payload. This can be null.
     * @param payloadClass         The {@link String} which tells what the class of the scope payload is.
     * @param payloadRevision      The {@link String} which tells what the revision of the scope payload is.
     * @param metaData             The {@link String} containing the metadata about the deadline.
     */
    @SuppressWarnings("squid:S107")
    public DeadlineDetails(@Nonnull String deadlineName, @Nonnull String scopeDescriptor,
                           @Nonnull String scopeDescriptorClass, @Nullable String payload,
                           @Nullable String payloadClass, @Nullable String payloadRevision, @Nonnull String metaData) {
        this.deadlineName = deadlineName;
        this.scopeDescriptor = scopeDescriptor;
        this.scopeDescriptorClass = scopeDescriptorClass;
        this.payload = payload;
        this.payloadClass = payloadClass;
        this.payloadRevision = payloadRevision;
        this.metaData = metaData;
    }

    /**
     * Created a new {@link DeadlineDetails} object, and returns that serialized as a {@link String}. The reason
     * {@link String} was chosen over a byte array is that optionally the JubRunr dashboard is used, and as
     * {@link String} its easy to read the details there.
     *
     * @param deadlineName The {@link String} with the name of the deadline.
     * @param descriptor   The {@link ScopeDescriptor} which tells what the scope is of the deadline.
     * @param message      The {@link DeadlineMessage} containing the payload and metadata which needs to be
     *                     serialized.
     * @param serializer   The {@link Serializer} used to serialize the {@code descriptor}, {@code payload},
     *                     {@code metadata}, as well as the whole {@link DeadlineDetails}.
     * @return The serialized {@link String} representation of the details.
     */
    @SuppressWarnings("rawtypes")
    static String serialized(@Nonnull String deadlineName, @Nonnull ScopeDescriptor descriptor,
                             @Nonnull DeadlineMessage message,
                             @Nonnull Serializer serializer) {
        SerializedObject<String> serializedDescriptor = serializer.serialize(descriptor, String.class);
        SerializedObject<String> serializedPayload = serializer.serialize(message.getPayload(), String.class);
        SerializedObject<String> serializedMetaData = serializer.serialize(message.getMetaData(), String.class);
        DeadlineDetails deadlineDetails = new DeadlineDetails(
                deadlineName,
                serializedDescriptor.getData(),
                serializedDescriptor.getType().getName(),
                serializedPayload.getData(),
                serializedPayload.getType().getName(),
                serializedPayload.getType().getRevision(),
                serializedMetaData.getData()
        );
        SerializedObject<String> serializedDeadlineDetails = serializer.serialize(deadlineDetails, String.class);
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
     * Returns the serialized {@link String} which tells what the scope is of the deadline.
     *
     * @return The serialized {@link String} which tells what the scope is of the deadline.
     */
    public String getScopeDescriptor() {
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
     * Returns the serialized {@link String} of the payload. This can be null.
     *
     * @return The serialized {@link String} of the payload. This can be null.
     */
    public String getPayload() {
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
     * Returns the {@link String} containing the metadata about the deadline.
     *
     * @return The {@link String} containing the metadata about the deadline.
     */
    public String getMetaData() {
        return metaData;
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
                getDeserializedPayload(serializer),
                getDeserializedMetaData(serializer));
    }

    private Object getDeserializedPayload(Serializer serializer) {
        SimpleSerializedObject<String> serializedDeadlinePayload = new SimpleSerializedObject<>(
                payload,
                String.class,
                payloadClass,
                payloadRevision
        );
        return serializer.deserialize(serializedDeadlinePayload);
    }

    private MetaData getDeserializedMetaData(Serializer serializer) {

        SimpleSerializedObject<String> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                metaData, String.class, MetaData.class.getName(), null
        );
        return serializer.deserialize(serializedDeadlineMetaData);
    }

    /**
     * Returns the serialized {@link ScopeDescriptor} using the supplied {@link Serializer}. This will be an instance of
     * the {@code scopeDescriptorClass} property.
     *
     * @return the {@link ScopeDescriptor} that is serialized using the supplied {@link Serializer}.
     */
    public ScopeDescriptor getDeserializedScopeDescriptor(Serializer serializer) {
        SimpleSerializedObject<String> serializedDeadlineScope = new SimpleSerializedObject<>(
                scopeDescriptor, String.class, scopeDescriptorClass, null
        );
        return serializer.deserialize(serializedDeadlineScope);
    }
}
