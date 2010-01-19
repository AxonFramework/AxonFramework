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

package org.axonframework.core.eventhandler.annotation;

import org.axonframework.core.DomainEvent;
import org.axonframework.core.StubDomainEvent;
import org.axonframework.core.eventhandler.TransactionStatus;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@SuppressWarnings({"UnusedDeclaration"})
public class AnnotationEventHandlerInvokerTest {

    private AnnotationEventHandlerInvoker testSubject;

    /*
    Test scenario:
    even though the super class handler is more specific, the generic one of the subclass takes precedence
    */

    @Test
    public void testInvokeEventHandler_SubClassHasPriority() {
        SecondSubclass secondSubclass = new SecondSubclass();
        testSubject = new AnnotationEventHandlerInvoker(secondSubclass);
        testSubject.invokeEventHandlerMethod(new StubEventTwo());

        assertEquals("Method handler 1 shouldn't be invoked. Calls", 0, secondSubclass.invocationCount1);
        assertEquals("Method handler 2 shouldn't be invoked. Calls", 0, secondSubclass.invocationCount2);
        assertEquals("Expected Method handler 3 to be invoked. Calls", 1, secondSubclass.invocationCount3);
    }

    /*
    Test scenario:
    within a single class, the most specific handler is chosen, even if an exact handler isn't found.
    */

    @Test
    public void testInvokeEventHandler_MostSpecificHandlerInClassChosen() {
        FirstSubclass handler = new FirstSubclass();
        testSubject = new AnnotationEventHandlerInvoker(handler);
        testSubject.invokeEventHandlerMethod(new StubEventTwo() {/*anonymous subclass*/
        });

        assertEquals(0, handler.invocationCount1);
        assertEquals(1, handler.invocationCount2);
    }

    @Test
    public void testInvokeEventHandler_UnknownEventIsIgnored() {
        FirstSubclass handler = new FirstSubclass();
        testSubject = new AnnotationEventHandlerInvoker(handler);
        testSubject.invokeEventHandlerMethod(new DomainEvent() {/*anonymous subclass*/
        });

        assertEquals(0, handler.invocationCount1);
        assertEquals(0, handler.invocationCount2);
    }

    /*
    Test scenario:
    within a single class, the most specific handler is chosen, even if an exact handler isn't found.
    */

    @Test
    public void testValidateEventHandler_MoreThan3ParameterHandlerIsRejected() {
        FirstSubclass handler = new IllegalEventHandler();
        try {
            AnnotationEventHandlerInvoker.validateHandlerMethods(handler);
            fail("Expected an UnsupportedHandlerMethodException");
        }
        catch (UnsupportedHandlerMethodException e) {
            assertTrue(e.getMessage().contains("notARealHandler"));
            assertEquals("notARealHandler", e.getViolatingMethod().getName());
        }
    }

    @Test
    public void testValidateEventHandler_WrongSecondsParameterIsRejected() {
        FirstSubclass handler = new ASecondIllegalEventHandler();
        try {
            AnnotationEventHandlerInvoker.validateHandlerMethods(handler);
            fail("Expected an UnsupportedHandlerMethodException");
        }
        catch (UnsupportedHandlerMethodException e) {
            assertTrue(e.getMessage().contains("notARealHandler"));
            assertEquals("notARealHandler", e.getViolatingMethod().getName());
        }
    }

    /*
    Test scenario:
    a method called handle with single parameter of type DomainEvent is not allowed. It conflicts with the proxy.
     */

    @Test
    public void testValidateEventHandler_HandleDomainEventIsRejected() {
        FirstSubclass handler = new EventHandlerWithUnfortunateMethod();
        try {
            AnnotationEventHandlerInvoker.validateHandlerMethods(handler);
            fail("Expected an UnsupportedHandlerMethodException");
        }
        catch (UnsupportedHandlerMethodException e) {
            assertTrue(e.getMessage().contains("conflict"));
            assertEquals("handle", e.getViolatingMethod().getName());
        }
    }

