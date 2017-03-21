package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.Id;
import javax.persistence.Version;

import static org.junit.Assert.assertEquals;

public final class NullRepositoryTest {

    private NullRepository<StubAggregate> testSubject;
    private String                        aggregateId;
    private StubAggregate                 aggregate;

    @Before
    public void setUp() {
        DefaultUnitOfWork.startAndGet(null);
        testSubject = new NullRepository<>(StubAggregate.class, null);
        aggregateId = "123";
        aggregate = new StubAggregate(aggregateId);
    }

    @After
    public void cleanUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testNewAggregate() throws Exception {
        AnnotatedAggregate<StubAggregate> newInstance = testSubject.newInstance(() -> aggregate);
        assertEquals(aggregate, newInstance.getAggregateRoot());
    }

    @Test(expected = RuntimeException.class)
    public void testLoadAggregate() {
        testSubject.load(aggregateId);
    }

    @Test(expected = RuntimeException.class)
    public void testLoadAggregate_WithVersion() {
        testSubject.load(aggregateId, 2L);
    }

    @Test
    public void testPersistAggregate() throws Exception {
        testSubject.doSave(testSubject.newInstance(() -> aggregate));
    }

    @Test
    public void testDeleteAggregate() throws Exception {
        testSubject.doDelete(testSubject.newInstance(() -> aggregate));
    }

    private class StubAggregate {
        @Id
        private final String identifier;

        @AggregateVersion
        @Version
        private long version;

        private StubAggregate(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }
}
