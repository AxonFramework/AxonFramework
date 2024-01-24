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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the default implemented methods of the {@link EventMessageHandler}.
 *
 * @author Steven van Beelen
 */
class EventMessageHandlerTest {

    @SuppressWarnings("Convert2Lambda") // Cannot spy a lambda
    private final EventMessageHandler testSubject = spy(new EventMessageHandler() {
        @Override
        public Object handleSync(EventMessage<?> event) throws Exception {
            return null;
        }
    });

    @Test
    void prepareResetWithNullResetContextInvokesPrepareReset() {
        testSubject.prepareReset(null, null);

        verify(testSubject).prepareReset(null);
    }

    @Test
    void prepareResetWithNonNullThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> testSubject.prepareReset("non-null",null ));
    }
}
