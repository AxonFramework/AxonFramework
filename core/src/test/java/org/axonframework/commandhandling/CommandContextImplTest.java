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

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class CommandContextImplTest {

    private Object command;
    private CommandContextImpl testSubject;

    @Before
    public void setUp() {
        command = new Object();
        testSubject = new CommandContextImpl(command);
    }

    @Test
    public void testPropertyLifeCycle() {
        assertFalse(testSubject.isPropertySet("testProp"));
        assertEquals(null, testSubject.getProperty("testProp"));
        testSubject.setProperty("testProp", null);
        assertTrue(testSubject.isPropertySet("testProp"));
        assertEquals(null, testSubject.getProperty("testProp"));
        testSubject.setProperty("testProp", "val");
        assertEquals("val", testSubject.getProperty("testProp"));
        assertTrue(testSubject.isPropertySet("testProp"));
        testSubject.removeProperty("testProp");
        assertFalse(testSubject.isPropertySet("testProp"));
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown", "ThrowableResultOfMethodCallIgnored"})
    @Test
    public void testMarkHandlerExecutionFailed() {
        assertTrue(testSubject.isSuccessful());
        assertFalse(testSubject.isExecuted());
        assertNull(testSubject.getResult());
        assertNull(testSubject.getException());
        RuntimeException exception = new RuntimeException("Mock");

        testSubject.markFailedHandlerExecution(exception);

        assertSame(exception, testSubject.getException());
        assertFalse(testSubject.isSuccessful());
        assertTrue(testSubject.isExecuted());
        assertNull(testSubject.getResult());
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown", "ThrowableResultOfMethodCallIgnored"})
    @Test
    public void testMarkInterceptorExecutionFailed() {
        assertTrue(testSubject.isSuccessful());
        assertFalse(testSubject.isExecuted());
        assertNull(testSubject.getResult());
        assertNull(testSubject.getException());
        RuntimeException exception = new RuntimeException("Mock");

        testSubject.markFailedInterceptorExecution(exception);

        assertSame(exception, testSubject.getException());
        assertFalse(testSubject.isSuccessful());
        assertFalse(testSubject.isExecuted());
        assertNull(testSubject.getResult());
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown", "ThrowableResultOfMethodCallIgnored"})
    @Test
    public void testMarkHandlerExecutionSuccessull() {
        assertTrue(testSubject.isSuccessful());
        assertFalse(testSubject.isExecuted());
        assertNull(testSubject.getResult());
        assertNull(testSubject.getException());

        testSubject.markSuccessfulExecution("OK!");

        assertEquals("OK!", testSubject.getResult());
        assertTrue(testSubject.isSuccessful());
        assertTrue(testSubject.isExecuted());
        assertNull(testSubject.getException());
    }

}
