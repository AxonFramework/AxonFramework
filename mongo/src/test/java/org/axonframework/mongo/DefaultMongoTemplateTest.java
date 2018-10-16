/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.mongo;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.*;

import static org.mockito.Mockito.*;

public class DefaultMongoTemplateTest {

    private MongoClient mockMongo;
    private MongoDatabase mockDb;
    private DefaultMongoTemplate testSubject;

    @Before
    public void createFixtures() {
        mockMongo = mock(MongoClient.class);
        mockDb = mock(MongoDatabase.class);
        //noinspection unchecked
        MongoCollection<Document> mockCollection = mock(MongoCollection.class);

        when(mockMongo.getDatabase(anyString())).thenReturn(mockDb);
        when(mockDb.getCollection(anyString())).thenReturn(mockCollection);
    }

    @Test
    public void testTrackingTokenDefaultValues() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build();

        verify(mockMongo).getDatabase("axonframework");

        testSubject.trackingTokensCollection();
        verify(mockDb).getCollection("trackingtokens");
    }

    @Test
    public void testTrackingTokenCustomValues() {
        testSubject = DefaultMongoTemplate.builder()
                                          .mongoDatabase(mockMongo, "customDatabaseName")
                                          .build()
                                          .withTrackingTokenCollection("customCollectionName");

        verify(mockMongo).getDatabase("customDatabaseName");
        testSubject.trackingTokensCollection();
        verify(mockDb).getCollection("customCollectionName");
    }

    @Test
    public void testSagasDefaultValues() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build();

        testSubject.sagaCollection();
        verify(mockDb).getCollection("sagas");
    }

    @Test
    public void testCustomProvidedNames() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build()
                                          .withSagasCollection("customsagas");

        testSubject.sagaCollection();
        verify(mockDb).getCollection("customsagas");
    }

    @Test
    public void testDomainEvents() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build();

        testSubject.eventCollection();
        verify(mockDb).getCollection("domainevents");
    }

    @Test
    public void testSnapshotEvents() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build();

        testSubject.snapshotCollection();

        verify(mockDb).getCollection("snapshotevents");
    }

    @Test
    public void testEventsCollectionWithCustomProvidedNames() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build()
                                          .withDomainEventsCollection("customevents")
                                          .withSnapshotCollection("customsnapshots");

        testSubject.eventCollection();
        verify(mockDb).getCollection("customevents");
    }

    @Test
    public void testSnapshotsCollectionWithCustomProvidedNames() {
        testSubject = DefaultMongoTemplate.builder().mongoDatabase(mockMongo).build()
                                          .withDomainEventsCollection("customevents")
                                          .withSnapshotCollection("customsnapshots");

        testSubject.snapshotCollection();
        verify(mockDb).getCollection("customsnapshots");
    }
}
