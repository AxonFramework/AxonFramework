/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga.repository.jpa;

import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.SagaStorageException;
import org.axonframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

/**
 * @author Allard Buijze
 */
@Entity
public class SagaEntry {

    @Id
    private String sagaId;

    @Lob
    private byte[] serializedSaga;

    public static SagaEntry forSaga(Saga saga) {
        byte[] serializedSaga = serialize(saga);
        return new SagaEntry(saga.getSagaIdentifier(), serializedSaga);
    }

    private static byte[] serialize(Saga saga) {
        if (!Serializable.class.isInstance(saga)) {
            throw new SagaStorageException("This repository can only store Serializable sagas");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(baos);
            try {
                oos.writeObject(saga);
            } finally {
                oos.close();
            }
        } catch (IOException e) {
            throw new SagaStorageException("An exception occurred while trying to serialize a Saga for storage", e);
        }
        return baos.toByteArray();
    }

    private static Saga deserialize(byte[] serializedSaga) {
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(new ByteArrayInputStream(serializedSaga));
            return (Saga) ois.readObject();
        } catch (IOException e) {
            throw new SagaStorageException("An exception occurred while trying to deserialize a stored Saga", e);
        } catch (ClassNotFoundException e) {
            throw new SagaStorageException("An exception occurred while trying to deserialize a stored Saga", e);
        }
    }

    public SagaEntry(String sagaId, byte[] serializedSaga) {
        this.sagaId = sagaId;
        this.serializedSaga = serializedSaga;
    }

    public String getSagaId() {
        return sagaId;
    }

    public Saga getSaga() {
        return deserialize(serializedSaga);
    }

    public void updateSaga(Saga saga) {
        Assert.isTrue(sagaId.equals(saga.getSagaIdentifier()),
                      "Cannot update an entry with another saga. Make sure Identifiers have not been altered.");
        this.serializedSaga = serialize(saga);
    }

    protected SagaEntry() {
        // required by JPA
    }
}
