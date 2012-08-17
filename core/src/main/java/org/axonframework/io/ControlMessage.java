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

/**
 * @author Allard Buijze
 */
public enum ControlMessage implements MessageDefinition {

    /**
     * Indicates the next item indicates the start of a batch.
     * <p/>
     * Binary representation: <code>1000 0001</code>
     */
    BATCH_START((byte) -127, DefaultMessageSegments.SHORT),

    /**
     * Indicates the next item indicates the end of a batch.
     * <p/>
     * Binary representation: <code>1000 1000</code>
     */
    BATCH_END((byte) -120),

    /**
     * Indicates the next item indicates the acknowledgement of a batch.
     * <p/>
     * Binary representation: <code>1000 1111</code>
     */
    BATCH_ACK((byte) -113);

    private final byte typeByte;
    private final MessageSegment[] messageSegments;

    ControlMessage(byte typeByte, MessageSegment... messageSegments) {
        this.typeByte = typeByte;
        this.messageSegments = messageSegments;
    }


    @Override
    public byte getTypeByte() {
        return typeByte;
    }

    @Override
    public MessageSegment[] getSegments() {
        return Arrays.copyOf(messageSegments, messageSegments.length);
    }

    public static boolean isControlMessage(MessageDefinition messageType) {
        return messageType.getTypeByte() < 0;
    }

    public static boolean isControlMessage(byte b) {
        return b < 0;
    }
}
