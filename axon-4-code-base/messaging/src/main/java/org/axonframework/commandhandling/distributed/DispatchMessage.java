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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for dispatch messages which may be used in the {@link CommandBusConnector} upon dispatching a command
 * to other nodes.
 */
public abstract class DispatchMessage {

    protected String commandIdentifier;
    protected byte[] serializedMetaData;
    protected String payloadType;
    protected String payloadRevision;
    protected byte[] serializedPayload;
    protected String commandName;
    protected boolean expectReply;

    /**
     * Default constructor required for de-/serialization of extending classes. Do not use directly.
     */
    protected DispatchMessage() {
    }

    /**
     * Initialized a DispatchMessage for the given {@code commandMessage}, which uses the given
     * {@code serializer} to deserialize its contents.
     * {@code expectReply} indicates whether the sender will be expecting a reply.
     *
     * @param commandMessage The message to send to the remote segment
     * @param serializer     The serialize to serialize the message payload and metadata with
     * @param expectReply    whether or not the sender is waiting for a reply.
     */
    protected DispatchMessage(CommandMessage<?> commandMessage, Serializer serializer, boolean expectReply) {
        this.commandIdentifier = commandMessage.getIdentifier();
        SerializedObject<byte[]> metaData = commandMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();
        SerializedObject<byte[]> payload = commandMessage.serializePayload(serializer, byte[].class);
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.serializedPayload = payload.getData();
        this.commandName = commandMessage.getCommandName();
        this.expectReply = expectReply;
    }

    /**
     * Returns the CommandMessage wrapped in this Message.
     *
     * @param serializer The serializer to deserialize message contents with
     * @return the CommandMessage wrapped in this Message
     */
    public CommandMessage<?> getCommandMessage(Serializer serializer) {
        SimpleSerializedObject<byte[]> serializedPayload =
                new SimpleSerializedObject<>(this.serializedPayload, byte[].class, payloadType, payloadRevision);
        final Object payload = serializer.deserialize(serializedPayload);
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(this.serializedMetaData, byte[].class);
        final MetaData metaData = serializer.deserialize(serializedMetaData);
        return new GenericCommandMessage<>(new GenericMessage<>(commandIdentifier, payload, metaData), commandName);
    }

    /**
     * Returns the identifier of the command carried by this instance.
     *
     * @return the identifier of the command carried by this instance as a {@code String}
     */
    public String getCommandIdentifier() {
        return commandIdentifier;
    }

    /**
     * Returns the serialized metadata of the command carried by this instance.
     *
     * @return the serialized metadata of the command carried by this instance as a {@code byte[]}
     */
    public byte[] getSerializedMetaData() {
        return serializedMetaData;
    }

    /**
     * Returns the payload type of the serialized payload of the command carried by this instance.
     *
     * @return the payload type of the serialized payload of the the command carried by this instance as a {@code String}
     */
    public String getPayloadType() {
        return payloadType;
    }

    /**
     * Returns the payload revision of the serialized payload of the command carried by this instance.
     *
     * @return the payload revision of the serialized payload of the command carried by this instance as a {@code String}
     */
    public String getPayloadRevision() {
        return payloadRevision;
    }

    /**
     * Returns the serialized payload of the command carried by this instance.
     *
     * @return the serialized payload of the command carried by this instance as a {@code byte[]}
     */
    public byte[] getSerializedPayload() {
        return serializedPayload;
    }

    /**
     * Returns the command name of the command carried by this instance.
     *
     * @return the command name of the command carried by this instance as a {@code String}
     */
    public String getCommandName() {
        return commandName;
    }

    /**
     * Indicates whether the sender of this message requests a reply.
     *
     * @return {@code true} if a reply is expected, otherwise {@code false}.
     */
    public boolean isExpectReply() {
        return expectReply;
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandIdentifier, serializedMetaData, payloadType, payloadRevision, serializedPayload,
                commandName, expectReply);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DispatchMessage other = (DispatchMessage) obj;
        return Objects.equals(this.commandIdentifier, other.commandIdentifier)
                && Objects.deepEquals(this.serializedMetaData, other.serializedMetaData)
                && Objects.equals(this.payloadType, other.payloadType)
                && Objects.equals(this.payloadRevision, other.payloadRevision)
                && Objects.deepEquals(this.serializedPayload, other.serializedPayload)
                && Objects.equals(this.commandName, other.commandName)
                && Objects.equals(this.expectReply, other.expectReply);
    }

    @Override
    public String toString() {
        return "DispatchMessage{" +
                "commandIdentifier='" + commandIdentifier + '\'' +
                ", serializedMetaData=" + Arrays.toString(serializedMetaData) +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", serializedPayload=" + Arrays.toString(serializedPayload) +
                ", commandName='" + commandName + '\'' +
                ", expectReply=" + expectReply +
                '}';
    }

}
