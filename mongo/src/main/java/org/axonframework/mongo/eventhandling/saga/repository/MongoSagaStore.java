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

package org.axonframework.mongo.eventhandling.saga.repository;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.bson.Document;

import java.util.Set;
import java.util.TreeSet;

import static com.mongodb.client.model.Projections.include;

/**
 * Implementations of the SagaRepository that stores Sagas and their associations in a Mongo Database. Each Saga and
 * its associations is stored as a single document.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 2.0
 */
public class MongoSagaStore implements SagaStore<Object> {

    private final MongoTemplate mongoTemplate;
    private final Serializer serializer;

    /**
     * Initializes the Repository, using given {@code mongoTemplate} to access the collections containing the
     * stored Saga instances. Serialization is done using an XStream based serializer.
     *
     * @param mongoTemplate the template providing access to the collections
     */
    public MongoSagaStore(MongoTemplate mongoTemplate) {
        this(mongoTemplate, new XStreamSerializer());
    }

    /**
     * Initializes the Repository, using given {@code mongoTemplate} to access the collections containing the
     * stored Saga instances, serializing Saga instances using the given {@code serializer}.
     *
     * @param mongoTemplate the template providing access to the collections
     * @param serializer    the serializer to serialize Saga instances with
     */
    public MongoSagaStore(MongoTemplate mongoTemplate, Serializer serializer) {
        Assert.notNull(mongoTemplate, () -> "mongoTemplate may not be null");
        this.mongoTemplate = mongoTemplate;
        this.serializer = serializer;
    }

    @Override
    public <S> Entry<S> loadSaga(Class<S> sagaType, String sagaIdentifier) {
        Document dbSaga = mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(sagaIdentifier)).first();
        if (dbSaga == null) {
            return null;
        }
        SagaEntry<S> sagaEntry = new SagaEntry<>(dbSaga);
        S loadedSaga = sagaEntry.getSaga(serializer);
        return new Entry<S>() {
            @Override
            public TrackingToken trackingToken() {
                return null;
            }

            @Override
            public Set<AssociationValue> associationValues() {
                return sagaEntry.getAssociationValues();
            }

            @Override
            public S saga() {
                return loadedSaga;
            }
        };
    }

    @Override
    public Set<String> findSagas(Class<?> sagaType, AssociationValue associationValue) {
        final BasicDBObject value = associationValueQuery(sagaType, associationValue);

        MongoCursor<Document> dbCursor = mongoTemplate.sagaCollection().find(value).projection(include("sagaIdentifier")).iterator();
        Set<String> found = new TreeSet<>();
        while (dbCursor.hasNext()) {
            found.add((String) dbCursor.next().get("sagaIdentifier"));
        }
        return found;
    }

    private BasicDBObject associationValueQuery(Class<?> sagaType, AssociationValue associationValue) {
        final BasicDBObject value = new BasicDBObject();
        value.put("sagaType", getSagaTypeName(sagaType));

        final BasicDBObject dbAssociation = new BasicDBObject();
        dbAssociation.put("key", associationValue.getKey());
        dbAssociation.put("value", associationValue.getValue());

        value.put("associations", dbAssociation);
        return value;
    }

    @Override
    public void deleteSaga(Class<?> sagaType, String sagaIdentifier, Set<AssociationValue> associationValues) {
        mongoTemplate.sagaCollection().findOneAndDelete(SagaEntry.queryByIdentifier(sagaIdentifier));
    }

    @Override
    public void updateSaga(Class<?> sagaType, String sagaIdentifier, Object saga, TrackingToken token, AssociationValues associationValues) {
        SagaEntry<?> sagaEntry = new SagaEntry<>(sagaIdentifier, saga, associationValues.asSet(), serializer);
        mongoTemplate.sagaCollection().updateOne(
                SagaEntry.queryByIdentifier(sagaIdentifier),
                new Document("$set", sagaEntry.asDocument()));
    }

    @Override
    public void insertSaga(Class<?> sagaType, String sagaIdentifier, Object saga, TrackingToken token, Set<AssociationValue> associationValues) {
        SagaEntry<?> sagaEntry = new SagaEntry<>(sagaIdentifier, saga, associationValues, serializer);
        Document sagaObject = sagaEntry.asDocument();
        mongoTemplate.sagaCollection().insertOne(sagaObject);
    }

    private String getSagaTypeName(Class<?> sagaType) {
        return serializer.typeForClass(sagaType).getName();
    }

}
