/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.commandhandling.annotation.CurrentUnitOfWorkParameterResolverFactory;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

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
    public void someMethod(LegacyUnitOfWork unitOfWork) {
    }

    @Test
    void createInstance() throws Exception {
        Method someMethod = getClass().getMethod("someMethod", LegacyUnitOfWork.class);

        assertNull(testSubject.createInstance(method, method.getParameters(), 0));
        assertSame(testSubject, testSubject.createInstance(someMethod, someMethod.getParameters(), 0));
    }

    @Test
    void resolveParameterValue() {
        LegacyDefaultUnitOfWork.startAndGet(null);
        try {
            assertSame(CurrentUnitOfWork.get(), testSubject.resolveParameterValue(new StubProcessingContext()));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void resolveParameterValueWithoutActiveUnitOfWork() {
        assertNull(testSubject.resolveParameterValue(new StubProcessingContext()));
    }

    @Test
    void matches() {
        assertTrue(testSubject.matches(new StubProcessingContext()));
        LegacyDefaultUnitOfWork.startAndGet(null);
        try {
            assertTrue(testSubject.matches(new StubProcessingContext()));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }
}
