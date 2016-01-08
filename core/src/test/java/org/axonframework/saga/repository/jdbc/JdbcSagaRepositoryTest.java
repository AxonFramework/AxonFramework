package org.axonframework.saga.repository.jdbc;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.repository.StubSaga;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Kristian Rosenvold
 */
public class JdbcSagaRepositoryTest {

    Connection connection;

    private JdbcSagaRepository repository;

    private JDBCDataSource dataSource;

    @Before
    public void setUp() throws SQLException {
        dataSource = spy(new JDBCDataSource());
        dataSource.setUrl("jdbc:hsqldb:mem:test");

        connection = dataSource.getConnection();
        repository = new JdbcSagaRepository(dataSource, new HsqlSagaSqlSchema());
        repository.createSchema();

        reset(dataSource);
    }

    @After
    public void shutDown() throws SQLException {
        connection.createStatement().execute("SHUTDOWN");
        connection.close();
    }

    @Test
    public void testAddingAnInactiveSagaDoesntStoreIt() {
        StubSaga testSaga = new StubSaga("test1");
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        testSaga.end();

        repository.add(testSaga);
        Set<String> actual = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(0, actual.size());
        Object actualSaga = repository.load("test1");
        assertNull(actualSaga);
    }

    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaFound() {
        StubSaga testSaga = new StubSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.add(testSaga);
        repository.add(otherTestSaga);
        Set<String> actual = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(1, actual.size());
        assertEquals("test1", actual.iterator().next());
    }

    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_NoSagaFound() {
        StubSaga testSaga = new StubSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        Set<String> actual = repository.find(InexistentSaga.class, new AssociationValue("key", "value"));
        assertTrue("Didn't expect any sagas", actual.isEmpty());
    }

    @Test
    public void testLoadSagaOfDifferentTypesWithSameAssociationValue_SagaDeleted() {
        StubSaga testSaga = new StubSaga("test1");
        MyOtherTestSaga otherTestSaga = new MyOtherTestSaga("test2");
        repository.add(testSaga);
        repository.add(otherTestSaga);
        testSaga.registerAssociationValue(new AssociationValue("key", "value"));
        otherTestSaga.registerAssociationValue(new AssociationValue("key", "value"));
        testSaga.end();
        repository.commit(testSaga);
        Set<String> actual = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertTrue("Didn't expect any sagas", actual.isEmpty());
    }

    @Test
    public void testLoadSaga_NotFound() {
        assertNull(repository.load("123456"));
    }

    @Test
    public void testLoadSaga_AssociationValueRemoved() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        saga.registerAssociationValue(new AssociationValue("key", "value"));
        repository.storeSaga(saga);
        repository.storeAssociationValue(new AssociationValue("key", "value"),
                                         identifier, saga.getClass().getName());
        StubSaga loaded = (StubSaga) repository.load(identifier);
        loaded.removeAssociationValue("key", "value");
        repository.commit(loaded);
        Set<String> found = repository.find(StubSaga.class, new AssociationValue("key", "value"));
        assertEquals(0, found.size());
    }

    @Test
    public void testSaveSaga() {
        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        repository.storeSaga(saga);
        StubSaga loaded = (StubSaga) repository.load(identifier);
        repository.commit(loaded);

        Saga load = repository.load(identifier);
        assertNotSame(loaded, load);
    }

    @Test
    public void testSaveSaga_InsideUnitOfWorkWithoutConnection() throws SQLException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet(null);

        String identifier = UUID.randomUUID().toString();
        StubSaga saga = new StubSaga(identifier);
        repository.storeSaga(saga);
        StubSaga loaded = (StubSaga) repository.load(identifier);
        repository.commit(loaded);

        Saga load = repository.load(identifier);
        assertNotSame(loaded, load);

        uow.commit();

        // the datasource should only have been asked once
        verify(dataSource, times(1)).getConnection();
    }


    public static class MyOtherTestSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -1562911263884220240L;

        public MyOtherTestSaga(String identifier) {
            super(identifier);
        }

        public void registerAssociationValue(AssociationValue associationValue) {
            associateWith(associationValue);
        }
    }

    private class InexistentSaga implements Saga {

        @Override
        public String getSagaIdentifier() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public AssociationValues getAssociationValues() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public void handle(EventMessage event) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public boolean isActive() {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}
