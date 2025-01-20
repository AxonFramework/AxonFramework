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

package org.axonframework.deadline;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;

import java.io.Serial;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of the {@link DeadlineMessage} interface.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link DeadlineMessage}. May be {@link Void}
 *            if no payload was provided.
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3.0
 */
public class GenericDeadlineMessage<P> extends GenericEventMessage<P> implements DeadlineMessage<P> {

    @Serial
    private static final long serialVersionUID = 2615162095663478618L;

    private final String deadlineName;

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code type} and {@code deadlineName}.
     * <p>
     * The {@link #getPayload()} defaults to {@code null} and the {@link MetaData} defaults to an empty instance.
     *
     * @param type         The {@link MessageType type} for this {@link DeadlineMessage}.
     * @param deadlineName The type for this {@link DeadlineMessage}.
     */
    public GenericDeadlineMessage(@Nonnull MessageType type,
                                  @Nonnull String deadlineName) {
        this(deadlineName, type, null);
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code deadlineName}, {@code type}, and
     * {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param deadlineName The type for this {@link DeadlineMessage}.
     * @param type         The {@link MessageType type} for this {@link DeadlineMessage}.
     * @param payload      The payload of type {@code P} for this {@link DeadlineMessage}.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull MessageType type,
                                  @Nullable P payload) {
        this(deadlineName, type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code deadlineName}, {@code type}, {@code payload},
     * and {@code metaData}.
     *
     * @param deadlineName The name for this {@link DeadlineMessage}.
     * @param type         The {@link MessageType type} for this {@link DeadlineMessage}.
     * @param payload      The payload of type {@code P} for this {@link DeadlineMessage}.
     * @param metaData     The metadata for this {@link DeadlineMessage}.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull MessageType type,
                                  @Nullable P payload,
                                  @Nonnull Map<String, ?> metaData) {
        super(type, payload, metaData);
        this.deadlineName = deadlineName;
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code deadlineName}, {@code identifier}, {@code type},
     * {@code payload}, {@code metaData}, and {@code timestamp}.
     *
     * @param deadlineName The name for this {@link DeadlineMessage}.
     * @param identifier   The identifier of this {@link DeadlineMessage}.
     * @param type         The {@link MessageType type} for this {@link DeadlineMessage}.
     * @param payload      The payload of type {@code P} for this {@link DeadlineMessage}.
     * @param metaData     The metadata for this {@link DeadlineMessage}.
     * @param timestamp    The {@link Instant timestamp} of this {@link DeadlineMessage DeadlineMessage's} creation.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull String identifier,
                                  @Nonnull MessageType type,
                                  @Nullable P payload,
                                  @Nonnull Map<String, ?> metaData,
                                  @Nonnull Instant timestamp) {
        super(identifier, type, payload, metaData, timestamp);
        this.deadlineName = deadlineName;
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code deadlineName}, {@code delegate} and
     * {@code timestampSupplier}, intended to reconstruct another {@link DeadlineMessage}.
     * <p>
     * The timestamp of the deadline is supplied lazily through the given {@code timestampSupplier} to prevent
     * unnecessary deserialization of the timestamp.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param deadlineName      The name for this {@link DeadlineMessage}.
     * @param delegate          The {@link Message} containing {@link Message#getPayload() payload},
     *                          {@link Message#type() type}, {@link Message#getIdentifier() identifier} and
     *                          {@link Message#getMetaData() metadata} for the {@link DeadlineMessage} to reconstruct.
     * @param timestampSupplier {@link Supplier} for the {@link Instant timestamp} of the
     *                          {@link DeadlineMessage DeadlineMessage's} creation.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull Message<P> delegate,
                                  @Nonnull Supplier<Instant> timestampSupplier) {
        super(delegate, timestampSupplier);
        this.deadlineName = deadlineName;
    }

    @Override
    public String getDeadlineName() {
        return deadlineName;
    }

    @Override
    public GenericDeadlineMessage<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericDeadlineMessage<>(deadlineName, getDelegate().withMetaData(metaData), this::getTimestamp);
    }

    @Override
    public GenericDeadlineMessage<P> andMetaData(@Nonnull Map<String, ?> additionalMetaData) {
        return new GenericDeadlineMessage<>(
                deadlineName, getDelegate().andMetaData(additionalMetaData), this::getTimestamp
        );
    }

    @Override
    protected String describeType() {
        return "GenericDeadlineMessage";
    }
}
