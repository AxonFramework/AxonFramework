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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.Subscribable;
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

import static org.junit.Assert.*;

/**
 * Tests to verify that Spring-generated proxy beans are also neatly proxied. Relates to issue #111 and #172
 *
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:/contexts/double_enhanced_listener_javaproxy.xml")
public class AnnotationCommandListenerBeanPostProcessorTest_DoubleAnnotatedJavaProxy {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private SomeCommandHandlerInterface transactionalHandler;

    @SuppressWarnings("unchecked")
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

        assertTrue("Bean doesn't implement EventListener",
                   org.axonframework.commandhandling.CommandHandler.class.isInstance(transactionalHandler));
        assertTrue("Bean doesn't implement Subscribable",
                   Subscribable.class.isInstance(transactionalHandler));
        // this command is rejected, because security annotations are defined on the interface(d) method)
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("StringCommand"), new SecurityVerifyingCallback());
        // Security annotation on these commands are not inspected by Spring, because proxy-target-class is false.
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(1), new FailureDetectingCallback());
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new Object()), new FailureDetectingCallback());
    }

    private static class FailureDetectingCallback implements CommandCallback<Object> {

        @Override
        public void onSuccess(Object result) {
        }

        @Override
        public void onFailure(Throwable cause) {
            cause.printStackTrace();
            fail("Did not expect exception");
        }
    }

    private static class SecurityVerifyingCallback implements CommandCallback<Object> {

        @Override
        public void onSuccess(Object result) {
            fail("Did not expect result");
        }

        @Override
        public void onFailure(Throwable cause) {
            assertTrue(cause instanceof AccessDeniedException);
        }
    }
}
