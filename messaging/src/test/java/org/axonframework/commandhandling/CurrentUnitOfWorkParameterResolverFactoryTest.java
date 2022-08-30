/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
class CurrentUnitOfWorkParameterResolverFactoryTest {

    private CurrentUnitOfWorkParameterResolverFactory testSubject;
    private Method method;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new CurrentUnitOfWorkParameterResolverFactory();
        method = getClass().getMethod("equals", Object.class);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someMethod(UnitOfWork unitOfWork) {
    }

    @Test
    void createInstance() throws Exception {
        Method someMethod = getClass().getMethod("someMethod", UnitOfWork.class);

        assertNull(testSubject.createInstance(method, method.getParameters(), 0));
        assertSame(testSubject, testSubject.createInstance(someMethod, someMethod.getParameters(), 0));
    }

    @Test
    void resolveParameterValue() {
        DefaultUnitOfWork.startAndGet(null);
        try {
            assertSame(CurrentUnitOfWork.get(), testSubject.resolveParameterValue(mock(GenericCommandMessage.class)));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void resolveParameterValueWithoutActiveUnitOfWork() {
        assertNull(testSubject.resolveParameterValue(mock(GenericCommandMessage.class)));
    }

    @Test
    void matches() {
        assertTrue(testSubject.matches(mock(GenericCommandMessage.class)));
        DefaultUnitOfWork.startAndGet(null);
        try {
            assertTrue(testSubject.matches(mock(Message.class)));
            assertTrue(testSubject.matches(mock(EventMessage.class)));
            assertTrue(testSubject.matches(mock(GenericEventMessage.class)));
            assertTrue(testSubject.matches(mock(GenericCommandMessage.class)));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }
}
