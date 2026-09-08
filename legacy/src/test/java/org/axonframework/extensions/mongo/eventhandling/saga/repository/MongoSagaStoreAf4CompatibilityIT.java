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
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.extensions.mongo.DefaultMongoTemplate;
import org.axonframework.extensions.mongo.MongoTemplate;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.AssociationValuesImpl;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.StubSaga;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link MongoSagaStore} reads and writes a sagas collection written by Axon Framework 4.
 * <p>
 * Documents are seeded by hand in the Axon Framework 4 layout, with the stored saga spelled out as JSON rather than
 * produced by the store's own converter, so that a change to either the document layout or the stored representation
 * fails here rather than passing by construction.
 * <p>
 * The Axon Framework 4 JPA and JDBC saga tables carry a {@code revision} column, and the suite covering those two
 * asserts on it. The Mongo document has no counterpart, so this store answers to a set of its own.
 *
 * @author Mateusz Nowak
 */
@Testcontainers
class MongoSagaStoreAf4CompatibilityIT {

    private static final AssociationValue ORDER_1 = new AssociationValue("orderId", "order-1");
    private static final AssociationValue ORDER_2 = new AssociationValue("orderId", "order-2");

    private static final String SAGA_1 = "saga-1";
    private static final String SAGA_2 = "saga-2";

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
        testSubject = MongoSagaStore.builder()
                                    .mongoTemplate(mongoTemplate)
                                    .converter(new JacksonConverter())
                                    .build();

