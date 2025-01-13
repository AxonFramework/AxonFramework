package org.axonframework.integrationtests.eventsourcing.eventstore.jpa;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.AggregateBasedStorageEngineTestSuite;
import org.axonframework.eventsourcing.eventstore.jpa.LegacyJpaEventStorageEngine;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(classes = JpaEmbeddedEventStoreTest.TestContext.class)
class LegacyJpaEventStorageEngineTest extends AggregateBasedStorageEngineTestSuite<LegacyJpaEventStorageEngine> {

    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private EntityManagerProvider entityManagerProvider;

    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionManager = new SpringTransactionManager(platformTransactionManager);
    }

    @Override
    protected LegacyJpaEventStorageEngine buildStorageEngine() {
        var testSerializer = TestSerializer.JACKSON.getSerializer();
        return new LegacyJpaEventStorageEngine(entityManagerProvider,
                                               transactionManager,
                                               testSerializer,
                                               testSerializer);
    }

    @Override
    protected EventMessage<String> convertPayload(EventMessage<?> original) {
        return null;
    }
}