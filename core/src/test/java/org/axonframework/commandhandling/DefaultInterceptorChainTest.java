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

package org.axonframework.commandhandling;

import org.axonframework.unitofwork.UnitOfWork;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.*;

import static java.util.Arrays.asList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DefaultInterceptorChainTest {

    private UnitOfWork mockUnitOfWork;
    private CommandHandler<?> mockCommandHandler;

    @Before
    public void setUp() throws Throwable {
        mockUnitOfWork = mock(UnitOfWork.class);
        mockCommandHandler = mock(CommandHandler.class);
        when(mockCommandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class))).thenReturn("Result");
    }

    @Test
    public void testChainWithDifferentProceedCalls() throws Throwable {
        CommandHandlerInterceptor interceptor1 = new CommandHandlerInterceptor() {
            @Override
            public Object handle(CommandMessage<?> commandMessage, UnitOfWork unitOfWork,
                                 InterceptorChain interceptorChain)
                    throws Throwable {
                return interceptorChain.proceed(GenericCommandMessage.asCommandMessage("testing"));
            }
        };
        CommandHandlerInterceptor interceptor2 = new CommandHandlerInterceptor() {
            @Override
            public Object handle(CommandMessage<?> commandMessage, UnitOfWork unitOfWork,
                                 InterceptorChain interceptorChain)
                    throws Throwable {
                return interceptorChain.proceed();
            }
        };

        DefaultInterceptorChain testSubject = new DefaultInterceptorChain(asCommandMessage("original"),
                                                                          mockUnitOfWork,
                                                                          mockCommandHandler,
                                                                          asList(interceptor1, interceptor2));

        String actual = (String) testSubject.proceed();

        assertSame("Result", actual);
        verify(mockCommandHandler).handle(argThat(new BaseMatcher<CommandMessage>() {
            @Override
            public boolean matches(Object o) {
                return (o instanceof CommandMessage) && ((CommandMessage) o).getPayload().equals("testing");
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Command with 'testing' payload");
            }
        }), isA(UnitOfWork.class));
    }
}
