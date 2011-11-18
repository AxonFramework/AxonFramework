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

package org.axonframework.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.Mongo;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author Jettro Coenradie
 */
public class AxonMongoWrapperTest {

    private DefaultMongoTemplate mongoTemplate;
    private Mongo mockMongo;
    private DB mockDb;

    @Before
    public void createFixtures() {
        mockMongo = mock(Mongo.class);
        mockDb = mock(DB.class);
        mongoTemplate = new DefaultMongoTemplate(mockMongo);
    }

    @Test
    public void testDomainEvents() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        mongoTemplate.domainEventCollection();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("domainevents");
    }

    @Test
    public void testSnapshotEvents() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        mongoTemplate.snapshotEventCollection();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("snapshotevents");
    }

    @Test
    public void testDatabase() throws Exception {
        mongoTemplate.database();
        verify(mockMongo).getDB("axonframework");

    }

    @Test
    public void testDomainEvents_changedName() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        mongoTemplate.setDomainEventsCollectionName("customdomainevents");
        mongoTemplate.domainEventCollection();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("customdomainevents");
    }

    @Test
    public void testSnapshotEvents_changedName() throws Exception {
        when(mockMongo.getDB("axonframework")).thenReturn(mockDb);

        mongoTemplate.setSnapshotEventsCollectionName("customsnapshotname");
        mongoTemplate.snapshotEventCollection();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("customsnapshotname");
    }

    @Test
    public void testDatabase_changedName() throws Exception {
        mongoTemplate.setDatabaseName("customdatabase");
        mongoTemplate.database();
        verify(mockMongo).getDB("customdatabase");

    }

}
