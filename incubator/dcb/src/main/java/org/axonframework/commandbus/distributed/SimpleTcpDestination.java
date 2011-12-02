/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandbus.distributed;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * @author Allard Buijze
 */
public class SimpleTcpDestination implements Destination {

    private final Socket socket;
    private final Serializer serializer;

    public SimpleTcpDestination(Socket socket, Serializer serializer) {
        this.socket = socket;
        this.serializer = serializer;
    }

    @Override
    public void send(Object command) {
        try {
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
            dataOut.writeUTF("SEND");
            SerializedObject serialized = serialize(command);
            dataOut.writeUTF(serialized.getType().getName());
            dataOut.writeInt(serialized.getType().getRevision());
            dataOut.writeInt(serialized.getData().length);
            dataOut.write(serialized.getData());
        } catch (IOException e) {
            // TODO: Implement
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <R> void send(Object command, CommandCallback<R> callback) {
        DataOutput dataOut = null;
        try {
            dataOut = new DataOutputStream(socket.getOutputStream());
            dataOut.writeUTF("SEND-RECEIVE");
            SerializedObject serialized = serialize(command);
            dataOut.writeUTF(serialized.getType().getName());
            dataOut.writeInt(serialized.getType().getRevision());
            dataOut.writeInt(serialized.getData().length);
            dataOut.write(serialized.getData());
            DataInput dataIn = new DataInputStream(socket.getInputStream());
            String type = dataIn.readUTF();
            int revision = dataIn.readInt();
            int length = dataIn.readInt();
            byte[] serializedResponse = new byte[length];
            dataIn.readFully(serializedResponse);
            Object response = serializer.deserialize(new SimpleSerializedObject(serializedResponse, type, revision));
            if (response instanceof Throwable) {
                callback.onFailure((Throwable) response);
            } else {
                callback.onSuccess((R) response);
            }
        } catch (IOException e) {
            // TODO: Implement
        }
    }

    private SerializedObject serialize(Object command) {
        return serializer.serialize(command);
    }

    @Override
    public boolean isAvailable() {
        return socket.isConnected();
    }
}
