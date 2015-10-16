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

import org.axonframework.common.Subscription;
import org.axonframework.messaging.unitofwork.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.HashMap;

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
        HashMap<String, MyStringCommandHandler> subscriptions = new HashMap<>();
        subscriptions.put(String.class.getName(), new MyStringCommandHandler());
        testSubject.setSubscriptions(subscriptions);
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"),
                             new CommandCallback<Object, CommandMessage<?>>() {
                                 @Override
                                 public void onSuccess(CommandMessage<?> command, CommandMessage<?> result) {
                                     assertEquals("Say hi!", result.getPayload());
                                 }

                                 @Override
                                 public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                     cause.printStackTrace();
                                     fail("Did not expect exception");
                                 }
                             });
    }

    @Test
    public void testDispatchCommand_ImplicitUnitOfWorkIsCommittedOnReturnValue() {
        UnitOfWorkFactory spyUnitOfWorkFactory = spy(new DefaultUnitOfWorkFactory());
        testSubject.setUnitOfWorkFactory(spyUnitOfWorkFactory);
        testSubject.subscribe(String.class.getName(), (command, unitOfWork) -> {
            assertTrue(CurrentUnitOfWork.isStarted());
            assertTrue(unitOfWork.isActive());
            assertNotNull(CurrentUnitOfWork.get());
            assertNotNull(unitOfWork);
            assertSame(CurrentUnitOfWork.get(), unitOfWork);
            return command;
        });
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"),
                             new CommandCallback<Object, CommandMessage<?>>() {
                                 @Override
                                 public void onSuccess(CommandMessage<?> commandMessage, CommandMessage<?> result) {
                                     assertEquals("Say hi!", result.getPayload());
                                 }

                                 @Override
                                 public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                     fail("Did not expect exception");
                                 }
                             });
        verify(spyUnitOfWorkFactory).createUnitOfWork(any(GenericCommandMessage.class));
        assertFalse(CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testDispatchCommand_ImplicitUnitOfWorkIsRolledBackOnException() {
        testSubject.subscribe(String.class.getName(), (command, unitOfWork) -> {
            assertTrue(CurrentUnitOfWork.isStarted());
            assertNotNull(CurrentUnitOfWork.get());
            throw new RuntimeException();
        });
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"), new CommandCallback<Object, Object>() {
            @Override
            public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                fail("Expected exception");
            }

            @Override
            public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                assertEquals(RuntimeException.class, cause.getClass());
            }
        });
        assertFalse(CurrentUnitOfWork.isStarted());
    }


    @Test
    public void testDispatchCommand_UnitOfWorkIsCommittedOnCheckedException() throws Exception {
        UnitOfWorkFactory mockUnitOfWorkFactory = mock(DefaultUnitOfWorkFactory.class);
        UnitOfWork mockUnitOfWork = spy(new DefaultUnitOfWork(null));
        when(mockUnitOfWorkFactory.createUnitOfWork(any())).thenReturn(mockUnitOfWork);

        testSubject.setUnitOfWorkFactory(mockUnitOfWorkFactory);
        testSubject.subscribe(String.class.getName(), (command, unitOfWork) -> {
            throw new Exception();
        });
        testSubject.setRollbackConfiguration(RollbackConfigurationType.UNCHECKED_EXCEPTIONS);

        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Say hi!"), new CommandCallback<Object, Object>() {
            @Override
            public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                fail("Expected exception");
            }

            @Override
            public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                assertEquals(cause.getClass(), Exception.class);
            }
        });

        verify(mockUnitOfWork).commit();
    }


    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchCommand_NoHandlerSubscribed() {
        final CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        final CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("test");
        testSubject.dispatch(command, callback);

        verify(callback).onFailure(eq(command), isA(NoHandlerForCommandException.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDispatchCommand_HandlerUnsubscribed() throws Exception {
        final CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        MyStringCommandHandler commandHandler = new MyStringCommandHandler();
        Subscription subscription = testSubject.subscribe(String.class.getName(), commandHandler);
        subscription.close();
        final CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("Say hi!");
        testSubject.dispatch(command, callback);

        verify(callback).onFailure(eq(command), isA(NoHandlerForCommandException.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testInterceptorChain_CommandHandledSuccessfully() throws Exception {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        final CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(mockInterceptor1.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockInterceptor2.handle((CommandMessage) invocation.getArguments()[0],
                                                                  (UnitOfWork) invocation.getArguments()[1],
                                                                  (InterceptorChain) invocation.getArguments()[2]));
        when(mockInterceptor2.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> commandHandler.handle((CommandMessage) invocation.getArguments()[0],
                                                                (UnitOfWork) invocation.getArguments()[1]));
        testSubject.setHandlerInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class))).thenReturn("Hi there!");
        testSubject.subscribe(String.class.getName(), commandHandler);

        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Hi there!"),
                             new CommandCallback<Object, Object>() {
                                 @Override
                                 public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                     assertEquals("Hi there!", result);
                                 }

                                 @Override
                                 public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
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
    public void testInterceptorChain_CommandHandlerThrowsException() throws Exception {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        final CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(mockInterceptor1.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockInterceptor2.handle((CommandMessage) invocation.getArguments()[0],
                                                                  (UnitOfWork) invocation.getArguments()[1],
                                                                  (InterceptorChain) invocation.getArguments()[2]));
        when(mockInterceptor2.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> commandHandler.handle((CommandMessage) invocation.getArguments()[0],
                                                                (UnitOfWork) invocation.getArguments()[1]));

        testSubject.setHandlerInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class)))
                .thenThrow(new RuntimeException("Faking failed command handling"));
        testSubject.subscribe(String.class.getName(), commandHandler);

        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Hi there!"),
                             new CommandCallback<Object, Object>() {
            @Override
            public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                fail("Expected exception to be thrown");
            }

            @Override
            public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
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
    public void testInterceptorChain_InterceptorThrowsException() throws Exception {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        final CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        when(mockInterceptor1.handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockInterceptor2.handle((CommandMessage) invocation.getArguments()[0],
                                                                  (UnitOfWork) invocation.getArguments()[1],
                                                                  (InterceptorChain) invocation.getArguments()[2]));
        testSubject.setHandlerInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(commandHandler.handle(isA(CommandMessage.class), isA(UnitOfWork.class))).thenReturn("Hi there!");
        testSubject.subscribe(String.class.getName(), commandHandler);
        RuntimeException someException = new RuntimeException("Mocking");
        doThrow(someException).when(mockInterceptor2).handle(isA(CommandMessage.class),
                                                             isA(UnitOfWork.class),
                                                             isA(InterceptorChain.class));
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Hi there!"),
                             new CommandCallback<Object, Object>() {
            @Override
            public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                fail("Expected exception to be propagated");
            }

            @Override
            public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
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
