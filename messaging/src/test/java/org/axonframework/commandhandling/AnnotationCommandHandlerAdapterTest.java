/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.commandhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class AnnotationCommandHandlerAdapterTest {

    private AnnotationCommandHandlerAdapter testSubject;
    private CommandBus mockBus;
    private MyCommandHandler mockTarget;
    private UnitOfWork<CommandMessage<?>> mockUnitOfWork;
    private ParameterResolverFactory parameterResolverFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockBus = mock(CommandBus.class);
        mockTarget = new MyCommandHandler();
        parameterResolverFactory = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new AnnotationCommandHandlerAdapter(mockTarget, parameterResolverFactory);
        mockUnitOfWork = mock(UnitOfWork.class);
        when(mockUnitOfWork.resources()).thenReturn(mock(Map.class));
        when(mockUnitOfWork.getCorrelationData()).thenReturn(MetaData.emptyInstance());
        when(mockBus.subscribe(any(), any())).thenReturn(mock(Registration.class));
        CurrentUnitOfWork.set(mockUnitOfWork);
    }

    @AfterEach
    void tearDown() {
        CurrentUnitOfWork.clear(mockUnitOfWork);
    }

    @Test
    void handlerDispatching_VoidReturnType() throws Exception {
        Object actualReturnValue = testSubject.handle(GenericCommandMessage.asCommandMessage(""));
        assertNull(actualReturnValue);
        assertEquals(1, mockTarget.voidHandlerInvoked);
        assertEquals(0, mockTarget.returningHandlerInvoked);
    }

    @Test
    void handlerDispatching_WithReturnType() throws Exception {
        Object actualReturnValue = testSubject.handle(GenericCommandMessage.asCommandMessage(1L));
        assertEquals(1L, actualReturnValue);
        assertEquals(0, mockTarget.voidHandlerInvoked);
        assertEquals(1, mockTarget.returningHandlerInvoked);
    }

    @Test
    void handlerDispatching_WithCustomCommandName() throws Exception {
        Object actualReturnValue = testSubject.handle(new GenericCommandMessage<>(new GenericMessage<>(1L), "almostLong"));
        assertEquals(1L, actualReturnValue);
        assertEquals(0, mockTarget.voidHandlerInvoked);
        assertEquals(0, mockTarget.returningHandlerInvoked);
        assertEquals(1, mockTarget.almostDuplicateReturningHandlerInvoked);
    }

    @Test
    void handlerDispatching_ThrowingException() {
        try {
            testSubject.handle(GenericCommandMessage.asCommandMessage(new HashSet()));
            fail("Expected exception");
        } catch (Exception ex) {
            assertEquals(Exception.class, ex.getClass());
            return;
        }
        fail("Shouldn't make it till here");
    }

    @Test
    void subscribe() {
        testSubject.subscribe(mockBus);

        verify(mockBus).subscribe(Long.class.getName(), testSubject);
        verify(mockBus).subscribe(String.class.getName(), testSubject);
        verify(mockBus).subscribe(HashSet.class.getName(), testSubject);
        verify(mockBus).subscribe(ArrayList.class.getName(), testSubject);
        verify(mockBus).subscribe("almostLong", testSubject);
        verifyNoMoreInteractions(mockBus);
    }

    @Test
    void handle_NoHandlerForCommand() throws Exception {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage(new LinkedList<>());

        assertThrows(NoHandlerForCommandException.class, () -> testSubject.handle(command));
    }

    private static class MyCommandHandler {

        private int voidHandlerInvoked;
        private int returningHandlerInvoked;
        private int almostDuplicateReturningHandlerInvoked;

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void myVoidHandler(String stringCommand, UnitOfWork<CommandMessage<?>> unitOfWork) {
            voidHandlerInvoked++;
        }

        @CommandHandler(commandName = "almostLong")
        public Long myAlmostDuplicateReturningHandler(Long longCommand, UnitOfWork<CommandMessage<?>> unitOfWork) {
            assertNotNull(unitOfWork, "The UnitOfWork was not passed to the command handler");
            almostDuplicateReturningHandlerInvoked++;
            return longCommand;
        }

        @CommandHandler
        public Long myReturningHandler(Long longCommand, UnitOfWork<CommandMessage<?>> unitOfWork) {
            assertNotNull(unitOfWork, "The UnitOfWork was not passed to the command handler");
            returningHandlerInvoked++;
            return longCommand;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void exceptionThrowingHandler(HashSet o) throws Exception {
            throw new Exception("Some exception");
        }

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void exceptionThrowingHandler(ArrayList o) {
            throw new RuntimeException("Some exception");
        }
    }
}
