/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
class DefaultInterceptorChainTest {

    private LegacyUnitOfWork<Message<?>> unitOfWork;
    private MessageHandler<Message<?>, Message<Object>> mockHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        unitOfWork = new LegacyDefaultUnitOfWork<>(null);
        mockHandler = mock(MessageHandler.class);
        when(mockHandler.handleSync(isA(Message.class), any())).thenReturn("Result");
    }

    @Test
    @SuppressWarnings("unchecked")
    void chainWithDifferentProceedCalls() throws Exception {
        MessageHandlerInterceptor interceptor1 = (unitOfWork, context, interceptorChain) -> {
            unitOfWork.transformMessage(m -> new GenericMessage<>(
                    new MessageType("message"), "testing"
            ));
            return interceptorChain.proceedSync(context);
        };
        MessageHandlerInterceptor interceptor2 = (unitOfWork, context, interceptorChain) -> interceptorChain.proceedSync(
                context);


        unitOfWork.transformMessage(m -> new GenericMessage<>(
                new MessageType("message"), "original"
        ));
        DefaultInterceptorChain testSubject = new DefaultInterceptorChain(
                unitOfWork, asList(interceptor1, interceptor2), mockHandler
        );

        String actual = (String) testSubject.proceedSync(StubProcessingContext.forUnitOfWork(unitOfWork));

        assertSame("Result", actual);
        verify(mockHandler).handleSync(argThat(x -> (x != null) && x.payload().equals("testing")), any());
    }
}
