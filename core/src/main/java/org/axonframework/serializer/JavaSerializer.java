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

package org.axonframework.serializer;

import org.axonframework.saga.Saga;
import org.axonframework.util.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * Serializer implementation that uses Java serialization to serialize and deserialize object instances. This
 * implementation is very suitable if the life span of the serialized objects allows classes to remain unchanged. If
 * Class definitions need to be changed during the object's life cycle, another implementation, like the
 * {@link XStreamSerializer} might be a more suitable alternative.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JavaSerializer implements Serializer<Object> {

    @SuppressWarnings({"NonSerializableObjectPassedToObjectStream"})
    @Override
    public void serialize(Object object, OutputStream outputStream) throws IOException {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(outputStream);
            try {
                oos.writeObject(object);
            } finally {
                oos.flush();
            }
        } catch (IOException e) {
            throw new SerializationException(
                    "An exception occurred while trying to serialize a Saga or Association Value for storage",
                    e);
        }
    }

    @Override
    public byte[] serialize(Object instance) {
        return serializeObject(instance);
    }

    @Override
    public Object deserialize(InputStream inputStream) throws IOException {
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(inputStream);
            return ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new SerializationException("An exception occurred while trying to deserialize a stored Saga", e);
        }
    }

    private byte[] serializeObject(Object instance) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            serialize(instance, baos);
        } catch (IOException e) {
            throw new SerializationException(
                    "An exception occurred while trying to serialize a Saga or Association Value for storage",
                    e);
        }
        return baos.toByteArray();
    }

    @Override
    public Saga deserialize(byte[] serializedSaga) {
        try {
            return (Saga) deserialize(new ByteArrayInputStream(serializedSaga));
        } catch (IOException e) {
            throw new SerializationException("An exception occurred while trying to deserialize an object", e);
        }
    }
}
