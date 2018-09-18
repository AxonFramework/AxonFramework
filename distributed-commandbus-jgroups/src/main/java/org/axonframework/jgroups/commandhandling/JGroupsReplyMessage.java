/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.jgroups.commandhandling;

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.distributed.ReplyMessage;
import org.axonframework.serialization.Serializer;
import org.jgroups.util.Streamable;

import java.io.*;

/**
 * JGroups Message representing a reply to a dispatched command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JGroupsReplyMessage extends ReplyMessage implements Streamable, Externalizable {

    private static final long serialVersionUID = 6955710928767199410L;
    private static final String NULL = "_null";

    /**
     * Default constructor required by the {@link Streamable} and {@link Externalizable} interfaces. Do not use
     * directly.
     */
    @SuppressWarnings("UnusedDeclaration")
    public JGroupsReplyMessage() {
    }

    /**
     * Initializes a JGroupsReplyMessage containing a reply to the command with given {commandIdentifier} and given
     * {@code commandResultMessage}. The parameter {@code success} determines whether the was executed successfully or
     * not.
     *
     * @param commandIdentifier    The identifier of the command to which the message is a reply
     * @param success              Whether or not the command executed successfully or not
     * @param commandResultMessage The return value of command process
     *                             the given {@code commandResultMessage} is ignored.
     * @param serializer           The serializer to serialize the message contents with
     */
    public JGroupsReplyMessage(String commandIdentifier, boolean success,
                               CommandResultMessage<?> commandResultMessage, Serializer serializer) {
        super(commandIdentifier, success, commandResultMessage, serializer);
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeUTF(commandIdentifier);
        out.writeBoolean(success);
        out.writeInt(serializedMetaData.length);
        out.write(serializedMetaData);
        if (payloadType == null) {
            out.writeUTF(NULL);
        } else {
            out.writeUTF(payloadType);
            out.writeUTF(payloadRevision == null ? NULL : payloadRevision);
            out.writeInt(serializedPayload.length);
            out.write(serializedPayload);
        }
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        commandIdentifier = in.readUTF();
        success = in.readBoolean();
        serializedMetaData = new byte[in.readInt()];
        in.readFully(serializedMetaData);
        payloadType = in.readUTF();
        if (NULL.equals(payloadType)) {
            payloadType = null;
        } else {
            payloadRevision = in.readUTF();
            if (NULL.equals(payloadRevision)) {
                payloadRevision = null;
            }
            serializedPayload = new byte[in.readInt()];
            in.readFully(serializedPayload);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        writeTo(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        readFrom(in);
    }

}
