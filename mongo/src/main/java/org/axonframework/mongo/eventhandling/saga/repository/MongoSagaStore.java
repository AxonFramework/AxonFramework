/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.mongo.MongoTemplate;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.bson.Document;

import java.util.Set;
import java.util.TreeSet;

import static com.mongodb.client.model.Projections.include;
import static org.axonframework.common.BuilderUtils.assertNonNull;

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
     * Instantiate a {@link MongoSagaStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link MongoTemplate} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MongoSagaStore} instance
     */
    protected MongoSagaStore(Builder builder) {
        builder.validate();
        this.mongoTemplate = builder.mongoTemplate;
        this.serializer = builder.serializer;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MongoSagaStore}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link XStreamSerializer}.
     * The {@link MongoTemplate} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link MongoSagaStore}
     */
    public static Builder builder() {
        return new Builder();
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

        MongoCursor<Document> dbCursor = mongoTemplate.sagaCollection()
                                                      .find(value)
                                                      .projection(include("sagaIdentifier"))
                                                      .iterator();
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
    public void updateSaga(Class<?> sagaType, String sagaIdentifier, Object saga, AssociationValues associationValues) {
        SagaEntry<?> sagaEntry = new SagaEntry<>(sagaIdentifier, saga, associationValues.asSet(), serializer);
        mongoTemplate.sagaCollection().updateOne(
                SagaEntry.queryByIdentifier(sagaIdentifier),
                new Document("$set", sagaEntry.asDocument()));
    }

    @Override
    public void insertSaga(Class<?> sagaType,
                           String sagaIdentifier,
                           Object saga,
                           Set<AssociationValue> associationValues) {
        SagaEntry<?> sagaEntry = new SagaEntry<>(sagaIdentifier, saga, associationValues, serializer);
        Document sagaObject = sagaEntry.asDocument();
        mongoTemplate.sagaCollection().insertOne(sagaObject);
    }

    private String getSagaTypeName(Class<?> sagaType) {
        return serializer.typeForClass(sagaType).getName();
    }

    /**
     * Builder class to instantiate a {@link MongoSagaStore}.
     * <p>
     * The {@link Serializer} is defaulted to a {@link XStreamSerializer}.
     * The {@link MongoTemplate} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder {

        private MongoTemplate mongoTemplate;
        private Serializer serializer = XStreamSerializer.builder().build();

        /**
         * Sets the {@link MongoTemplate} providing access to the collections.
         *
         * @param mongoTemplate the {@link MongoTemplate} providing access to the collections
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder mongoTemplate(MongoTemplate mongoTemplate) {
            assertNonNull(mongoTemplate, "MongoTemplate may not be null");
            this.mongoTemplate = mongoTemplate;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize a Saga instance. Defaults to a {@link XStreamSerializer}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize a Saga instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Initializes a {@link MongoSagaStore} as specified through this Builder.
         *
         * @return a {@link MongoSagaStore} as specified through this Builder
         */
        public MongoSagaStore build() {
            return new MongoSagaStore(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(mongoTemplate, "The MongoTemplate is a hard requirement and should be provided");
        }
    }
}
