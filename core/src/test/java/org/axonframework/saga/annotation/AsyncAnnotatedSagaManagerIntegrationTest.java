package org.axonframework.saga.annotation;

import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.common.annotation.SpringBeanParameterResolverFactory;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.async.RetryPolicy;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.annotation.AsyncAnnotatedSagaManagerTest.AddAssociationEvent;
import org.axonframework.saga.annotation.AsyncAnnotatedSagaManagerTest.OptionallyCreateNewEvent;
import org.axonframework.saga.repository.jpa.JpaSagaRepository;
import org.axonframework.unitofwork.SpringTransactionManager;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(classes = AsyncAnnotatedSagaManagerIntegrationTest.Context.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class AsyncAnnotatedSagaManagerIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JpaSagaRepository jpaSagaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AsyncAnnotatedSagaManager asyncSagaManager;

    @Autowired
    private InvocationLogger invocationLogger;

    @Autowired
    private ExecutorService executorService;

    @Before
    public void setUp() throws Exception {
        // the serialized form of the Saga exceeds the default length of a blob.
        // So we must alter the table to prevent data truncation
        new TransactionTemplate(transactionManager)
                .execute(new TransactionCallbackWithoutResult() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        entityManager.createNativeQuery(
                                "ALTER TABLE SagaEntry ALTER COLUMN serializedSaga VARBINARY(1024)")
                                     .executeUpdate();
                    }
                });
        invocationLogger.reset();
    }

    @Test
    public void testRetryMechanismWhenRepositoryFails() throws Exception {
        final AtomicInteger failOnSave = new AtomicInteger();
        final AtomicInteger failOnLoad = new AtomicInteger();
        doAnswer(new Answer<Object>() {
                     @Override
                     public Object answer(InvocationOnMock invocation) throws Throwable {
                         if (failOnSave.get() <= 0) {
                             return invocation.callRealMethod();
                         }
                         failOnSave.decrementAndGet();
                         // trigger a failure
                         TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
                         try {
                             throw new RuntimeException("Faking some JDBC exception on save");
                         } finally {
                             tx.setRollbackOnly();
                             transactionManager.rollback(tx);
                         }
                     }
                 }

        ).when(jpaSagaRepository).commit(any(Saga.class));
        doAnswer(new Answer<Object>() {
                     @Override
                     public Object answer(InvocationOnMock invocation) throws Throwable {
                         if (failOnLoad.get() <= 0) {
                             return invocation.callRealMethod();
                         }
                         failOnLoad.decrementAndGet();
                         // trigger a failure
                         TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
                         try {
                             throw new RuntimeException("Faking some JDBC exception on load");
                         } finally {
                             tx.setRollbackOnly();
                             transactionManager.rollback(tx);
                         }
                     }
                 }

        ).when(jpaSagaRepository).find(any(Class.class), any(AssociationValue.class));

        EventMessage<Object> createEvent = asEventMessage(new OptionallyCreateNewEvent("1"));
        EventMessage<Object> updateEvent = asEventMessage(new AsyncAnnotatedSagaManagerTest.UpdateEvent("1"));
        EventMessage<Object> addAssociationEvent = asEventMessage(new AddAssociationEvent("1", "2"));
        EventMessage<Object> optionallyCreateNew = asEventMessage(new OptionallyCreateNewEvent("2"));
        EventMessage<Object> deleteEvent = asEventMessage(new AsyncAnnotatedSagaManagerTest.DeleteEvent("2"));

        failOnSave.set(5);
        asyncSagaManager.handle(createEvent);
        asyncSagaManager.handle(updateEvent);
        asyncSagaManager.handle(addAssociationEvent);
        failOnLoad.set(5);
        asyncSagaManager.handle(optionallyCreateNew);
        asyncSagaManager.handle(asEventMessage(new AsyncAnnotatedSagaManagerTest.UpdateEvent("notExist")));
        asyncSagaManager.handle(asEventMessage(new AsyncAnnotatedSagaManagerTest.UpdateEvent("notExist")));

        asyncSagaManager.handle(asEventMessage(new AsyncAnnotatedSagaManagerTest.UpdateEvent("notExist")));
        Thread.sleep(10);
        asyncSagaManager.handle(deleteEvent);

        while (failOnSave.get() > 0 || failOnLoad.get() > 0) {
            Thread.sleep(100);
        }

        asyncSagaManager.stop();
        executorService.shutdown();
        executorService.awaitTermination(20, TimeUnit.SECONDS);

//        verify(jpaSagaRepository, atLeast(6)).commit(any(Saga.class));

        reset(jpaSagaRepository);
        Set<String> associations = jpaSagaRepository.find(AsyncAnnotatedSagaManagerTest.StubAsyncSaga.class,
                                                          new AssociationValue("association", "1"));
        assertEquals(0, associations.size());
        assertEquals(5, invocationLogger.getEvents().size());

    }

    @ImportResource({"/META-INF/spring/db-context.xml"})
    @Configuration
    public static class Context {

        @PersistenceContext
        private EntityManager entityManager;

        @Bean
        public AsyncAnnotatedSagaManager sagaManager(ParameterResolverFactory parameterResolverFactory,
                                                     PlatformTransactionManager txManager,
                                                     ExecutorService executorService) {
            final AsyncAnnotatedSagaManager sagaManager = new AsyncAnnotatedSagaManager(
                    parameterResolverFactory,
                    AsyncAnnotatedSagaManagerTest.StubAsyncSaga.class);
            sagaManager.setErrorHandler(new ErrorHandler() {
                @Override
                public RetryPolicy onErrorPreparing(Class<? extends Saga> sagaType, EventMessage<?> publishedEvent,
                                                    int invocationCount, Exception e) {
                    return RetryPolicy.retryAfter(100, TimeUnit.MILLISECONDS);
                }

                @Override
                public RetryPolicy onErrorInvoking(Saga saga, EventMessage publishedEvent, int invocationCount,
                                                   Exception e) {
                    return RetryPolicy.retryAfter(100, TimeUnit.MILLISECONDS);
                }
            });
            sagaManager.setBufferSize(128);
            sagaManager.setProcessorCount(1);
            sagaManager.setSagaRepository(jpaSagaRepository());
            sagaManager.setTransactionManager(new SpringTransactionManager(txManager));
            sagaManager.setExecutor(executorService);
            sagaManager.start();
            return sagaManager;
        }

        @Bean
        public ParameterResolverFactory parameterResolverFactory() {
            return new SpringBeanParameterResolverFactory();
        }

        @Bean
        public ExecutorService executorService() {
            return Executors.newCachedThreadPool();
        }

        @Bean
        public JpaSagaRepository jpaSagaRepository() {
            return spy(new JpaSagaRepository(new SimpleEntityManagerProvider(entityManager)));
        }

        @Bean
        public EventBus eventBus() {
            return mock(EventBus.class);
        }

        @Bean
        public InvocationLogger invocationLogger() {
            return new InvocationLogger();
        }
    }
}
