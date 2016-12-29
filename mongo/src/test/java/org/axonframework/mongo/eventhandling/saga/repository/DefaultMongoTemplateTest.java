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

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class DefaultMongoTemplateTest {

    private DefaultMongoTemplate mongoTemplate;
    private MongoClient mockMongo;
    private MongoDatabase mockDb;

    @Before
    public void createFixtures() {
        mockMongo = mock(MongoClient.class);
        mockDb = mock(MongoDatabase.class);
        when(mockMongo.getDatabase(anyString())).thenReturn(mockDb);
        mongoTemplate = new DefaultMongoTemplate(mockMongo);
    }

    @Test
    public void testSagas() throws Exception {
        mongoTemplate.sagaCollection();

        verify(mockMongo).getDatabase("axonframework");
        verify(mockDb).getCollection("sagas");
    }

    @Test
    public void testCustomProvidedNames() throws Exception {
        mongoTemplate = new DefaultMongoTemplate(mockMongo,
                                                 "customdatabase",
                                                 "customsagas");

        mongoTemplate.sagaCollection();
        verify(mockDb).getCollection("customsagas");
    }
}
