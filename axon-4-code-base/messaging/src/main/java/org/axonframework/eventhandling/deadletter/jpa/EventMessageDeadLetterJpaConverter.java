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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Converter responsible for converting to and from known {@link EventMessage} implementations that should be supported
 * by a {@link org.axonframework.messaging.deadletter.SequencedDeadLetterQueue} capable of storing
 * {@code EventMessages}.
 * <p>
 * It rebuilds the original message implementation by checking which properties were extracted when it was originally
 * mapped. For example, if the aggregate type and token are present, it will construct a
 * {@link GenericTrackedDomainEventMessage}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class EventMessageDeadLetterJpaConverter implements DeadLetterJpaConverter<EventMessage<?>> {

    @SuppressWarnings("rawtypes")
    @Override
    public DeadLetterEventEntry convert(EventMessage<?> message, Serializer eventSerializer, Serializer genericSerializer) {
        GenericEventMessage<?> eventMessage = (GenericEventMessage<?>) message;
        Optional<TrackedEventMessage> trackedEventMessage = Optional.of(eventMessage).filter(
                TrackedEventMessage.class::isInstance).map(TrackedEventMessage.class::cast);
        Optional<DomainEventMessage> domainEventMessage = Optional.of(eventMessage).filter(
                DomainEventMessage.class::isInstance).map(DomainEventMessage.class::cast);

        SerializedObject<byte[]> serializedPayload = eventSerializer.serialize(message.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetadata = eventSerializer.serialize(message.getMetaData(), byte[].class);
        Optional<SerializedObject<byte[]>> serializedToken = trackedEventMessage.map(m -> genericSerializer.serialize(m.trackingToken(),
                                                                                                               byte[].class));


        return new DeadLetterEventEntry(
                message.getClass().getName(),
                message.getIdentifier(),
                message.getTimestamp().toString(),
                serializedPayload.getType().getName(),
                serializedPayload.getType().getRevision(),
                serializedPayload.getData(),
                serializedMetadata.getData(),
                domainEventMessage.map(DomainEventMessage::getType).orElse(null),
                domainEventMessage.map(DomainEventMessage::getAggregateIdentifier).orElse(null),
                domainEventMessage.map(DomainEventMessage::getSequenceNumber).orElse(null),
                serializedToken.map(SerializedObject::getType).map(SerializedType::getName).orElse(null),
                serializedToken.map(SerializedObject::getData).orElse(null)
        );
    }

    @Override
    public EventMessage<?> convert(DeadLetterEventEntry entry, Serializer eventSerializer, Serializer genericSerializer) {
        SerializedMessage<?> serializedMessage = new SerializedMessage<>(entry.getEventIdentifier(),
                                                                         entry.getPayload(),
                                                                         entry.getMetaData(),
                                                                         eventSerializer);
        Supplier<Instant> timestampSupplier = () -> Instant.parse(entry.getTimeStamp());
        if (entry.getTrackingToken() != null) {
            TrackingToken trackingToken = genericSerializer.deserialize(entry.getTrackingToken());
            if (entry.getAggregateIdentifier() != null) {
                return new GenericTrackedDomainEventMessage<>(trackingToken,
                                                              entry.getType(),
                                                              entry.getAggregateIdentifier(),
                                                              entry.getSequenceNumber(),
                                                              serializedMessage,
                                                              timestampSupplier);
            } else {
                return new GenericTrackedEventMessage<>(trackingToken,
                                                        serializedMessage,
                                                        timestampSupplier);
            }
        }
        if (entry.getAggregateIdentifier() != null) {
            return new GenericDomainEventMessage<>(entry.getType(),
                                                   entry.getAggregateIdentifier(),
                                                   entry.getSequenceNumber(),
                                                   serializedMessage.getPayload(),
                                                   serializedMessage.getMetaData(),
                                                   serializedMessage.getIdentifier(),
                                                   timestampSupplier.get());
        } else {
            return new GenericEventMessage<>(serializedMessage,
                                             timestampSupplier);
        }
    }

    @Override
    public boolean canConvert(DeadLetterEventEntry message) {
        return message.getMessageType().equals(GenericTrackedDomainEventMessage.class.getName()) ||
                message.getMessageType().equals(GenericEventMessage.class.getName()) ||
                message.getMessageType().equals(GenericDomainEventMessage.class.getName()) ||
                message.getMessageType().equals(GenericTrackedEventMessage.class.getName());
    }

    @Override
    public boolean canConvert(EventMessage<?> message) {
        return message instanceof GenericEventMessage;
    }
}
