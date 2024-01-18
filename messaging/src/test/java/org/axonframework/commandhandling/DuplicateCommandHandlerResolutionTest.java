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

import org.axonframework.messaging.MessageHandler;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test towards verifying the expected behavior of the provided default {@link DuplicateCommandHandlerResolver}
 * implementations in the DuplicateCommandHandlerResolution enum.
 *
 * @author Steven van Beelen
 */
class DuplicateCommandHandlerResolutionTest {

    private MessageHandler<? super CommandMessage<?>, CommandResultMessage<?>> initialHandler;
    private MessageHandler<? super CommandMessage<?>, CommandResultMessage<?>> duplicateHandler;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        initialHandler = mock(MessageHandler.class);
        doReturn(DuplicateCommandHandlerResolutionTest.Handler1.class).when(initialHandler).getTargetType();
        //noinspection unchecked
        duplicateHandler = mock(MessageHandler.class);
        doReturn(DuplicateCommandHandlerResolutionTest.Handler2.class).when(duplicateHandler).getTargetType();
    }

    @Test
    void logAndOverride() {
        DuplicateCommandHandlerResolver testSubject = DuplicateCommandHandlerResolution.logAndOverride();

        MessageHandler<? super CommandMessage<?>, CommandResultMessage<?>> result = testSubject.resolve("test", initialHandler, duplicateHandler);

        assertEquals(duplicateHandler, result);
    }

    @Test
    void silentlyOverride() {
        DuplicateCommandHandlerResolver testSubject = DuplicateCommandHandlerResolution.silentOverride();

        MessageHandler<? super CommandMessage<?>, CommandResultMessage<?>> result = testSubject.resolve("test", initialHandler, duplicateHandler);

        assertEquals(duplicateHandler, result);
    }

    @Test
    void duplicateHandlersRejected() {
        DuplicateCommandHandlerResolver testSubject = DuplicateCommandHandlerResolution.rejectDuplicates();

        try {
            testSubject.resolve("testCommand", initialHandler, duplicateHandler);
            fail("Expected DuplicateCommandHandlerSubscriptionException");
        } catch (DuplicateCommandHandlerSubscriptionException e) {
            assertTrue(e.getMessage().contains("[testCommand]"));
            assertTrue(e.getMessage().contains("Handler2]"));
            assertTrue(e.getMessage().contains("Handler1]"));
        }
    }

    public static class Handler1 {}

    public static class Handler2 {}
}
