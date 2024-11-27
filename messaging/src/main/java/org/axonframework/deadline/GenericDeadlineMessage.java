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

package org.axonframework.deadline;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;

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
     * Returns the given {@code deadlineName} and {@code messageOrPayload} as a DeadlineMessage which expires at the
     * given {@code expiryTime}. If the {@code messageOrPayload} parameter is of type {@link Message}, a new
     * {@code DeadlineMessage} instance will be created using the payload and meta data of the given message. Otherwise,
     * the given {@code messageOrPayload} is wrapped into a {@code GenericDeadlineMessage} as its payload.
     *
     * @param deadlineName     The name for this {@link DeadlineMessage}.
     * @param messageOrPayload A {@link Message} or payload to wrap as a DeadlineMessage
     * @param expiryTime       The timestamp at which the deadline expires
     * @param <P>              The generic type of the expected payload of the resulting object
     * @return a DeadlineMessage using the {@code deadlineName} as its deadline name and containing the given
     * {@code messageOrPayload} as the payload
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName type}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <P> DeadlineMessage<P> asDeadlineMessage(@Nonnull String deadlineName,
                                                           @Nullable Object messageOrPayload,
                                                           @Nonnull Instant expiryTime) {
        if (messageOrPayload instanceof Message) {
            return new GenericDeadlineMessage<>(deadlineName,
                                                (Message<P>) messageOrPayload,
                                                () -> expiryTime);
        }
        QualifiedName type = messageOrPayload == null
                ? QualifiedName.dottedName("empty.deadline.payload")
                : QualifiedNameUtils.fromClassName(messageOrPayload.getClass());
        return new GenericDeadlineMessage<>(
                deadlineName, new GenericMessage<>(type, (P) messageOrPayload), () -> expiryTime
        );
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code type} and {@code deadlineName}.
     * <p>
     * The {@link #getPayload()} defaults to {@code null} and the {@link MetaData} defaults to an empty instance.
     *
     * @param type         The {@link QualifiedName type} for this {@link DeadlineMessage}.
     * @param deadlineName The name for this {@link DeadlineMessage}.
     */
    public GenericDeadlineMessage(@Nonnull QualifiedName type,
                                  @Nonnull String deadlineName) {
        this(deadlineName, type, null);
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code deadlineName}, {@code type}, and
     * {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param deadlineName The name for this {@link DeadlineMessage}.
     * @param type         The {@link QualifiedName type} for this {@link DeadlineMessage}.
     * @param payload      The payload of type {@code P} for this {@link DeadlineMessage}.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull QualifiedName type,
                                  @Nullable P payload) {
        this(deadlineName, type, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericDeadlineMessage} for the given {@code deadlineName}, {@code type}, {@code payload},
     * and {@code metaData}.
     *
     * @param deadlineName The name for this {@link DeadlineMessage}.
     * @param type         The {@link QualifiedName type} for this {@link DeadlineMessage}.
     * @param payload      The payload of type {@code P} for this {@link DeadlineMessage}.
     * @param metaData     The metadata for this {@link DeadlineMessage}.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull QualifiedName type,
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
     * @param type         The {@link QualifiedName type} for this {@link DeadlineMessage}.
     * @param payload      The payload of type {@code P} for this {@link DeadlineMessage}.
     * @param metaData     The metadata for this {@link DeadlineMessage}.
     * @param timestamp    The {@link Instant timestamp} of this {@link DeadlineMessage DeadlineMessage's} creation.
     */
    public GenericDeadlineMessage(@Nonnull String deadlineName,
                                  @Nonnull String identifier,
                                  @Nonnull QualifiedName type,
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
