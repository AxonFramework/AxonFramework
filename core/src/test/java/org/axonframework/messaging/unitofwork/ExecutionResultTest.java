/*
 * Copyright (c) 2010-2015. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.unitofwork;

import org.axonframework.messaging.ExecutionException;
import org.junit.Test;

import static junit.framework.TestCase.*;

/**
 * @author Rene de Waele
 */
public class ExecutionResultTest {

    @Test
    public void testNormalExecutionResult() {
        Object result = new Object();
        ExecutionResult subject = new ExecutionResult(result);
        assertSame(result, subject.getResult());
        assertFalse(subject.isExceptionResult());
        assertNull(subject.getExceptionResult());
    }

    @Test
    public void testUncheckedExceptionResult() {
        RuntimeException mockException = new RuntimeException();
        ExecutionResult subject = new ExecutionResult(mockException);
        assertTrue(subject.isExceptionResult());
        assertSame(mockException, subject.getExceptionResult());
        try {
            subject.getResult();
        } catch (Throwable e) {
            assertSame(mockException, e);
            return;
        }
        throw new AssertionError();
    }

    @Test
    public void testCheckedExceptionResult() {
        Exception mockException = new Exception();
        ExecutionResult subject = new ExecutionResult(mockException);
        assertTrue(subject.isExceptionResult());
        assertSame(mockException, subject.getExceptionResult());
        try {
            subject.getResult();
        } catch (ExecutionException e) {
            assertSame(mockException, e.getCause());
            return;
        }
        throw new AssertionError();
    }
}