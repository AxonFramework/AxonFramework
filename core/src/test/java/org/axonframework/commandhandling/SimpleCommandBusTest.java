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

package org.axonframework.commandhandling;

import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Arrays;
import java.util.HashMap;

import static org.hamcrest.core.Is.is;
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

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testDispatchCommand_HandlerSubscribed() {
        HashMap<Class<String>, MyStringCommandHandler> subscriptions = new HashMap<Class<String>, MyStringCommandHandler>();
        subscriptions.put(String.class, new MyStringCommandHandler());
        testSubject.setSubscriptions(subscriptions);
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"),
                             new CommandCallback<CommandMessage<?>>() {
                                 @Override
                                 public void onSuccess(CommandMessage<?> result) {
                                     assertEquals("Say hi!", result.getPayload());
                                 }

                                 @Override
                                 public void onFailure(Throwable cause) {
                                     cause.printStackTrace();
                                     fail("Did not expect exception");
                                 }
                             });
    }

    @Test
    public void testDispatchCommand_ImplicitUnitOfWorkIsCommittedOnReturnValue() {
        UnitOfWorkFactory spyUnitOfWorkFactory = spy(new DefaultUnitOfWorkFactory());
        testSubject.setUnitOfWorkFactory(spyUnitOfWorkFactory);
        testSubject.subscribe(String.class, new CommandHandler<String>() {
            @Override
            public Object handle(CommandMessage<String> command, UnitOfWork unitOfWork) throws Throwable {
                assertTrue(CurrentUnitOfWork.isStarted());
                assertTrue(unitOfWork.isStarted());
                assertNotNull(CurrentUnitOfWork.get());
                assertNotNull(unitOfWork);
                assertSame(CurrentUnitOfWork.get(), unitOfWork);
                return command;
            }
        });
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"),
                             new CommandCallback<CommandMessage<?>>() {
                                 @Override
                                 public void onSuccess(CommandMessage<?> result) {
                                     assertEquals("Say hi!", result.getPayload());
                                 }

                                 @Override
                                 public void onFailure(Throwable cause) {
                                     fail("Did not expect exception");
                                 }
                             });
        verify(spyUnitOfWorkFactory).createUnitOfWork();
        assertFalse(CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testDispatchCommand_ImplicitUnitOfWorkIsRolledBackOnException() {
        testSubject.subscribe(String.class, new CommandHandler<String>() {
            @Override
            public Object handle(CommandMessage<String> command, UnitOfWork unitOfWork) throws Throwable {
                assertTrue(CurrentUnitOfWork.isStarted());
                assertNotNull(CurrentUnitOfWork.get());
                throw new RuntimeException();
            }
        });
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected exception");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals(RuntimeException.class, cause.getClass());
            }
        });
        assertFalse(CurrentUnitOfWork.isStarted());
    }


    @Test
    public void testDispatchCommand_UnitOfWorkIsCommittedOnCheckedException() {
        UnitOfWorkFactory mockUnitOfWorkFactory = mock(DefaultUnitOfWorkFactory.class);
        UnitOfWork mockUnitOfWork = mock(UnitOfWork.class);
        when(mockUnitOfWorkFactory.createUnitOfWork()).thenReturn(mockUnitOfWork);

        testSubject.setUnitOfWorkFactory(mockUnitOfWorkFactory);
        testSubject.subscribe(String.class, new CommandHandler<String>() {
            @Override
            public Object handle(CommandMessage<String> command, UnitOfWork unitOfWork) throws Throwable {
                throw new Exception();
            }
        });
        testSubject.setRollbackConfiguration(new RollbackOnUncheckedExceptionConfiguration());

        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected exception");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertThat(cause, is(Exception.class));
            }
        });

        verify(mockUnitOfWork).commit();
    }


    @Test(expected = NoHandlerForCommandException.class)
    public void testDispatchCommand_NoHandlerSubscribed() throws Exception {
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"));
    }

    @Test(expected = NoHandlerForCommandException.class)
    public void testDispatchCommand_HandlerUnsubscribed() throws Exception {
        MyStringCommandHandler commandHandler = new MyStringCommandHandler();
        testSubject.subscribe(String.class, commandHandler);
        testSubject.unsubscribe(String.class, commandHandler);
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"));
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
        when(mockInterceptor1.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return mockInterceptor2.handle((CommandMessage) invocation.getArguments()[0],
                                                       (UnitOfWork) invocation.getArguments()[1],
                                                       (InterceptorChain) invocation.getArguments()[2]);
                    }
                });
        when(mockInterceptor2.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return commandHandler.handle((CommandMessage) invocation.getArguments()[0],
                                                     (UnitOfWork) invocation.getArguments()[1]);
                    }
                });
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class))).thenReturn("Hi there!");
        testSubject.subscribe(String.class, commandHandler);

        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Hi there!"), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                assertEquals("Hi there!", result);
            }

            @Override
            public void onFailure(Throwable cause) {
                throw new RuntimeException("Unexpected exception", cause);
            }
        });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(isA(CommandMessage.class),
                                                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(isA(CommandMessage.class),
                                                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(GenericCommandMessage.class), isA(UnitOfWork.class));
    }

    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    @Test
    public void testInterceptorChain_CommandHandlerThrowsException() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        final CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(mockInterceptor1.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return mockInterceptor2.handle((CommandMessage) invocation.getArguments()[0],
                                                       (UnitOfWork) invocation.getArguments()[1],
                                                       (InterceptorChain) invocation.getArguments()[2]);
                    }
                });
        when(mockInterceptor2.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @SuppressWarnings({"unchecked"})
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return commandHandler.handle((CommandMessage) invocation.getArguments()[0],
                                                     (UnitOfWork) invocation.getArguments()[1]);
                    }
                });

        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class)))
                .thenThrow(new RuntimeException("Faking failed command handling"));
        testSubject.subscribe(String.class, commandHandler);

        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Hi there!"), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected exception to be thrown");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals("Faking failed command handling", cause.getMessage());
            }
        });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(isA(CommandMessage.class),
                                                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(isA(CommandMessage.class),
                                                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler).handle(isA(GenericCommandMessage.class), isA(UnitOfWork.class));
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown", "unchecked"})
    @Test
    public void testInterceptorChain_InterceptorThrowsException() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        when(mockInterceptor1.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        return mockInterceptor2.handle((CommandMessage) invocation.getArguments()[0],
                                                       (UnitOfWork) invocation.getArguments()[1],
                                                       (InterceptorChain) invocation.getArguments()[2]);
                    }
                });
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class))).thenReturn("Hi there!");
        testSubject.subscribe(String.class, commandHandler);
        RuntimeException someException = new RuntimeException("Mocking");
        doThrow(someException).when(mockInterceptor2).handle(isA(CommandMessage.class),
                                                             isA(UnitOfWork.class),
                                                             isA(InterceptorChain.class));
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Hi there!"), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected exception to be propagated");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals("Mocking", cause.getMessage());
            }
        });
        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).handle(isA(CommandMessage.class),
                                                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(mockInterceptor2).handle(isA(CommandMessage.class),
                                                isA(UnitOfWork.class), isA(InterceptorChain.class));
        inOrder.verify(commandHandler, never()).handle(isA(CommandMessage.class), isA(UnitOfWork.class));
    }

    private static class MyStringCommandHandler implements CommandHandler<String> {

        @Override
        public Object handle(CommandMessage<String> command, UnitOfWork uow) {
            return command;
        }
    }
}
