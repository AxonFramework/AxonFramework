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

import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.AxonException;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.RemoteExceptionDescription;
import org.axonframework.messaging.RemoteHandlingException;
import org.axonframework.messaging.RemoteNonTransientHandlingException;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for reply messages which may be used in the {@link CommandBusConnector} for replying on received
 * commands from other nodes.
 */
public abstract class ReplyMessage implements Serializable {

    protected String commandIdentifier;
    protected byte[] serializedMetaData;
    protected String payloadType;
    protected String payloadRevision;
    protected byte[] serializedPayload;
    protected String exceptionType;
    protected String exceptionRevision;
    protected byte[] serializedException;

    /**
     * Default constructor required for de-/serialization of extending classes. Do not use directly.
     */
    protected ReplyMessage() {
    }

    /**
     * Initializes a ReplyMessage containing a reply to the command with given {commandIdentifier} and given
     * {@code commandResultMessage}.
     *
     * @param commandIdentifier    the identifier of the command to which the message is a reply
     * @param commandResultMessage the result message of command process
     * @param serializer           the serializer to serialize the message contents with
     */
    public ReplyMessage(String commandIdentifier, CommandResultMessage<?> commandResultMessage, Serializer serializer) {
        this.commandIdentifier = commandIdentifier;

        SerializedObject<byte[]> metaData = commandResultMessage.serializeMetaData(serializer, byte[].class);
        this.serializedMetaData = metaData.getData();

        SerializedObject<byte[]> payload = commandResultMessage.serializePayload(serializer, byte[].class);
        this.serializedPayload = payload.getData();
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();

        SerializedObject<byte[]> exception = commandResultMessage.serializeExceptionResult(serializer, byte[].class);
        this.serializedException = exception.getData();
        this.exceptionType = exception.getType().getName();
        this.exceptionRevision = exception.getType().getRevision();
    }

    /**
     * Returns a {@link CommandResultMessage} containing the result of command processing.
     *
     * @param serializer the serializer to deserialize the result with
     * @return a {@link CommandResultMessage} containing the return value of command processing
     */
    public CommandResultMessage<?> getCommandResultMessage(Serializer serializer) {
        Object payload = deserializePayload(serializer);
        RemoteExceptionDescription exceptionDescription = deserializeException(serializer);
        SerializedMetaData<byte[]> serializedMetaData =
                new SerializedMetaData<>(this.serializedMetaData, byte[].class);
        MetaData metaData = serializer.deserialize(serializedMetaData);

        if (exceptionDescription != null) {
            return new GenericCommandResultMessage<>(new CommandExecutionException("The remote handler threw an exception",
                                                                                   convertToRemoteException(exceptionDescription),
                                                                                   payload),
                                                     metaData);
        }
        return new GenericCommandResultMessage<>(payload, metaData);
    }

    private AxonException convertToRemoteException(RemoteExceptionDescription exceptionDescription) {
        return exceptionDescription.isPersistent() ?
                new RemoteNonTransientHandlingException(exceptionDescription) :
                new RemoteHandlingException(exceptionDescription);
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
     * Returns the exception type of the serialized reply message.
     *
     * @return the exception type of the serialized reply message as a {@code String}
     */
    public String getExceptionType() {
        return exceptionType;
    }

    /**
     * Returns the exception revision of the serialized reply message.
     *
     * @return the exception revision of the serialized reply message as a {@code String}
     */
    public String getExceptionRevision() {
        return exceptionRevision;
    }

    /**
     * Returns the serialized exception of the serialized reply message.
     *
     * @return the serialized exception of the serialized reply message as {@code byte[]}
     */
    public byte[] getSerializedException() {
        return serializedException;
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
                            payloadType,
                            payloadRevision,
                            serializedPayload,
                            exceptionType,
                            exceptionRevision,
                            serializedException,
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
                && Objects.equals(this.payloadType, other.payloadType)
                && Objects.equals(this.payloadRevision, other.payloadRevision)
                && Objects.deepEquals(this.serializedPayload, other.serializedPayload)
                && Objects.deepEquals(this.exceptionType, other.exceptionType)
                && Objects.deepEquals(this.exceptionRevision, other.exceptionRevision)
                && Objects.deepEquals(this.serializedException, other.serializedException)
                && Objects.deepEquals(this.serializedMetaData, other.serializedMetaData);
    }

    @Override
    public String toString() {
        return "ReplyMessage{" +
                "commandIdentifier='" + commandIdentifier + '\'' +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", serializedPayload=" + Arrays.toString(serializedPayload) +
                ", exceptionType='" + exceptionType + '\'' +
                ", exceptionRevision='" + exceptionRevision + '\'' +
                ", serializedException='" + Arrays.toString(serializedMetaData) +
                ", serializedMetaData=" + Arrays.toString(serializedMetaData) +
                '}';
    }

    private Object deserializePayload(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(serializedPayload,
                                                                   byte[].class,
                                                                   payloadType,
                                                                   payloadRevision));
    }

    private RemoteExceptionDescription deserializeException(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(serializedException,
                                                                   byte[].class,
                                                                   exceptionType,
                                                                   exceptionRevision));
    }

}
