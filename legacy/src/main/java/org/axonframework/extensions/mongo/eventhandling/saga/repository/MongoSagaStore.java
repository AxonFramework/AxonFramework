/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extensions.mongo.eventhandling.saga.repository;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.conversion.Converter;
import org.axonframework.extensions.mongo.MongoTemplate;
import org.axonframework.messaging.core.unitofwork.transaction.NoTransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.AssociationValues;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;

import static com.mongodb.client.model.Projections.include;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementations of the SagaRepository that stores Sagas and their associations in a Mongo Database. Each Saga and its
 * associations is stored as a single document.
 * <p>
 * The document layout is that of Axon Framework 4, so an existing sagas collection can be read and updated without
 * migration, provided its {@code sagaType} field holds saga class names. Axon Framework 4 derived that field through
 * its {@code Serializer}, which for the default configuration also produced the class name. An application that mapped
 * its saga classes to some other type name, an XStream alias being the usual way, has documents this store cannot route
 * to and must rewrite that field to the class name first.
 * <p>
 * Note that {@code sagaType} records the class of the saga instance handed to
 * {@link #insertSaga(Class, String, Object, Set)}, which is not necessarily the {@code sagaType} argument. Loading, on
 * the other hand, matches on the identifier alone and ignores the type entirely.
 * <p>
 * This store performs one Mongo operation per call, so it manages no transaction of its own beyond whatever the
 * {@link TransactionManager} it was given provides. That defaults to {@link NoTransactionManager}, as it did in Axon
 * Framework 4, under which a call commits on its own.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 2.0
 */
public class MongoSagaStore implements SagaStore<Object> {

    private final MongoTemplate mongoTemplate;
    private final Converter converter;
    private final TransactionManager transactionManager;

    /**
     * Instantiate a {@link MongoSagaStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link MongoTemplate} and {@link Converter} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MongoSagaStore} instance
     */
    protected MongoSagaStore(Builder builder) {
        builder.validate();
        this.mongoTemplate = builder.mongoTemplate;
        this.converter = builder.converter;
        this.transactionManager = builder.transactionManager;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MongoSagaStore}.
     * <p>
     * The {@link MongoTemplate} and {@link Converter} are <b>hard requirements</b> and as such should be provided. The
     * {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     *
     * @return a Builder to be able to create a {@link MongoSagaStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public @Nullable <S> Entry<S> loadSaga(Class<S> sagaType, String sagaIdentifier) {
        Document dbSaga = transactionManager.fetchInTransaction(
                () -> mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(sagaIdentifier)).first()
        );
        if (dbSaga == null) {
            return null;
        }
        SagaEntry<S> sagaEntry = new SagaEntry<>(dbSaga);
        S loadedSaga = sagaEntry.getSaga(converter, sagaType);
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
        Set<String> found = new TreeSet<>();
        try (MongoCursor<Document> dbCursor = transactionManager.fetchInTransaction(
                () -> mongoTemplate.sagaCollection()
                                   .find(value)
                                   .projection(include("sagaIdentifier"))
                                   .iterator())) {
            while (dbCursor.hasNext()) {
                found.add((String) dbCursor.next().get("sagaIdentifier"));
            }
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
        transactionManager.executeInTransaction(
                () -> mongoTemplate.sagaCollection().findOneAndDelete(SagaEntry.queryByIdentifier(sagaIdentifier))
        );
    }

    @Override
    public void updateSaga(Class<?> sagaType, String sagaIdentifier, Object saga, AssociationValues associationValues) {
        SagaEntry<?> sagaEntry = new SagaEntry<>(sagaIdentifier, saga, associationValues.asSet(), converter);
        transactionManager.executeInTransaction(
                () -> mongoTemplate.sagaCollection().updateOne(
                        SagaEntry.queryByIdentifier(sagaIdentifier),
                        new Document("$set", sagaEntry.asDocument()))
        );
    }

    @Override
    public void insertSaga(Class<?> sagaType,
                           String sagaIdentifier,
                           Object saga,
                           Set<AssociationValue> associationValues) {
        SagaEntry<?> sagaEntry = new SagaEntry<>(sagaIdentifier, saga, associationValues, converter);
        Document sagaObject = sagaEntry.asDocument();
        transactionManager.executeInTransaction(
                () -> mongoTemplate.sagaCollection().insertOne(sagaObject)
        );
    }

    private String getSagaTypeName(Class<?> sagaType) {
        return sagaType.getName();
    }

    /**
     * Builder class to instantiate a {@link MongoSagaStore}.
     * <p>
     * The {@link MongoTemplate} and {@link Converter} are <b>hard requirements</b> and as such should be provided. The
     * {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     */
    public static class Builder {

        private MongoTemplate mongoTemplate;
        private Converter converter;
        private TransactionManager transactionManager = NoTransactionManager.instance();

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
         * Sets the {@link Converter} used to convert a Saga instance to and from its stored form.
         *
         * @param converter a {@link Converter} used to convert a Saga instance to and from its stored form
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder converter(Converter converter) {
            assertNonNull(converter, "Converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transaction around fetching tokens. Will default to
         * {@link NoTransactionManager}, which effectively will not use transactions.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transaction around fetching event data
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
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
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(mongoTemplate, "The MongoTemplate is a hard requirement and should be provided");
            assertNonNull(converter, "The Converter is a hard requirement and should be provided");
        }
    }
}
