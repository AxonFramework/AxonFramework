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

package org.axonframework.eventhandling.amqp.spring;

import org.aopalliance.aop.Advice;
import org.junit.*;
import org.junit.runner.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * @author Allard Buijze
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ListenerContainerFactory.class, SimpleMessageListenerContainer.class})
public class ListenerContainerFactoryTest {

    private ExtendedMessageListenerContainer mockContainer;
    private ListenerContainerFactory testSubject;
    private ConnectionFactory mockConnectionFactory;

    @Before
    public void setUp() throws Exception {
        mockConnectionFactory = mock(ConnectionFactory.class);
        mockContainer = PowerMockito.mock(ExtendedMessageListenerContainer.class);
        whenNew(ExtendedMessageListenerContainer.class).withNoArguments().thenReturn(mockContainer);
        testSubject = new ListenerContainerFactory();
        testSubject.setConnectionFactory(mockConnectionFactory);
        PowerMockito.doNothing().when(mockContainer).afterPropertiesSet();
    }

    @Test
    public void testListenerContainerKeepsDefaults() throws Exception {
        SimpleMessageListenerContainer actual = testSubject.createContainer(new SpringAMQPConsumerConfiguration());

        // just to make sure PowerMock was correctly configured
        assertSame(mockContainer, actual);

        verify(mockContainer).setConnectionFactory(mockConnectionFactory);
        verify(mockContainer).afterPropertiesSet();
        PowerMockito.verifyNoMoreInteractions(mockContainer);
    }

    @Test
    public void testListenerContainerFullyConfigured() {
        SpringAMQPConsumerConfiguration config = new SpringAMQPConsumerConfiguration();

        config.setAcknowledgeMode(AcknowledgeMode.AUTO);
        Advice[] adviceChain = new Advice[0];
        config.setAdviceChain(adviceChain);
        config.setConcurrentConsumers(5);
        ConnectionFactory mockConnectionFactory = mock(ConnectionFactory.class);
        testSubject.setConnectionFactory(mockConnectionFactory);
        ErrorHandler mockErrorHandler = mock(ErrorHandler.class);
        config.setErrorHandler(mockErrorHandler);
        MessagePropertiesConverter mockMessagePropertiesConverter = mock(MessagePropertiesConverter.class);
        config.setMessagePropertiesConverter(mockMessagePropertiesConverter);
        config.setPrefetchCount(6);
        config.setReceiveTimeout(6000L);
        config.setRecoveryInterval(6500L);
        config.setShutdownTimeout(3000L);
        config.setExclusive(false);
        Executor mockTaskExecutor = mock(Executor.class);
        config.setTaskExecutor(mockTaskExecutor);
        DefaultTransactionAttribute transactionAttribute = new DefaultTransactionAttribute();
        config.setTransactionAttribute(transactionAttribute);
        PlatformTransactionManager mockTransactionManager = mock(PlatformTransactionManager.class);
        config.setTransactionManager(mockTransactionManager);
        config.setTxSize(100);

        SimpleMessageListenerContainer actual = testSubject.createContainer(config);

        // just to make sure PowerMock was correctly configured
        assertSame(mockContainer, actual);

        verify(mockContainer).setAcknowledgeMode(AcknowledgeMode.AUTO);
        verify(mockContainer).setAdviceChain(adviceChain);
        verify(mockContainer).setConcurrentConsumers(5);
        verify(mockContainer).setConnectionFactory(mockConnectionFactory);
        verify(mockContainer).setErrorHandler(mockErrorHandler);
        verify(mockContainer).setMessagePropertiesConverter(mockMessagePropertiesConverter);
        verify(mockContainer).setPrefetchCount(6);
        verify(mockContainer).setReceiveTimeout(6000);
        verify(mockContainer).setRecoveryInterval(6500);
        verify(mockContainer).setShutdownTimeout(3000);
        verify(mockContainer).setExclusive(false);
        verify(mockContainer).setTaskExecutor(mockTaskExecutor);
        verify(mockContainer).setTransactionAttribute(transactionAttribute);
        verify(mockContainer).setTransactionManager(mockTransactionManager);
        verify(mockContainer).setChannelTransacted(true);
        verify(mockContainer).setTxSize(100);
        verify(mockContainer).afterPropertiesSet();
        PowerMockito.verifyNoMoreInteractions(mockContainer);
    }
}
