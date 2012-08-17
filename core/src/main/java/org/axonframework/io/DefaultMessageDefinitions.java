/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.io;

import java.util.Arrays;

import static org.axonframework.io.DefaultMessageSegments.*;

/**
 * Enumeration of supported Message Types by the {@link org.axonframework.eventhandling.io.EventMessageWriter} and
 * {@link org.axonframework.eventhandling.io.EventMessageReader}.
 * <p/>
 *
 * @author Allard Buijze
 * @since 2.0
 */
public enum DefaultMessageDefinitions implements MessageDefinition {

    /**
     * Represents a DomainEventMessage
     * <p/>
     * Binary representation: <code>0000 0011</code>
     */
    DOMAIN_EVENT_MESSAGE((byte) 3, UTF, UTF, UTF, LONG, UTF, UTF, BINARY, BINARY),

    /**
     * Represents an EventMessage which is not a DomainEventMessage
     * <p/>
     * Binary representation: <code>0000 0001</code>
     */
    EVENT_MESSAGE((byte) 1, UTF, UTF, UTF, UTF, BINARY, BINARY),

    /**
     * Represents a Command Message
     * <p/>
     * Binary representation: <code>0001 0000</code>
     */
    COMMAND_MESSAGE((byte) (16), UTF, BINARY, BINARY),

    /**
     * Indicates the next item is a reply to a Command containing a result.
     * <p/>
     * Binary representation: <code>0011 0000</code>
     */
    REPLY_OK((byte) 48, UTF, BINARY),

    /**
     * Indicates the next item is a reply to a Command containing an error message.
     * <p/>
     * Binary representation: <code>0111 0000</code>
     */
    REPLY_ERROR((byte) 112, UTF, BINARY);

    private final byte typeByte;
    private final DefaultMessageSegments[] segments;

    /**
     * Returns the MessageDefinition identified by the given <code>typeByte</code>.
     *
     * @param typeByte The byte representing the MessageDefinitions
     * @return the MessageDefinitions represented by the typeByte, or <code>null</code> if unknown
     */
    public static MessageDefinition fromTypeByte(byte typeByte) {
        for (DefaultMessageDefinitions type : DefaultMessageDefinitions.values()) {
            if (type.typeByte == typeByte) {
                return type;
            }
        }
        return null;
    }

    private DefaultMessageDefinitions(byte typeByte, DefaultMessageSegments... segments) {
        this.typeByte = typeByte;
        this.segments = segments;
    }

    @Override
    public byte getTypeByte() {
        return typeByte;
    }

    @Override
    public MessageSegment[] getSegments() {
        return Arrays.copyOf(segments, segments.length);
    }
}
