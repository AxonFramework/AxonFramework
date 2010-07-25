/*
 * Copyright (c) 2010. Axon Framework
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

import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleCommandBusTest {

    private SimpleCommandBus testSubject;

    @Before
    public void setUp() {
        this.testSubject = new SimpleCommandBus();
    }

    @Test
    public void testDispatchCommand_HandlerSubscribed() {
        testSubject.subscribe(String.class, new MyStringCommandHandler());
        testSubject.dispatch("Say hi!", new CommandCallback<String, Object>() {
            @Override
            public void onSuccess(Object result, CommandContext context) {
                assertEquals("Say hi!", result);
            }

            @Override
            public void onFailure(Throwable cause, CommandContext context) {
                fail("Did not expect exception");
            }
        });
    }

    @Test(expected = NoHandlerForCommandException.class)
    public void testDispatchCommand_NoHandlerSubscribed() throws Exception {
        testSubject.dispatch("Say hi!");
    }

    @Test(expected = NoHandlerForCommandException.class)
    public void testDispatchCommand_HandlerUnsubscribed() throws Exception {
        MyStringCommandHandler commandHandler = new MyStringCommandHandler();
        testSubject.subscribe(String.class, commandHandler);
        testSubject.unsubscribe(String.class, commandHandler);
        testSubject.dispatch("Say hi!");
    }

    @Test
    public void testUnsubscribe_HandlerNotKnown() {
        testSubject.unsubscribe(String.class, new MyStringCommandHandler());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testInterceptorChain_CommandHandledSuccessfully() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        final CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(mockInterceptor1.handle(isA(CommandContext.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return mockInterceptor2.handle((CommandContext) invocation.getArguments()[0],
                                                       (InterceptorChain) invocation.getArguments()[1]);
                    }
                });
        when(mockInterceptor2.handle(isA(CommandContext.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return commandHandler.handle((String) commandContextFrom(invocation).getCommand());
                    }
                });
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        when(commandHandler.handle("Hi there!")).thenReturn("Hi there!");
        testSubject.subscribe(String.class, commandHandler);

        testSubject.dispatch("Hi there!", new CommandCallback<String, Object>() {
            @Override
            public void onSuccess(Object result, CommandContext<String> context) {
                assertEquals("Hi there!", result);
            }

            @Override
            public void onFailure(Throwable cause, CommandContext context) {
                throw new RuntimeException("Unexpected exception", cause);
            }
        });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(isA(CommandContext.class),
                                                isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(isA(CommandContext.class),
                                                isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle("Hi there!");
    }

    @Test
    public void testInterceptorChain_CommandHandlerThrowsException() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        final CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(mockInterceptor1.handle(isA(CommandContext.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return mockInterceptor2.handle((CommandContext) invocation.getArguments()[0],
                                                       (InterceptorChain) invocation.getArguments()[1]);
                    }
                });
        when(mockInterceptor2.handle(isA(CommandContext.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return commandHandler.handle((String) commandContextFrom(invocation).getCommand());
                    }
                });

        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        when(commandHandler.handle("Hi there!")).thenThrow(new RuntimeException("Faking failed command handling"));
        testSubject.subscribe(String.class, commandHandler);

        testSubject.dispatch("Hi there!", new CommandCallback<String, Object>() {
            @Override
            public void onSuccess(Object result, CommandContext context) {
                fail("Expected exception to be thrown");
            }

            @Override
            public void onFailure(Throwable cause, CommandContext context) {
                assertEquals("Faking failed command handling", cause.getMessage());
            }
        });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(isA(CommandContext.class),
                                                isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(isA(CommandContext.class),
                                                isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle("Hi there!");
    }

    private CommandContext commandContextFrom(InvocationOnMock invocation) {
        return ((CommandContext) invocation.getArguments()[0]);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testInterceptorChain_InterceptorThrowsException() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        when(mockInterceptor1.handle(isA(CommandContext.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return mockInterceptor2.handle((CommandContext) invocation.getArguments()[0],
                                                       (InterceptorChain) invocation.getArguments()[1]);
                    }
                });
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(commandHandler.handle("Hi there!")).thenReturn("Hi there!");
        testSubject.subscribe(String.class, commandHandler);
        RuntimeException someException = new RuntimeException("Mocking");
        doThrow(someException).when(mockInterceptor2).handle(isA(CommandContext.class), isA(InterceptorChain.class));
        testSubject.dispatch("Hi there!", new CommandCallback<String, Object>() {
            @Override
            public void onSuccess(Object result, CommandContext context) {
                fail("Expected exception to be propagated");
            }

            @Override
            public void onFailure(Throwable cause, CommandContext context) {
                assertEquals("Mocking", cause.getMessage());
            }
        });
        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(isA(CommandContext.class),
                                                isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(isA(CommandContext.class),
                                                isA(InterceptorChain.class));
        inOrder.verify(commandHandler, never()).handle("Hi there!");

    }

    private static class MyStringCommandHandler implements CommandHandler<String> {

        @Override
        public Object handle(String command) {
            return command;
        }
    }
}