    @Test
    public void testValidateEventHandler_NonEventParameterIsRejected() {
        AnotherIllegalEventHandler handler = new AnotherIllegalEventHandler();
        try {
            AnnotationEventHandlerInvoker.validateHandlerMethods(handler);
            fail("Expected an UnsupportedHandlerMethodException");
        }
        catch (UnsupportedHandlerMethodException e) {
            assertTrue(e.getMessage().contains("notARealHandler"));
            assertEquals("notARealHandler", e.getViolatingMethod().getName());
        }
    }

    @Test
    public void testFindHandlerConfiguration() {
        SecondSubclass handler = new SecondSubclass();
        testSubject = new AnnotationEventHandlerInvoker(handler);
        org.axonframework.core.eventhandler.annotation.EventHandler configuration = testSubject
                .findEventHandlerConfiguration(new StubEventOne());
        assertNotNull(configuration);

        // if no method is found, null is returned
        assertNull(testSubject.findEventHandlerConfiguration(new DomainEvent() {
        }));
    }

    @Test
    public void testBeforeAndAfterTransactionInvocations_BeforeAndAfterMethodsAvailable() {
        FirstSubclass handler1 = new SecondSubclass();
        TransactionStatus status = mock(TransactionStatus.class);
        testSubject = new AnnotationEventHandlerInvoker(handler1);
        testSubject.invokeBeforeTransaction(status);
        try {
            testSubject.invokeAfterTransaction(status);
            fail("Expected exception to be propagated");
        } catch (TransactionMethodExecutionException e) {
            assertTrue(e.getMessage().contains("afterTransaction"));
        }

        assertEquals(1, handler1.beforeTransactionCount);
        assertEquals(1, handler1.afterTransactionCount);
    }

    @Test
    public void testBeforeAndAfterTransactionInvocations_OnlyBeforeMethodAvailable() {
        FirstSubclass handler1 = new FirstSubclass();
        TransactionStatus status = mock(TransactionStatus.class);
        testSubject = new AnnotationEventHandlerInvoker(handler1);
        testSubject.invokeBeforeTransaction(status);
        testSubject.invokeAfterTransaction(status);

        assertEquals(1, handler1.beforeTransactionCount);
        assertEquals(0, handler1.afterTransactionCount);
    }

    private static class FirstSubclass {

        protected int invocationCount1;
        protected int invocationCount2;
        protected int beforeTransactionCount;
        protected int afterTransactionCount;

        /*
        return values are allowed, but ignored
         */

        @EventHandler
        public boolean method1(StubEventOne event) {
            invocationCount1++;
            return true;
        }

        @EventHandler
        public void method2(StubEventTwo event) {
            invocationCount2++;
        }

        @BeforeTransaction
        public void beforeTransaction(TransactionStatus transactionStatus) {
            beforeTransactionCount++;
        }
    }

    private static class SecondSubclass extends FirstSubclass {

        protected int invocationCount3;

        @EventHandler
        public void method3(StubEventOne event, TransactionStatus transactionStatus) {
            invocationCount3++;
        }

        @AfterTransaction
        public void afterTransaction() throws Exception {
            afterTransactionCount++;
            throw new Exception("Mock");
        }

    }

    private static class IllegalEventHandler extends SecondSubclass {

        @EventHandler
        public void notARealHandler(StubEventTwo event, TransactionStatus transactionStatus,
                                    String thisParameterMakesItIncompatible) {
        }

    }

    private static class ASecondIllegalEventHandler extends SecondSubclass {

        @EventHandler
        public void notARealHandler(StubEventTwo event, String thisParameterMakesItIncompatible) {
        }

    }

    private static class AnotherIllegalEventHandler extends SecondSubclass {

        @EventHandler
        public void notARealHandler(String thisParameterMakesItIncompatible) {
        }

    }

    private static class StubEventOne extends StubDomainEvent {

    }

    private static class StubEventTwo extends StubEventOne {

    }

    private class EventHandlerWithUnfortunateMethod extends FirstSubclass {

        @EventHandler
        public void handle(DomainEvent event) {
        }
    }
}
