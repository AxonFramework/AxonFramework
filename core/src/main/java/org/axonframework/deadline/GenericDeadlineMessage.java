/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generic implementation of {@link DeadlineMessage}.
 *
 * @param <T> The type of payload contained in this Message
 * @author Milan Savic
 * @since 3.3
 */
public class GenericDeadlineMessage<T> extends GenericEventMessage<T> implements DeadlineMessage<T> {

    private static final long serialVersionUID = 2615162095663478618L;

    /**
     * Returns the given deadline as DeadlineMessage. If {@code deadline} already implements DeadlineMessage, it is
     * returned as is. If it is a Message, a new DeadlineMessage will be created using the payload and meta data of the
     * given deadline. Otherwise, the given {@code deadline} is wrapped into a GenericDeadlineMessage as its payload.
     *
     * @param deadline The deadline to wrap as DeadlineMessage
     * @param <T>      The generic type of the expected payload of the resulting object
     * @return a DeadlineMessage containing given {@code deadline} as payload, or {@code deadline} if it already
     * implements DeadlineMessage
     */
    @SuppressWarnings("unchecked")
    public static <T> DeadlineMessage<T> asDeadlineMessage(Object deadline) {
        if (DeadlineMessage.class.isInstance(deadline)) {
            return (DeadlineMessage<T>) deadline;
        } else if (deadline instanceof Message) {
            Message message = (Message) deadline;
            return new GenericDeadlineMessage<>(message, () -> clock.instant());
        }
        return new GenericDeadlineMessage<>(new GenericMessage<>((T) deadline), () -> clock.instant());
    }

    /**
     * Creates a GenericDeadlineMessage with given {@code payload}, and an empty MetaData.
     *
     * @param payload The payload of the Message
     */
    public GenericDeadlineMessage(T payload) {
        super(payload);
    }

    /**
     * Creates a GenericDeadlineMessage with given {@code payload} and given {@code metaData}.
     *
     * @param payload  The payload of the Message
     * @param metaData The MetaData of the Message
     */
    public GenericDeadlineMessage(T payload, Map<String, ?> metaData) {
        super(payload, metaData);
    }

    /**
     * Constructor to reconstructs a DeadlineMessage using existing data.
     *
     * @param identifier The identifier of the Message
     * @param payload    The payload of the Message
     * @param metaData   The MetaData of the Message
     * @param timestamp  The timestamp of the Message creation
     */
    public GenericDeadlineMessage(String identifier, T payload, Map<String, ?> metaData, Instant timestamp) {
        super(identifier, payload, metaData, timestamp);
    }

    /**
     * Constructor to reconstruct a DeadlineMessage using existing data. The timestamp of the event is supplied lazily
     * to prevent unnecessary deserialization of the timestamp.
     *
     * @param delegate          The message containing payload, identifier and MetaData
     * @param timestampSupplier Supplier for the timestamp of the Message creation
     */
    public GenericDeadlineMessage(Message<T> delegate,
                                  Supplier<Instant> timestampSupplier) {
        super(delegate, timestampSupplier);
    }

    @Override
    public GenericDeadlineMessage<T> withMetaData(Map<String, ?> metaData) {
        return null;
    }

    @Override
    public GenericDeadlineMessage<T> andMetaData(Map<String, ?> additionalMetaData) {
        return null;
    }

    @Override
    protected String describeType() {
        return "GenericDeadlineMessage";
    }
}
