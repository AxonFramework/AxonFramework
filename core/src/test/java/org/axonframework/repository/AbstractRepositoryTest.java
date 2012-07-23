package org.axonframework.repository;

import org.axonframework.domain.AbstractAggregateRoot;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.junit.*;

/**
 * @author Allard Buijze
 */
public class AbstractRepositoryTest {

    private AbstractRepository testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new AbstractRepository<JpaAggregate>(JpaAggregate.class) {
            @Override
            protected void doSave(JpaAggregate aggregate) {
            }

            @Override
            protected JpaAggregate doLoad(Object aggregateIdentifier, Long expectedVersion) {
                return new JpaAggregate();
            }

            @Override
            protected void doDelete(JpaAggregate aggregate) {
            }
        };
        DefaultUnitOfWork.startAndGet();
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAggregateTypeVerification_CorrectType() throws Exception {
        testSubject.add(new JpaAggregate("hi"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAggregateTypeVerification_WrongType() throws Exception {
        testSubject.add(new AbstractAggregateRoot() {
            @Override
            public Object getIdentifier() {
                return "1";
            }
        });
    }
}
