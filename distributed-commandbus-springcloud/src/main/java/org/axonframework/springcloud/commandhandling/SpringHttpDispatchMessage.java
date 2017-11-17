/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.DispatchMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;

/**
 * Spring Http message that contains a CommandMessage that needs to be dispatched on a remote command bus segment.
 */
public class SpringHttpDispatchMessage<C> extends DispatchMessage implements Serializable {

    /**
     * Initialize a SpringHttpDispatchMessage for the given {@code commandMessage}, to be serialized using given
     * {@code serializer}. {@code expectReply} indicates whether the sender will be expecting a reply.
     *
     * @param commandMessage The message to send to the remote segment
     * @param serializer     The serialize to serialize the message payload and metadata with
     * @param expectReply    whether or not the sender is waiting for a reply.
     */
    public SpringHttpDispatchMessage(CommandMessage<?> commandMessage, Serializer serializer, boolean expectReply) {
        super(commandMessage, serializer, expectReply);
    }

    @SuppressWarnings("unused")
    private SpringHttpDispatchMessage() {
        // Used for de-/serialization
    }

    @Override
    public CommandMessage<C> getCommandMessage(Serializer serializer) {
        SimpleSerializedObject<byte[]> serializedPayload =
                new SimpleSerializedObject<>(this.serializedPayload, byte[].class, payloadType, payloadRevision);
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(this.serializedMetaData, byte[].class);
        final MetaData metaData = serializer.deserialize(serializedMetaData);
        GenericMessage<C> genericMessage =
                new GenericMessage<>(commandIdentifier, serializer.deserialize(serializedPayload), metaData);
        return new GenericCommandMessage<>(genericMessage, commandName);
    }
}
