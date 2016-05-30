/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.mongo;

import com.mongodb.DB;
import com.mongodb.Mongo;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author Jettro Coenradie
 */
public class DefaultMongoTemplateTest {

    private DefaultMongoTemplate mongoTemplate;
    private Mongo mockMongo;
    private DB mockDb;

    @Before
    public void createFixtures() {
        mockMongo = mock(Mongo.class);
        mockDb = mock(DB.class);
        when(mockMongo.getDB(anyString())).thenReturn(mockDb);
        mongoTemplate = new DefaultMongoTemplate(mockMongo);
    }

    @Test
    public void testDomainEvents() throws Exception {
        mongoTemplate.eventCollection();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("domainevents");
    }

    @Test
    public void testSnapshotEvents() throws Exception {

        mongoTemplate.snapshotCollection();

        verify(mockMongo).getDB("axonframework");
        verify(mockDb).getCollection("snapshotevents");
    }

    @Test
    public void testAuthentication() {
        char[] password = "pw".toCharArray();
        mongoTemplate = new DefaultMongoTemplate(mockMongo, "dbName", "events", "snapshots", "username", password);
        mongoTemplate.eventCollection();

        verify(mockDb).authenticate("username", password);
    }

    @Test
    public void testCustomProvidedNames() throws Exception {
        mongoTemplate = new DefaultMongoTemplate(mockMongo,
                                                 "customdatabase",
                                                 "customevents",
                                                 "customsnapshots",
                                                 null,
                                                 null);

        mongoTemplate.eventCollection();
        verify(mockDb).getCollection("customevents");

        mongoTemplate.snapshotCollection();
        verify(mockDb).getCollection("customevents");
    }
}
