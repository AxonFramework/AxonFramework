/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * Tests to verify that Spring-generated proxy beans are also neatly proxied. Relates to issue #111 and #172
 *
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:/contexts/double_enhanced_listener.xml")
public class AnnotationCommandListenerBeanPostProcessorTest_DoubleAnnotated {

    @Autowired
    private TransactionalCommandHandler transactionalHandler;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private PlatformTransactionManager mockTransactionManager;

    @Before
    public void beforeClass() {
        reset(mockTransactionManager);
        when(mockTransactionManager.getTransaction(isA(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus(true));
    }

    @Test
    public void testInitializeProxiedInstance() throws Throwable {
        SecurityContextHolder.setContext(new SecurityContext() {
            @Override
            public Authentication getAuthentication() {
                return new TestingAuthenticationToken("Princpipal", "Credentials", "ROLE_ADMINISTRATOR");
            }

            @Override
            public void setAuthentication(Authentication authentication) {
            }
        });
        assertNotNull(transactionalHandler);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("StringCommand"));

        verify(mockTransactionManager).getTransaction(isA(TransactionDefinition.class));
        verify(mockTransactionManager).commit(isA(TransactionStatus.class));
        assertEquals(1, transactionalHandler.getInvocations());

        assertTrue("Bean doesn't implemment EventListener",
                   org.axonframework.commandhandling.CommandHandler.class.isInstance(transactionalHandler));
        ((CommandHandler<Integer>) transactionalHandler).handle(GenericCommandMessage.asCommandMessage(12), null);
        assertEquals(2, transactionalHandler.getInvocations());

        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new Object()), new VoidCallback() {
            @Override
            protected void onSuccess() {
                fail("Expected a security exception");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals(AccessDeniedException.class, cause.getClass());
            }
        });
    }
}
