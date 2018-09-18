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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for reply messages which may be used in the {@link CommandBusConnector} for replying on received
 * commands from other nodes.
 */
public abstract class ReplyMessage {

    protected String commandIdentifier;
    protected boolean success;
    protected byte[] serializedMetaData;
    protected String payloadType;
    protected String payloadRevision;
    protected byte[] serializedPayload;

    /**
     * Default constructor required for de-/serialization of extending classes. Do not use directly.
     */
    protected ReplyMessage() {
    }

    /**
     * Initializes a ReplyMessage containing a reply to the command with given {commandIdentifier} and given
     * {@code commandResultMessage}. The parameter {@code success} determines whether the was executed successfully or
     * not.
     *
     * @param commandIdentifier    The identifier of the command to which the message is a reply
     * @param success              Whether or not the command executed successfully or not
     * @param commandResultMessage The result message of command process
     * @param serializer           The serializer to serialize the message contents with
     */
    public ReplyMessage(String commandIdentifier, boolean success, CommandResultMessage<?> commandResultMessage,
                        Serializer serializer) {
        this.success = success;
        this.commandIdentifier = commandIdentifier;
        SerializedObject<byte[]> metaData = commandResultMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();
        SerializedObject<byte[]> payload = commandResultMessage.serializePayload(serializer, byte[].class);
        this.serializedPayload = payload.getData();
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
    }

    /**
     * Returns the returnValue of the command processing. If {@link #isSuccess()} return {@code false}, this
     * method returns {@code null}. This method also returns {@code null} if response processing returned
     * a {@code null} value.
     *
     * @param serializer The serializer to deserialize the result with
     * @return The return value of command processing
     */
    public CommandResultMessage<?> getCommandResultMessage(Serializer serializer) {
        if (!success || payloadType == null) {
            return null;
        }
        Object payload = deserializePayload(serializer);
        SerializedMetaData<byte[]> serializedMetaData =
                new SerializedMetaData<>(this.serializedMetaData, byte[].class);
        MetaData metaData = serializer.deserialize(serializedMetaData);
        return new GenericCommandResultMessage<>(payload, metaData);
    }

    /**
     * Returns the error of the command processing. If {@link #isSuccess()} return {@code true}, this
     * method returns {@code null}.
     *
     * @param serializer The serializer to deserialize the result with
     * @return The exception thrown during command processing
     */
    public Throwable getError(Serializer serializer) {
        if (success || payloadType == null) {
            return null;
        }
        return (Throwable) deserializePayload(serializer);
    }

    /**
     * Returns the identifier of the command for which this message is a reply.
     *
     * @return the identifier of the command for which this message is a reply as a {@code String}
     */
    public String getCommandIdentifier() {
        return commandIdentifier;
    }

    /**
     * Whether the reply message represents a successfully executed command. In this case, successful means that the
     * command's execution did not result in an exception.
     *
     * @return {@code true} if this reply contains a return value, {@code false} if it contains an error.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the payload type of the serialized reply message.
     *
     * @return the payload type of the serialized reply message as a {@code String}
     */
    public String getPayloadType() {
        return payloadType;
    }

    /**
     * Returns the payload revision of the serialized reply message.
     *
     * @return the payload revision of the serialized reply message as a {@code String}
     */
    public String getPayloadRevision() {
        return payloadRevision;
    }

    /**
     * Returns the serialized payload of the serialized reply message.
     *
     * @return the serialized payload of the serialized reply message as a {@code byte[]}
     */
    public byte[] getSerializedPayload() {
        return serializedPayload;
    }

    /**
     * Returns the serialized meta data of the serialized reply message.
     *
     * @return the serialized meta data of the serialized reply message as a {@code byte[]}
     */
    public byte[] getSerializedMetaData() {
        return serializedMetaData;
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandIdentifier,
                            success,
                            payloadType,
                            payloadRevision,
                            serializedPayload,
                            serializedMetaData);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ReplyMessage other = (ReplyMessage) obj;
        return Objects.equals(this.commandIdentifier, other.commandIdentifier)
                && Objects.equals(this.success, other.success)
                && Objects.equals(this.payloadType, other.payloadType)
                && Objects.equals(this.payloadRevision, other.payloadRevision)
                && Objects.deepEquals(this.serializedPayload, other.serializedPayload)
                && Objects.deepEquals(this.serializedMetaData, other.serializedMetaData);
    }

    @Override
    public String toString() {
        return "ReplyMessage{" +
                "commandIdentifier='" + commandIdentifier + '\'' +
                ", success=" + success +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", serializedPayload=" + Arrays.toString(serializedPayload) +
                ", serializedMetaData=" + Arrays.toString(serializedMetaData) +
                '}';
    }

    private Object deserializePayload(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(serializedPayload, byte[].class,
                                                                   payloadType, payloadRevision));
    }

}
