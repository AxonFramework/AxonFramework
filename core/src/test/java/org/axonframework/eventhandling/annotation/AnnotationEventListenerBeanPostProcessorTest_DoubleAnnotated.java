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

package org.axonframework.eventhandling.annotation;

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests to verify that Spring-generated proxy beans are also neatly proxied. Relates to issue #111
 *
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:/contexts/double_enhanced_listener.xml")
public class AnnotationEventListenerBeanPostProcessorTest_DoubleAnnotated {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TransactionalListener transactionalListener;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private PlatformTransactionManager mockTransactionManager;

    @Before
    public void beforeClass() {
        reset(mockTransactionManager);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus(true));
    }

    @Test
    public void testInitializeProxiedInstance() {
        assertNotNull(transactionalListener);
        eventBus.publish(new GenericEventMessage<>(new Object()));

        verify(mockTransactionManager).getTransaction(isA(TransactionDefinition.class));
        verify(mockTransactionManager).commit(isA(TransactionStatus.class));
        assertEquals(1, transactionalListener.getInvocations());

        assertTrue("Bean doesn't implement EventListener", EventListener.class.isInstance(transactionalListener));
        ((EventListener) transactionalListener).handle(new GenericEventMessage<>(new Object()));
        assertEquals(2, transactionalListener.getInvocations());
    }
}
