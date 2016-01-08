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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

import java.lang.annotation.Annotation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
public class CurrentUnitOfWorkParameterResolverFactoryTest {

    private CurrentUnitOfWorkParameterResolverFactory testSubject;
    private Annotation[] noAnnotations = new Annotation[]{};

    @Before
    public void setUp() throws Exception {
        testSubject = new CurrentUnitOfWorkParameterResolverFactory();
    }

    @Test
    public void testCreateInstance() throws Exception {
        assertNull(testSubject.createInstance(noAnnotations, Object.class, noAnnotations));
        assertSame(testSubject, testSubject.createInstance(noAnnotations, UnitOfWork.class, noAnnotations));
    }

    @Test
    public void testResolveParameterValue() throws Exception {
        DefaultUnitOfWork.startAndGet(null);
        try {
            assertSame(CurrentUnitOfWork.get(), testSubject.resolveParameterValue(mock(GenericCommandMessage.class)));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testMatches() throws Exception {
        assertFalse(testSubject.matches(mock(GenericCommandMessage.class)));
        DefaultUnitOfWork.startAndGet(null);
        try {
            assertFalse(testSubject.matches(mock(Message.class)));
            assertTrue(testSubject.matches(mock(GenericCommandMessage.class)));
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }
}
