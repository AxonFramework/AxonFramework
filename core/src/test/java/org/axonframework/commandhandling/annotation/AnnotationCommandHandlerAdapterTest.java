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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationCommandHandlerAdapterTest {

    private AnnotationCommandHandlerAdapter testSubject;
    private CommandBus mockBus;
    private MyCommandHandler mockTarget;
    private UnitOfWork mockUnitOfWork;

    @Before
    public void setUp() {
        mockBus = mock(CommandBus.class);
        mockTarget = new MyCommandHandler();
        testSubject = new AnnotationCommandHandlerAdapter(mockTarget, mockBus);
        mockUnitOfWork = mock(UnitOfWork.class);

    }

    @Test
    public void testHandlerDispatching_VoidReturnType() throws Throwable {
        Object actualReturnValue = testSubject.handle("", mockUnitOfWork);
        assertEquals(void.class, actualReturnValue);
        assertEquals(1, mockTarget.voidHandlerInvoked);
        assertEquals(0, mockTarget.returningHandlerInvoked);
    }

    @Test
    public void testHandlerDispatching_WithReturnType() throws Throwable {
        Object actualReturnValue = testSubject.handle(1L, mockUnitOfWork);
        assertEquals(1L, actualReturnValue);
        assertEquals(0, mockTarget.voidHandlerInvoked);
        assertEquals(1, mockTarget.returningHandlerInvoked);
    }

    @Test
    public void testHandlerDispatching_ThrowingException() throws Throwable {
        try {
            testSubject.handle(new HashSet(), mockUnitOfWork);
            fail("Expected exception");
        }
        catch (Exception ex) {
            assertEquals(Exception.class, ex.getClass());
        }
    }

    @Test
    public void testSubscribe() {
        testSubject.subscribe();

        verify(mockBus).subscribe(Long.class, testSubject);
        verify(mockBus).subscribe(String.class, testSubject);
        verify(mockBus).subscribe(HashSet.class, testSubject);
        verify(mockBus).subscribe(ArrayList.class, testSubject);
        verifyNoMoreInteractions(mockBus);
    }

    @Test
    public void testFindHandlerMethod() {
        Method method = testSubject.findCommandHandlerMethodFor("");
        assertEquals("myVoidHandler", method.getName());
    }

    @Test(expected = NoHandlerForCommandException.class)
    public void testHandle_NoHandlerForCommand() throws Throwable {
        testSubject.handle(new LinkedList(), null);
    }

    private static class MyCommandHandler {

        private int voidHandlerInvoked;
        private int returningHandlerInvoked;

        @SuppressWarnings({"UnusedDeclaration"})
        @CommandHandler
        public void myVoidHandler(String stringCommand, UnitOfWork unitOfWork) {
            voidHandlerInvoked++;
        }

        @CommandHandler
        public Long myReturningHandler(Long longCommand, UnitOfWork unitOfWork) {
            assertNotNull("The UnitOfWork was not passed to the command handler", unitOfWork);
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
        public void exceptionThrowingHandler(ArrayList o) throws Exception {
            throw new RuntimeException("Some exception");
        }
    }
}
