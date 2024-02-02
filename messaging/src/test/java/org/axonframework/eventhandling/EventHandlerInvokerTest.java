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

package org.axonframework.eventhandling;

import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the default implemented methods of the {@link EventHandlerInvoker}.
 *
 * @author Steven van Beelen
 */
class EventHandlerInvokerTest {

    private final EventHandlerInvoker testSubject = spy(new EventHandlerInvoker() {
        @Override
        public boolean canHandle(@Nonnull EventMessage<?> eventMessage, @Nonnull Segment segment) {
            return true;
        }

        @Override
        public void handle(@Nonnull EventMessage<?> message, ProcessingContext processingContext,
                           @Nonnull Segment segment) throws Exception {
            // Do nothing
        }
    });

    @Test
    void performResetWithNullResetContextInvokesPerformReset() {
        testSubject.performReset(null, null);

        verify(testSubject).performReset(null);
    }

    @Test
    void performResetWithNonNullThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> testSubject.performReset("non-null", null));
    }
}
