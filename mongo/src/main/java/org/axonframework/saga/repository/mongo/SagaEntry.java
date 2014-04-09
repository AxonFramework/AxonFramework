/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.saga.repository.mongo;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java representation of sagas stored in a mongo instance
 *
 * @author Jettro Coenradie
 * @since 2.0
 */
public class SagaEntry {

    private static final String SAGA_IDENTIFIER = "sagaIdentifier";
    private static final String SERIALIZED_SAGA = "serializedSaga";
    private static final String SAGA_TYPE = "sagaType";
    private static final String ASSOCIATIONS = "associations";
    private static final String ASSOCIATION_KEY = "key";
    private static final String ASSOCIATION_VALUE = "value";

    private String sagaId;
    private String sagaType;

    private byte[] serializedSaga;

    private volatile Saga saga;
    private final Set<AssociationValue> associationValues;

    /**
     * Constructs a new SagaEntry for the given <code>saga</code>. The given saga must be serializable. The provided
     * saga is not modified by this operation.
     *
     * @param saga       The saga to store
     * @param serializer The serialization mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(Saga saga, Serializer serializer) {
        this.sagaId = saga.getSagaIdentifier();
        SerializedObject<byte[]> serialized = serializer.serialize(saga, byte[].class);
        this.serializedSaga = serialized.getData();
        this.sagaType = serializer.typeForClass(saga.getClass()).getName();
        this.saga = saga;
        this.associationValues = new HashSet<AssociationValue>(this.saga.getAssociationValues().asSet());
    }

    /**
     * Initializes a Saga entry using a DBObject containing the Mongo Document
     *
     * @param dbSaga The mongo Document containing the serialized saga
     */
    public SagaEntry(DBObject dbSaga) {
        this.sagaId = (String) dbSaga.get(SAGA_IDENTIFIER);
        this.serializedSaga = (byte[]) dbSaga.get(SERIALIZED_SAGA);
        this.sagaType = (String) dbSaga.get(SAGA_TYPE);
        this.associationValues = toAssociationSet(dbSaga);
    }

    /**
     * Returns the Saga instance stored in this entry.
     *
     * @param serializer The serializer to decode the Saga
     * @return the Saga instance stored in this entry
     */
    public Saga getSaga(Serializer serializer) {
        if (saga != null) {
            return saga;
        }
        return (Saga) serializer.deserialize(new SimpleSerializedObject<byte[]>(serializedSaga, byte[].class,
                                                                                sagaType, ""));
    }

    /**
     * Returns the Mongo Document representing the Saga provided in this entry.
     *
     * @return the Mongo Document representing the Saga provided in this entry
     */
    public DBObject asDBObject() {
        return new BasicDBObject(SAGA_TYPE, sagaType)
                .append(SAGA_IDENTIFIER, sagaId)
                .append(SERIALIZED_SAGA, serializedSaga)
                .append(ASSOCIATIONS, toDBList(associationValues));
    }

    @SuppressWarnings("unchecked")
    private Set<AssociationValue> toAssociationSet(DBObject dbSaga) {
        Set<AssociationValue> values = new HashSet<AssociationValue>();
        List<DBObject> list = (List<DBObject>) dbSaga.get(ASSOCIATIONS);
        if (list != null) {
            for (DBObject item : list) {
                values.add(new AssociationValue((String) item.get(ASSOCIATION_KEY),
                                                (String) item.get(ASSOCIATION_VALUE)));
            }
        }
        return values;
    }

    private static List toDBList(Iterable<AssociationValue> associationValues) {
        BasicDBList list = new BasicDBList();
        for (AssociationValue associationValue : associationValues) {
            list.add(new BasicDBObject(ASSOCIATION_KEY, associationValue.getKey())
                             .append(ASSOCIATION_VALUE, associationValue.getValue()));
        }
        return list;
    }

    /**
     * Returns the Mongo Query to find a Saga based on its identifier.
     *
     * @param identifier The identifier of the saga to find
     * @return the Query (as DBObject) to find a Saga in a Mongo Database
     */
    public static DBObject queryByIdentifier(String identifier) {
        return BasicDBObjectBuilder.start()
                                   .add(SAGA_IDENTIFIER, identifier)
                                   .get();
    }
}