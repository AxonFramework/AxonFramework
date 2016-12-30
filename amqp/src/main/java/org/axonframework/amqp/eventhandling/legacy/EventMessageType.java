/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.amqp.eventhandling.legacy;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;

/**
 * Enumeration of supported Message Types by the {@link EventMessageWriter} and {@link EventMessageReader}.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public enum EventMessageType {

    /**
     * Represents a DomainEventMessage
     */
    DOMAIN_EVENT_MESSAGE((byte) 3, DomainEventMessage.class),

    /**
     * Represents an EventMessage which is not a DomainEventMessage
     */
    EVENT_MESSAGE((byte) 1, EventMessage.class);

    private final byte typeByte;
    private final Class<? extends EventMessage> messageClass;

    /**
     * Returns the most specific EventMessageType for the given {@code message}.
     *
     * @param message The message to resolve the type for
     * @return The EventMessageType for the given {@code message}
     */
    public static EventMessageType forMessage(EventMessage message) {
        for (EventMessageType type : EventMessageType.values()) {
            if (type.messageClass.isInstance(message)) {
                return type;
            }
        }
        return EVENT_MESSAGE;
    }

    /**
     * Returns the EventMessageType identified by the given {@code typeByte}.
     *
     * @param typeByte The byte representing the EventMessageType
     * @return the EventMessageType represented by the typeByte, or {@code null} if unknown
     */
    public static EventMessageType fromTypeByte(byte typeByte) {
        for (EventMessageType type : EventMessageType.values()) {
            if (type.typeByte == typeByte) {
                return type;
            }
        }
        return null;
    }

    EventMessageType(byte typeByte, Class<? extends EventMessage> messageClass) {
        this.typeByte = typeByte;
        this.messageClass = messageClass;
    }

    /**
     * Returns the Type Byte for this EventMessageType.
     *
     * @return the byte representing this EventMessageType
     */
    public byte getTypeByte() {
        return typeByte;
    }
}
