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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyMessageSupportingContext;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConflictResolutionTest {

    private Method method;
    private ConflictResolution subject;
    private ConflictResolver conflictResolver;
    private final CommandMessage<String> commandMessage =
            new GenericCommandMessage<>(new MessageType("command"), "test");

    @BeforeEach
    void setUp() throws Exception {
        method = getClass().getDeclaredMethod("handle", String.class, ConflictResolver.class);
        subject = new ConflictResolution();
        conflictResolver = mock(ConflictResolver.class);
        LegacyDefaultUnitOfWork.startAndGet(commandMessage);
    }

    @AfterEach
    void tearDown() {
        CurrentUnitOfWork.ifStarted(LegacyUnitOfWork::commit);
    }

    @Test
    void factoryMethod() {
        assertNotNull(subject.createInstance(method, method.getParameters(), 1));
        assertNull(subject.createInstance(method, method.getParameters(), 0));
    }

    @Test
    void resolve() {
        ConflictResolution.initialize(conflictResolver);
        assertFalse(subject.matches(StubProcessingContext.forMessage(EventTestUtils.asEventMessage("testEvent"))));
        assertTrue(subject.matches(StubProcessingContext.forMessage(commandMessage)));
        assertSame(conflictResolver, ConflictResolution.getConflictResolver());
        assertSame(conflictResolver, subject.resolveParameterValue(StubProcessingContext.forMessage(commandMessage)));
    }

    @Test
    void resolveWithoutInitializationReturnsNoConflictsResolver() {
        assertTrue(subject.matches(StubProcessingContext.forMessage(commandMessage)));
        assertSame(NoConflictResolver.INSTANCE, subject.resolveParameterValue(new LegacyMessageSupportingContext(commandMessage)));
    }

    @SuppressWarnings("unused") //used in set up
    private void handle(String command, ConflictResolver conflictResolver) {
    }
}
