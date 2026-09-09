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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.extensions.mongo.DefaultMongoTemplate;
import org.axonframework.extensions.mongo.MongoTemplate;
import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.AssociationValuesImpl;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.SagaStoreTestSuite;
import org.axonframework.modelling.saga.repository.StubSaga;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link MongoSagaStore} against a real MongoDB.
 * <p>
 * Nobody manages a transaction, which is how the store behaves with the {@link TransactionManager} it defaults to. Each
 * of its operations is a single Mongo call, so that is enough for the whole {@link SagaStore} contract.
 *
 * @author Mateusz Nowak
 */
@Testcontainers
class MongoSagaStoreIT extends SagaStoreTestSuite {

    private static final AssociationValue ORDER_1 = new AssociationValue("orderId", "order-1");

    @Container
    private static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer("mongo:8.0");

    private static MongoClient mongoClient;

    private MongoTemplate mongoTemplate;
    private MongoSagaStore testSubject;

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MONGO_CONTAINER.getConnectionString());
    }

    @AfterAll
    static void disconnect() {
        mongoClient.close();
    }

    @BeforeEach
    void setUp() {
        mongoTemplate = DefaultMongoTemplate.builder()
                                            .mongoDatabase(mongoClient)
                                            .build();
        mongoTemplate.sagaCollection().drop();
        testSubject = store(mongoTemplate);
    }

    private static MongoSagaStore store(MongoTemplate mongoTemplate) {
        return store(mongoTemplate, null);
    }

    private static MongoSagaStore store(MongoTemplate mongoTemplate, TransactionManager transactionManager) {
        MongoSagaStore.Builder builder = MongoSagaStore.builder()
                                                       .mongoTemplate(mongoTemplate)
                                                       .converter(new JacksonConverter());
        if (transactionManager != null) {
            builder.transactionManager(transactionManager);
        }
        return builder.build();
    }

    @Override
    protected SagaStore<Object> testSubject() {
        return testSubject;
    }

    /**
     * Pins behaviour that differs per implementation, and is therefore not part of {@link SagaStoreTestSuite}. This
     * store issues an update that matches no document and does not upsert, so nothing happens at all;
     * {@code InMemorySagaStore} creates the saga and {@code JpaSagaStore} applies the association changes anyway.
     */
    @Test
    void updatingAnAbsentSagaChangesNothing() {
        // given no saga stored, and an association pending addition
        AssociationValuesImpl associations = new AssociationValuesImpl();
        associations.add(ORDER_1);

        // when
        testSubject.updateSaga(StubSaga.class, "saga-1", new StubSaga(), associations);

        // then no saga came into being, and no association points at one
        assertThat(testSubject.loadSaga(StubSaga.class, "saga-1")).isNull();
        assertThat(testSubject.findSagas(StubSaga.class, ORDER_1)).isEmpty();
        assertThat(mongoTemplate.sagaCollection().countDocuments()).isZero();
    }

    /**
     * Characterises behaviour inherited from Axon Framework 4: {@link SagaEntry#queryByIdentifier(String)} matches on
     * the identifier alone, so the saga type asked for plays no part in finding the document. Since the type does decide
     * what the stored bytes are converted into, asking for the wrong one fails in conversion rather than returning
     * {@code null}.
     */
    @Test
    void loadSagaMatchesOnTheIdentifierAloneAndIgnoresTheSagaType() {
        // given a saga stored under one type
        StubSaga saga = new StubSaga();
        saga.handled("OrderPlaced");
        testSubject.insertSaga(StubSaga.class, "saga-1", saga, singleton(ORDER_1));

        // when loaded as an unrelated type whose shape happens to accept the same document
        SagaStore.Entry<AcceptsAnything> entry = testSubject.loadSaga(AcceptsAnything.class, "saga-1");

        // then the document was found regardless, and converted into the type asked for
        assertThat(entry).isNotNull();
        assertThat(entry.saga()).isInstanceOf(AcceptsAnything.class);
        assertThat(entry.saga().handledEvents()).containsExactly("OrderPlaced");
    }

    /**
     * Characterises behaviour inherited from Axon Framework 4: {@link SagaEntry} derives the stored saga type from the
     * saga instance, not from the {@code sagaType} argument. A store that took the argument instead would make this
     * document findable as a {@code StubSaga}, and Axon Framework 4 applications could depend on either.
     */
    @Test
    void theStoredSagaTypeComesFromTheSagaInstanceAndNotTheDeclaredType() {
        // given a saga of one class inserted under the type of another
        testSubject.insertSaga(StubSaga.class, "saga-1", new OtherStubSaga("created"), singleton(ORDER_1));

        // when / then it is findable as the class of the instance
        assertThat(testSubject.findSagas(OtherStubSaga.class, ORDER_1)).containsExactly("saga-1");
        assertThat(testSubject.findSagas(StubSaga.class, ORDER_1)).isEmpty();
    }

    /**
     * The store holds a {@link TransactionManager} for no other reason than to wrap its operations in one, so a
     * transaction manager that records is enough to show it does.
     */
    @Test
    void everyOperationRunsInsideATransaction() {
        // given a store over a recording transaction manager
        CountingTransactionManager transactionManager = new CountingTransactionManager();
        MongoSagaStore store = store(mongoTemplate, transactionManager);

        // when each operation is invoked once
        store.insertSaga(StubSaga.class, "saga-1", new StubSaga(), singleton(ORDER_1));
        store.updateSaga(StubSaga.class, "saga-1", new StubSaga(), new AssociationValuesImpl(singleton(ORDER_1)));
        store.loadSaga(StubSaga.class, "saga-1");
        store.findSagas(StubSaga.class, ORDER_1);
        store.deleteSaga(StubSaga.class, "saga-1", singleton(ORDER_1));

        // then each ran in a transaction of its own, and all of them committed
        assertThat(transactionManager.started()).isEqualTo(5);
        assertThat(transactionManager.committed()).isEqualTo(5);
        assertThat(transactionManager.rolledBack()).isZero();
    }

    @Test
    void builderRejectsANullTransactionManager() {
        // given / when / then
        MongoSagaStore.Builder builder = MongoSagaStore.builder();
        assertThatThrownBy(() -> builder.transactionManager(null)).isInstanceOf(AxonConfigurationException.class);
    }

    /**
     * Axon Framework 4 defaulted the converter's predecessor to an {@code XStreamSerializer}. Axon Framework 5 has no
     * such default to fall back on, so a store without one is rejected at build time rather than at first use.
     */
    @Test
    void buildingWithoutAConverterIsRejected() {
        // given / when / then
        MongoSagaStore.Builder builder = MongoSagaStore.builder().mongoTemplate(mongoTemplate);
        assertThatThrownBy(builder::build).isInstanceOf(AxonConfigurationException.class);
    }

    /**
     * Shares the shape of {@link StubSaga} so that a document written for one converts into the other, which is what
     * lets {@link #loadSagaMatchesOnTheIdentifierAloneAndIgnoresTheSagaType()} tell "found the document" apart from
     * "failed to convert it".
     */
    private record AcceptsAnything(List<String> handledEvents) {

    }

    private record OtherStubSaga(String state) {

    }

    private static class CountingTransactionManager implements TransactionManager {

        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger committed = new AtomicInteger();
        private final AtomicInteger rolledBack = new AtomicInteger();

        @Override
        public Transaction startTransaction() {
            started.incrementAndGet();
            return new Transaction() {
                @Override
                public void commit() {
                    committed.incrementAndGet();
                }

                @Override
                public void rollback() {
                    rolledBack.incrementAndGet();
                }
            };
        }

        private int started() {
            return started.get();
        }

        private int committed() {
            return committed.get();
        }

        private int rolledBack() {
            return rolledBack.get();
        }
    }
}
