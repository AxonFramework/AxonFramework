/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
public class DefaultInterceptorChainTest {

    private UnitOfWork<Message<?>> unitOfWork;
    private MessageHandler<Message<?>> mockHandler;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        unitOfWork = new DefaultUnitOfWork<>(null);
        mockHandler = mock(MessageHandler.class);
        when(mockHandler.handle(isA(Message.class))).thenReturn("Result");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testChainWithDifferentProceedCalls() throws Exception {
        MessageHandlerInterceptor interceptor1 = (unitOfWork, interceptorChain) -> {
            unitOfWork.transformMessage(m -> new GenericMessage<>("testing"));
            return interceptorChain.proceed();
        };
        MessageHandlerInterceptor interceptor2 = (unitOfWork, interceptorChain) -> interceptorChain.proceed();


        unitOfWork.transformMessage(m -> new GenericMessage<>("original"));
        DefaultInterceptorChain testSubject = new DefaultInterceptorChain(
                unitOfWork, asList(interceptor1, interceptor2), mockHandler
        );

        String actual = (String) testSubject.proceed();

        assertSame("Result", actual);
        verify(mockHandler).handle(argThat(x -> (x != null) && x.getPayload().equals("testing")));
    }
}
