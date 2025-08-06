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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageType;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;

import java.time.Instant;
import java.util.Map;

/**
 * Interface describing the properties of a stored {@link EventMessage event messages}.
 *
 * @param <T> The content type of the {@link #payload()}.
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 2.0.0
 */
public interface EventData<T> {

    /**
     * Returns the identifier of the stored event.
     *
     * @return The identifier of the stored event.
     */
    @Nonnull
    String getEventIdentifier();

    /**
     * Returns the type of the stored event.
     * <p>
     * Typically refers to the {@link MessageType#name()}.
     *
     * @return The type of the stored event.
     */
    @Nonnull
    String type();

    /**
     * Returns the version of the stored event.
     * <p>
     * Typically refers to the {@link MessageType#version()}.
     *
     * @return The version of the stored event.
     */
    @Nonnull
    String version();

    /**
     * Returns the payload of the stored event.
     *
     * @return The payload of the stored event.
     */
    @Nonnull
    T payload();

    /**
     * Returns the metadata of the stored event.
     *
     * @return The metadata of the stored event.
     */
    @Nonnull
    Map<String, String> metaData();

    /**
     * Returns the timestamp at which the stored event was first created.
     *
     * @return The timestamp at which the stored event was first created.
     */
    @Nonnull
    Instant getTimestamp();

    /**
     * Returns the serialized data of the MetaData of the serialized Event.
     *
     * @return the serialized data of the MetaData of the serialized Event
     * @deprecated In favor of {@link #metaData()}.
     */
    @Deprecated
    SerializedObject<T> getMetaData();

    /**
     * Returns the serialized data of the Event Message's payload.
     *
     * @return the serialized data of the Event Message's payload
     * @deprecated In favor of {@link #payload()} for the {@link SerializedObject#getData()}, and {@link #type()} for
     * the {@link SerializedType#getName()} and {@link #version()} for the {@link SerializedType#getRevision()}.
     */
    @Deprecated
    SerializedObject<T> getPayload();
}
