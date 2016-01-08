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

package org.axonframework.messaging;

import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DefaultInterceptorChainTest {

    private UnitOfWork mockUnitOfWork;
    private MessageHandler<Message<?>> mockHandler;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mockUnitOfWork = mock(UnitOfWork.class);
        mockHandler = mock(MessageHandler.class);
        when(mockHandler.handle(isA(Message.class), isA(UnitOfWork.class))).thenReturn("Result");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testChainWithDifferentProceedCalls() throws Exception {
        MessageHandlerInterceptor interceptor1 = (message, unitOfWork, interceptorChain)
                -> interceptorChain.proceed(new GenericMessage<>("testing"));
        MessageHandlerInterceptor interceptor2 = (message, unitOfWork, interceptorChain)
                -> interceptorChain.proceed();

        DefaultInterceptorChain testSubject = new DefaultInterceptorChain(asCommandMessage("original"),
                                                                          mockUnitOfWork, asList(interceptor1, interceptor2), mockHandler
        );

        String actual = (String) testSubject.proceed();

        assertSame("Result", actual);
        verify(mockHandler).handle(argThat(new BaseMatcher<Message<?>>() {
            @Override
            public boolean matches(Object o) {
                return (o instanceof Message<?>) && ((Message<?>) o).getPayload().equals("testing");
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Message with 'testing' payload");
            }
        }), isA(UnitOfWork.class));
    }
}
