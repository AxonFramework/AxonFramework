/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.deadline.dbscheduler;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Pojo that contains the needed information for a {@link com.github.kagkarlsson.scheduler.task.Task} handling a
 * deadline. Will be serialized and deserialized using the configured {@link Serializer} on the
 * {@link com.github.kagkarlsson.scheduler.Scheduler}. This object is used with the
 * {@link DbSchedulerDeadlineManager#humanReadableTask()}.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@SuppressWarnings("Duplicates")
public class DbSchedulerHumanReadableDeadlineDetails implements Serializable {

    private String deadlineName;
    private String name;
    private String scopeDescriptor;
    private String scopeDescriptorClass;
    private String payload;
    private String payloadClass;
    private String payloadRevision;
    private String metaData;

    DbSchedulerHumanReadableDeadlineDetails() {
        //no-args constructor needed for deserialization
    }

    /**
     * Creates a new {@link DbSchedulerHumanReadableDeadlineDetails} object, likely based on a {@link DeadlineMessage}.
     *
     * @param deadlineName         The {@link String} with the name of the deadline.
     * @param name                 The {@link DeadlineMessage#name()} of the deadline as a
     *                             {@link QualifiedName#toString()}.
     * @param scopeDescriptor      The {@link String} which tells what the scope is of the deadline.
     * @param scopeDescriptorClass The {@link String} which tells what the class of the scope descriptor is.
     * @param payload              The {@link String} with the payload. This can be null.
     * @param payloadClass         The {@link String} which tells what the class of the scope payload is.
     * @param payloadRevision      The {@link String} which tells what the revision of the scope payload is.
     * @param metaData             The {@link String} containing the metadata about the deadline. This can be null.
     */
    @SuppressWarnings("squid:S107")
    DbSchedulerHumanReadableDeadlineDetails(@Nonnull String deadlineName,
                                            @Nonnull String name,
                                            @Nonnull String scopeDescriptor,
                                            @Nonnull String scopeDescriptorClass,
                                            @Nullable String payload,
                                            @Nullable String payloadClass,
                                            @Nullable String payloadRevision,
                                            @Nullable String metaData) {
        this.deadlineName = deadlineName;
        this.name = name;
        this.scopeDescriptor = scopeDescriptor;
        this.scopeDescriptorClass = scopeDescriptorClass;
        this.payload = payload;
        this.payloadClass = payloadClass;
        this.payloadRevision = payloadRevision;
        this.metaData = metaData;
    }

    /**
     * Created a new {@link DbSchedulerHumanReadableDeadlineDetails} object, using the supplied serializer where
     * needed.
     *
     * @param deadlineName The {@link String} with the name of the deadline.
     * @param descriptor   The {@link ScopeDescriptor} which tells what the scope is of the deadline.
     * @param message      The {@link DeadlineMessage} containing the payload and metadata which needs to be
     *                     serialized.
     * @param serializer   The {@link Serializer} used to serialize the {@code descriptor}, {@code payload},
     *                     {@code metadata}, as well as the whole {@link DbSchedulerHumanReadableDeadlineDetails}.
     * @return The serialized {@link String} representation of the details.
     */
    @SuppressWarnings("rawtypes")
    static DbSchedulerHumanReadableDeadlineDetails serialized(@Nonnull String deadlineName,
                                                              @Nonnull ScopeDescriptor descriptor,
                                                              @Nonnull DeadlineMessage message,
                                                              @Nonnull Serializer serializer) {
        SerializedObject<String> serializedDescriptor = serializer.serialize(descriptor, String.class);
        SerializedObject<String> serializedPayload = serializer.serialize(message.getPayload(), String.class);
        SerializedObject<String> serializedMetaData = serializer.serialize(message.getMetaData(), String.class);

        return new DbSchedulerHumanReadableDeadlineDetails(deadlineName,
                                                           message.name().toString(),
                                                           serializedDescriptor.getData(),
                                                           serializedDescriptor.getType().getName(),
                                                           serializedPayload.getData(),
                                                           serializedPayload.getType().getName(),
                                                           serializedPayload.getType().getRevision(),
                                                           serializedMetaData.getData());
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
     * Returns the {@link DeadlineMessage#name()} of this deadline.
     *
     * @return The {@link DeadlineMessage#name()} of this deadline.
     */
    public String getName() {
        return name;
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
     * Returns the {@link DbSchedulerHumanReadableDeadlineDetails} as an {@link GenericDeadlineMessage}, with the
     * {@code} timestamp set using the {@code GenericEventMessage.clock}.
     *
     * @return the {@link GenericDeadlineMessage} with all the properties of this pojo, and a timestamp.
     */
    @SuppressWarnings("rawtypes")
    public GenericDeadlineMessage asDeadLineMessage(Serializer serializer) {
        return new GenericDeadlineMessage<>(deadlineName,
                                            QualifiedName.fromString(name),
                                            getDeserializedPayload(serializer),
                                            getDeserializedMetaData(serializer));
    }

    private Object getDeserializedPayload(Serializer serializer) {
        SimpleSerializedObject<String> serializedDeadlinePayload =
                new SimpleSerializedObject<>(payload, String.class, payloadClass, payloadRevision);
        return serializer.deserialize(serializedDeadlinePayload);
    }

    private MetaData getDeserializedMetaData(Serializer serializer) {
        SimpleSerializedObject<String> serializedDeadlineMetaData =
                new SimpleSerializedObject<>(metaData, String.class, MetaData.class.getName(), null);
        return serializer.deserialize(serializedDeadlineMetaData);
    }

    /**
     * Returns the serialized {@link ScopeDescriptor} using the supplied {@link Serializer}. This will be an instance of
     * the {@code scopeDescriptorClass} property.
     *
     * @return the {@link ScopeDescriptor} that is serialized using the supplied {@link Serializer}.
     */
    public ScopeDescriptor getDeserializedScopeDescriptor(Serializer serializer) {
        SimpleSerializedObject<String> serializedDeadlineScope =
                new SimpleSerializedObject<>(scopeDescriptor, String.class, scopeDescriptorClass, null);
        return serializer.deserialize(serializedDeadlineScope);
    }

    @Override
    public String toString() {
        return format("DbScheduler deadline details, deadlineName: [%s], " +
                              "name: [%s], " +
                              "scopeDescriptor: [%s], " +
                              "scopeDescriptorClass: [%s], " +
                              "payload: [%s], " +
                              "payloadClass: [%s], " +
                              "payloadRevision: [%s], " +
                              "metadata: [%s]",
                      deadlineName,
                      name,
                      scopeDescriptor,
                      scopeDescriptorClass,
                      payload,
                      payloadClass,
                      payloadRevision,
                      metaData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deadlineName,
                            name,
                            scopeDescriptor,
                            scopeDescriptorClass,
                            payload,
                            payloadClass,
                            payloadRevision,
                            metaData);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DbSchedulerHumanReadableDeadlineDetails other = (DbSchedulerHumanReadableDeadlineDetails) obj;
        return Objects.equals(this.deadlineName, other.deadlineName) &&
                Objects.equals(this.name, other.name) &&
                Objects.equals(this.scopeDescriptor, other.scopeDescriptor) &&
                Objects.equals(this.scopeDescriptorClass, other.scopeDescriptorClass) &&
                Objects.equals(this.payload, other.payload) &&
                Objects.equals(this.payloadClass, other.payloadClass) &&
                Objects.equals(this.payloadRevision, other.payloadRevision) &&
                Objects.equals(this.metaData, other.metaData);
    }
}
