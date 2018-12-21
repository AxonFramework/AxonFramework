/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of the {@link DeadlineMessage}.
 *
 * @param <T> The type of payload contained in this Message
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public class GenericDeadlineMessage<T> extends GenericEventMessage<T> implements DeadlineMessage<T> {

    private static final long serialVersionUID = 2615162095663478618L;

    private final String deadlineName;

    /**
     * Instantiate a GenericDeadlineMessage with the given {@code deadlineName}, a {@code null} payload and en empty
     * {@link MetaData}.
     *
     * @param deadlineName A {@link String} denoting the deadline's name
     */
    public GenericDeadlineMessage(String deadlineName) {
        this(deadlineName, null);
    }

    /**
     * Instantiate a GenericDeadlineMessage with the given {@code deadlineName}, a {@code payload} of type {@code T}
     * and en empty {@link MetaData}.
     *
     * @param deadlineName A {@link String} denoting the deadline's name
     * @param payload      The payload of type {@code T} for the DeadlineMessage
     */
    public GenericDeadlineMessage(String deadlineName, T payload) {
        this(deadlineName, payload, MetaData.emptyInstance());
    }

    /**
     * Instantiate a GenericDeadlineMessage with the given {@code deadlineName}, a {@code payload} of type {@code T}
     * and the given {@code metaData}.
     *
     * @param deadlineName A {@link String} denoting the deadline's name
     * @param payload      The payload of the Message
     * @param metaData     The MetaData of the Message
     */
    public GenericDeadlineMessage(String deadlineName, T payload, Map<String, ?> metaData) {
        super(payload, metaData);
        this.deadlineName = deadlineName;
    }

    /**
     * Constructor to reconstructs a DeadlineMessage using existing data.
     *
     * @param deadlineName A {@link String} denoting the deadline's name
     * @param identifier   The identifier of type {@link String} for the Message
     * @param payload      The payload of type {@code T} for the Message
     * @param metaData     The {@link MetaData} of the Message
     * @param timestamp    An {@link Instant} timestamp of the Message creation
     */
    public GenericDeadlineMessage(String deadlineName,
                                  String identifier,
                                  T payload,
                                  Map<String, ?> metaData,
                                  Instant timestamp) {
        super(identifier, payload, metaData, timestamp);
        this.deadlineName = deadlineName;
    }

    /**
     * Constructor to reconstruct a DeadlineMessage using existing data. The timestamp of the event is supplied lazily
     * to prevent unnecessary deserialization of the timestamp.
     *
     * @param deadlineName      A {@link String} denoting the deadline's name
     * @param delegate          The {@link Message} containing the payload, identifier and {@link MetaData}
     * @param timestampSupplier {@link Supplier} for the timestamp of the Message creation
     */
    public GenericDeadlineMessage(String deadlineName, Message<T> delegate, Supplier<Instant> timestampSupplier) {
        super(delegate, timestampSupplier);
        this.deadlineName = deadlineName;
    }

    /**
     * Returns the given {@code deadlineName} and {@code messageOrPayload} as a DeadlineMessage. If the
     * {@code messageOrPayload} parameter is of type {@link Message}, a new DeadlineMessage will be created using the
     * payload and meta data of the given deadline.
     * Otherwise, the given {@code messageOrPayload} is wrapped into a GenericDeadlineMessage as its payload.
     *
     * @param deadlineName     A {@link String} denoting the deadline's name
     * @param messageOrPayload A {@link Message} or payload to wrap as a DeadlineMessage
     * @param <T>              The generic type of the expected payload of the resulting object
     * @return a DeadlineMessage using the {@code deadlineName} as its deadline name and containing the given
     * {@code messageOrPayload} as the payload
     */
    @SuppressWarnings("unchecked")
    public static <T> DeadlineMessage<T> asDeadlineMessage(String deadlineName, Object messageOrPayload) {
        Instant now = clock.instant();
        return messageOrPayload instanceof Message
                ? new GenericDeadlineMessage<>(deadlineName, (Message) messageOrPayload, () -> now)
                : new GenericDeadlineMessage<>(deadlineName,
                                               new GenericMessage<>((T) messageOrPayload),
                                               () -> now);
    }

    @Override
    public String getDeadlineName() {
        return deadlineName;
    }

    @Override
    public GenericDeadlineMessage<T> withMetaData(Map<String, ?> metaData) {
        return new GenericDeadlineMessage<>(deadlineName, getDelegate().withMetaData(metaData), this::getTimestamp);
    }

    @Override
    public GenericDeadlineMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        return new GenericDeadlineMessage<>(
                deadlineName, getDelegate().andMetaData(additionalMetaData), this::getTimestamp
        );
    }

    @Override
    protected String describeType() {
        return "GenericDeadlineMessage";
    }
}