        String sagaType = StubSaga.class.getName();
        insertAf4Saga(SAGA_1, sagaType, "{\"handledEvents\":[\"OrderPlaced\"]}", ORDER_1);
        insertAf4Saga(SAGA_2, sagaType, "{\"handledEvents\":[\"OrderPlaced\",\"OrderPaid\"]}", ORDER_2);
    }

    /**
     * Inserts a saga document directly, bypassing the store, in the Axon Framework 4 layout: the stored saga as BSON
     * binary, and the associations embedded in the saga document as {@code key}/{@code value} sub-documents.
     */
    private void insertAf4Saga(String sagaId, String sagaType, String serializedSaga, AssociationValue association) {
        mongoTemplate.sagaCollection().insertOne(new Document("sagaType", sagaType)
                                                         .append("sagaIdentifier", sagaId)
                                                         .append("serializedSaga",
                                                                 serializedSaga.getBytes(StandardCharsets.UTF_8))
                                                         .append("associations",
                                                                 List.of(new Document("key", association.getKey())
                                                                                 .append("value",
                                                                                         association.getValue()))));
    }

    private Document documentOf(String sagaId) {
        return mongoTemplate.sagaCollection().find(SagaEntry.queryByIdentifier(sagaId)).first();
    }

    @Nested
    class ReadingAxonFramework4Documents {

        @Test
        void aSagaWrittenByAxonFramework4IsReadBack() {
            // given a document written by Axon Framework 4 / when
            SagaStore.Entry<StubSaga> entry = testSubject.loadSaga(StubSaga.class, SAGA_1);

            // then
            assertThat(entry).isNotNull();
            assertThat(entry.saga().getHandledEvents()).containsExactly("OrderPlaced");
            assertThat(entry.associationValues()).containsExactly(ORDER_1);
        }

        @Test
        void aSagaIsFoundByAnAssociationWrittenByAxonFramework4() {
            // given / when / then
            assertThat(testSubject.findSagas(StubSaga.class, ORDER_1)).containsExactly(SAGA_1);
            assertThat(testSubject.findSagas(StubSaga.class, ORDER_2)).containsExactly(SAGA_2);
        }

        @Test
        void documentsWhoseSagaTypeIsNotTheClassNameAreNotReachableByAssociation() {
            // given a document written by an Axon Framework 4 application whose Serializer mapped the saga class to a
            // type name other than its fully qualified class name, an XStream alias being the usual way to get one
            String alias = "order-saga";
            insertAf4Saga("aliased-saga", alias, "{\"handledEvents\":[\"OrderPlaced\"]}", ORDER_1);

            // when / then the saga type is matched literally against the class name, so the document is invisible.
            // Axon Framework 4 resolved it through the same Serializer that wrote it; this store has no such mapping,
            // and such a collection needs its saga type fields rewritten to the class name before it can be used here.
            assertThat(testSubject.findSagas(StubSaga.class, ORDER_1)).containsExactly(SAGA_1);

            // the saga document itself is queried by identifier alone, so it still loads, associations and all
            SagaStore.Entry<StubSaga> entry = testSubject.loadSaga(StubSaga.class, "aliased-saga");
            assertThat(entry).isNotNull();
            assertThat(entry.saga().getHandledEvents()).containsExactly("OrderPlaced");
            assertThat(entry.associationValues()).containsExactly(ORDER_1);
        }
    }

    @Nested
    class WritingToAnAxonFramework4Collection {

        @Test
        void updatingASagaWrittenByAxonFramework4ReplacesItsStateInPlace() {
            // given a document written by Axon Framework 4
            StubSaga updated = new StubSaga();
            updated.handled("OrderShipped");

            // when
            testSubject.updateSaga(StubSaga.class,
                                   SAGA_2,
                                   updated,
                                   new AssociationValuesImpl(singleton(ORDER_2)));

            // then the state was replaced, and the identifier and type it was found by are unchanged
            SagaStore.Entry<StubSaga> entry = testSubject.loadSaga(StubSaga.class, SAGA_2);
            assertThat(entry).isNotNull();
            assertThat(entry.saga().getHandledEvents()).containsExactly("OrderShipped");
            assertThat(testSubject.findSagas(StubSaga.class, ORDER_2)).containsExactly(SAGA_2);
        }

        @Test
        void updatingASagaLeavesFieldsThisStoreDoesNotWriteUntouched() {
            // given a document carrying a field this store knows nothing about, as one written by a later Axon
            // Framework 4 version or a custom store might
            mongoTemplate.sagaCollection().updateOne(SagaEntry.queryByIdentifier(SAGA_1),
                                                     new Document("$set", new Document("revision", "2")));

            // when
            testSubject.updateSaga(StubSaga.class,
                                   SAGA_1,
                                   new StubSaga(),
                                   new AssociationValuesImpl(singleton(ORDER_1)));

            // then the update set only the fields it owns, so the unknown one survived
            assertThat(documentOf(SAGA_1)).containsEntry("revision", "2");
        }

        @Test
        void anInsertedSagaCarriesTheAxonFramework4DocumentLayout() {
            // given / when
            StubSaga saga = new StubSaga();
            saga.handled("OrderPlaced");
            testSubject.insertSaga(StubSaga.class, "saga-new", saga, singleton(ORDER_1));

            // then, which is what an Axon Framework 4 reader expects to find
            Document document = documentOf("saga-new");
            assertThat(document).isNotNull();
            assertThat(document.getString("sagaIdentifier")).isEqualTo("saga-new");
            assertThat(document.getString("sagaType")).isEqualTo(StubSaga.class.getName());
            assertThat(document.get("serializedSaga")).isInstanceOf(Binary.class);
            assertThat(new String(((Binary) document.get("serializedSaga")).getData(), StandardCharsets.UTF_8))
                    .isEqualTo("{\"handledEvents\":[\"OrderPlaced\"]}");
            assertThat(document.getList("associations", Document.class))
                    .containsExactly(new Document("key", "orderId").append("value", "order-1"));
        }

        /**
         * The JPA and JDBC stores write {@code "axon-legacy"} to the {@code revision} column, distinguishing a row this
         * module wrote from one Axon Framework 4 left. Axon Framework 4 never gave the Mongo document such a field, so
         * this store adds none: an unknown field is one more thing for a reader outside this module to account for.
         */
        @Test
        void anInsertedSagaCarriesNoFieldsBeyondTheAxonFramework4Layout() {
            // given / when
            testSubject.insertSaga(StubSaga.class, "saga-new", new StubSaga(), singleton(ORDER_1));

            // then, _id aside, which Mongo assigns itself
            Document document = documentOf("saga-new");
            assertThat(document).isNotNull();
            assertThat(document.keySet())
                    .containsExactlyInAnyOrder("_id", "sagaType", "sagaIdentifier", "serializedSaga", "associations");
        }
    }
}
