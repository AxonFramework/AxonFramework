/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConflictResolutionTest {

    private Method method;
    private ConflictResolution subject;
    private ConflictResolver conflictResolver;
    private final CommandMessage<String> commandMessage =
            new GenericCommandMessage<>(QualifiedNameUtils.fromDottedName("test.command"), "test");

    @BeforeEach
    void setUp() throws Exception {
        method = getClass().getDeclaredMethod("handle", String.class, ConflictResolver.class);
        subject = new ConflictResolution();
        conflictResolver = mock(ConflictResolver.class);
        DefaultUnitOfWork.startAndGet(commandMessage);
    }

    @AfterEach
    void tearDown() {
        CurrentUnitOfWork.ifStarted(UnitOfWork::commit);
    }

    @Test
    void factoryMethod() {
        assertNotNull(subject.createInstance(method, method.getParameters(), 1));
        assertNull(subject.createInstance(method, method.getParameters(), 0));
    }

    @Test
    void resolve() {
        ConflictResolution.initialize(conflictResolver);
        assertFalse(subject.matches(GenericEventMessage.asEventMessage("testEvent"), null));
        assertTrue(subject.matches(commandMessage, null));
        assertSame(conflictResolver, ConflictResolution.getConflictResolver());
        assertSame(conflictResolver, subject.resolveParameterValue(commandMessage, null));
    }

    @Test
    void resolveWithoutInitializationReturnsNoConflictsResolver() {
        assertTrue(subject.matches(commandMessage, null));
        assertSame(NoConflictResolver.INSTANCE, subject.resolveParameterValue(commandMessage, null));
    }

    @SuppressWarnings("unused") //used in set up
    private void handle(String command, ConflictResolver conflictResolver) {
    }
}
