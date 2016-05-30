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

package org.axonframework.eventhandling.saga.repository.jdbc;

import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventhandling.saga.AssociationValuesImpl;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.StubSaga;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

/**
 * @author Kristian Rosenvold
 */
public class JdbcSagaStoreTest {

    private Connection connection;

    private JdbcSagaStore testSubject;

    private JDBCDataSource dataSource;

    @Before
    public void setUp() throws SQLException {
        dataSource = spy(new JDBCDataSource());
        dataSource.setUrl("jdbc:hsqldb:mem:test");

        connection = dataSource.getConnection();
        testSubject = new JdbcSagaStore(dataSource, new HsqlSagaSqlSchema());
        testSubject.createSchema();

        reset(dataSource);
    }

    @After
    public void shutDown() throws SQLException {
        connection.createStatement().execute("SHUTDOWN");
        connection.close();
    }

    @Test
    public void testInsertUpdateAndLoadSaga() throws Exception {
        StubSaga saga = new StubSaga();
        Set<AssociationValue> associationValues = singleton(new AssociationValue("key", "value"));
        testSubject.insertSaga(StubSaga.class, "123", saga, null, associationValues);
        testSubject.updateSaga(StubSaga.class, "123", saga, null, new AssociationValuesImpl(associationValues));

        SagaStore.Entry<StubSaga> entry = testSubject.loadSaga(StubSaga.class, "123");
        assertNotNull(entry);
        assertNotNull(entry.saga());
        assertEquals(associationValues, entry.associationValues());
    }

    @Test
    public void testLoadSaga_NotFound() throws Exception {
        assertNull(testSubject.loadSaga(StubSaga.class, "123456"));
    }

    @Test
    public void testLoadSagaByAssociationValue() throws Exception {
        AssociationValues associationsValues = new AssociationValuesImpl(singleton(new AssociationValue("key", "value")));
        testSubject.insertSaga(StubSaga.class, "123", new StubSaga(), null, associationsValues.asSet());
        testSubject.insertSaga(StubSaga.class, "456", new StubSaga(), null, singleton(new AssociationValue("key", "value2")));

        associationsValues.add(new AssociationValue("key", "value2"));
        testSubject.updateSaga(StubSaga.class, "123", new StubSaga(), null, associationsValues);
        associationsValues.commit();

        associationsValues.remove(new AssociationValue("key", "value2"));
        testSubject.updateSaga(StubSaga.class, "123", new StubSaga(), null, associationsValues);
        associationsValues.commit();

        Set<String> actual = testSubject.findSagas(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(singleton("123"), actual);
    }
}
