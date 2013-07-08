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

package org.axonframework.contextsupport.spring;

import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerBeanPostProcessor;
import org.axonframework.saga.AbstractSagaManager;
import org.axonframework.saga.SagaFactory;
import org.axonframework.saga.SagaManager;
import org.axonframework.saga.annotation.AsyncAnnotatedSagaManager;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class AnnotationConfigurationBeanDefinitionParserTest {

    @SuppressWarnings({"SpringJavaAutowiringInspection"})
    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Autowired
    private SagaFactory sagaFactory;
    @Autowired
    @Qualifier("mockTransactionManager")
    private PlatformTransactionManager tm;
    @Autowired
    @Qualifier("transactionManager")
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ThreadPoolTaskExecutor te;

    @PersistenceContext
    private EntityManager entityManager;

    @Before
    public void startup() {
        reset(sagaFactory, tm);
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.createQuery("DELETE FROM SagaEntry se").executeUpdate();
                entityManager.createQuery("DELETE FROM AssociationValueEntry se").executeUpdate();
            }
        });
    }

    @Test
    public void testParseInternalElementParserContext() {
        BeanDefinition eventListenerDefinition = beanFactory.getBeanDefinition(
                "__axon-annotation-event-listener-bean-post-processor");
        assertNotNull("Event listener bean post processor not defined", eventListenerDefinition);
        assertNull("Event bus should not be defined explicitly",
                   eventListenerDefinition.getPropertyValues().getPropertyValue("eventBus"));

        AnnotationEventListenerBeanPostProcessor processor = beanFactory.getBean(
                "__axon-annotation-event-listener-bean-post-processor",
                AnnotationEventListenerBeanPostProcessor.class);
        assertNotNull(processor);

        BeanDefinition commandHandlerDefinition = beanFactory.getBeanDefinition(
                "__axon-annotation-command-handler-bean-post-processor");
        assertNotNull("Event listener bean post processor not defined", commandHandlerDefinition);
        PropertyValue propertyValue = commandHandlerDefinition.getPropertyValues().getPropertyValue("commandBus");
        assertNotNull("Executor not defined", propertyValue);
        Object value = propertyValue.getValue();
        assertTrue("Wrong property value", RuntimeBeanReference.class.isInstance(value));
        RuntimeBeanReference beanReference = (RuntimeBeanReference) value;
        assertEquals("commandBus-embedded-ref", beanReference.getBeanName());
        assertNull("Event bus should not be defined explicitly",
                   commandHandlerDefinition.getPropertyValues().getPropertyValue("eventBus"));

        AnnotationCommandHandlerBeanPostProcessor handler = beanFactory.getBean(
                "__axon-annotation-command-handler-bean-post-processor",
                AnnotationCommandHandlerBeanPostProcessor.class);
        assertNotNull(handler);
    }

    @Test
    @DirtiesContext
    public void testSagaManagerWiring() {
        // this part should prove correct autowiring of the saga manager
        SagaManager sagaManager = beanFactory.getBean("sagaManager", SagaManager.class);
        assertNotNull(sagaManager);

        // This type is found using component scanning
        when(sagaFactory.supports(StubSaga.class)).thenReturn(true);
        when(sagaFactory.createSaga(StubSaga.class)).thenReturn(new StubSaga());

        String identifier = UUID.randomUUID().toString();
        sagaManager.handle(new GenericDomainEventMessage<SimpleEvent>(identifier, (long) 0,
                                                                      new SimpleEvent(
                                                                              identifier), MetaData.emptyInstance()));

        verify(sagaFactory).createSaga(StubSaga.class);
    }

    @Test
    @DirtiesContext
    public void testSagaManagerWiring_suppressExceptionsFalse() throws NoSuchFieldException, IllegalAccessException {
        // this part should prove correct autowiring of the saga manager
        AbstractSagaManager sagaManager = beanFactory.getBean("sagaManagerNotSuppressingExceptions", AbstractSagaManager.class);
        assertNotNull(sagaManager);

        final Field field = AbstractSagaManager.class.getDeclaredField("suppressExceptions");
        field.setAccessible(true);
        final Boolean suppressExceptions = (Boolean) field.get(sagaManager);
        assertFalse(suppressExceptions);
    }

    @Test
    @DirtiesContext
    public void testAsyncTransactionalSagaManagerWiring() throws InterruptedException {
        // this part should prove correct autowiring of the saga manager
        AsyncAnnotatedSagaManager sagaManager = beanFactory.getBean("asyncTransactionalSagaManager",
                                                                    AsyncAnnotatedSagaManager.class);
        assertNotNull(sagaManager);

        when(sagaFactory.supports(StubSaga.class)).thenReturn(true);
        when(sagaFactory.createSaga(StubSaga.class)).thenReturn(new StubSaga());

        String identifier = UUID.randomUUID().toString();
        sagaManager.handle(new GenericDomainEventMessage<SimpleEvent>(identifier, (long) 0,
                                                                      new SimpleEvent(
                                                                              identifier), MetaData.emptyInstance()));
        sagaManager.unsubscribe();
        verify(sagaFactory).createSaga(eq(StubSaga.class));
        sagaManager.stop();

        assertEquals("Saga was never stored in the saga repository",
                     1L, entityManager.createQuery("SELECT count(se) FROM SagaEntry se").getSingleResult());
    }

    @Test
    @DirtiesContext
    public void testAsyncSagaManagerWiring() throws InterruptedException {

        // this part should prove correct autowiring of the saga manager
        SagaManager sagaManager = beanFactory.getBean("asyncSagaManager", SagaManager.class);
        assertNotNull(sagaManager);
        assertNotNull(tm);

        when(sagaFactory.supports(StubSaga.class)).thenReturn(true);
        when(sagaFactory.createSaga(StubSaga.class)).thenReturn(new StubSaga());

        String identifier = UUID.randomUUID().toString();
        sagaManager.handle(new GenericDomainEventMessage<SimpleEvent>(identifier, (long) 0,
                                                                      new SimpleEvent(
                                                                              identifier), MetaData.emptyInstance()));
        Thread.sleep(250);
        te.shutdown();
        te.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);
        verify(sagaFactory).createSaga(StubSaga.class);
        verify(tm, never()).getTransaction(isA(TransactionDefinition.class));
    }
}
