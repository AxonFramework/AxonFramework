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

package org.axonframework.saga.repository;

import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaStorageException;
import org.axonframework.util.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * SagaSerializer implementation that uses Java serialization to serialize and deserialize Sagas. This implementation
 * is
 * very suitable if the life span of a Saga allows classes to remain unchanged. If Class definitions need to be changed
 * during the life cycle of existing Sagas, another implementation, like the {@link XStreamSagaSerializer} might be a
 * more suitable alternative.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class JavaSagaSerializer implements SagaSerializer {

    @Override
    public byte[] serialize(Saga saga) {
        return serializeObject(saga);
    }

    private byte[] serializeObject(Object instance) {
        if (!Serializable.class.isInstance(instance)) {
            throw new SagaStorageException("This repository can only store Serializable sagas and AssociationValues");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(baos);
            try {
                oos.writeObject(instance);
            } finally {
                oos.close();
            }
        } catch (IOException e) {
            throw new SerializationException(
                    "An exception occurred while trying to serialize a Saga or Association Value for storage",
                    e);
        }
        return baos.toByteArray();
    }

    @Override
    public Saga deserialize(byte[] serializedSaga) {
        return deserializeObject(serializedSaga, Saga.class);
    }

    private <T> T deserializeObject(byte[] serializedInstance, Class<T> expectedType) {
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(new ByteArrayInputStream(serializedInstance));
            return expectedType.cast(ois.readObject());
        } catch (IOException e) {
            throw new SerializationException("An exception occurred while trying to deserialize a stored Saga", e);
        } catch (ClassNotFoundException e) {
            throw new SerializationException("An exception occurred while trying to deserialize a stored Saga", e);
        }
    }

    @Override
    public byte[] serializeAssociationValue(Object value) {
        return serializeObject(value);
    }

    @Override
    public Object deserializeAssociationValue(byte[] serializedAssociationValue) {
        return deserializeObject(serializedAssociationValue, Object.class);
    }
}
