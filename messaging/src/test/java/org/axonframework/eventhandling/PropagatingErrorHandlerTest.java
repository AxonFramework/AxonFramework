/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PropagatingErrorHandlerTest {

    private PropagatingErrorHandler testSubject = PropagatingErrorHandler.instance();

    @Test
    void handleErrorRethrowsOriginalWhenError() throws Exception {
        ErrorContext context = new ErrorContext("test", new MockError(), Collections.emptyList());

        assertThrows(MockError.class, () -> testSubject.handleError(context));
    }

    @Test
    void handleErrorRethrowsOriginalWhenException() throws Exception {
        ErrorContext context = new ErrorContext("test", new MockException(), Collections.emptyList());

        assertThrows(MockException.class, () -> testSubject.handleError(context));
    }

    @Test
    void handleErrorWrapsOriginalWhenThrowable() throws Exception {
        ErrorContext context = new ErrorContext("test", new Throwable("Unknown"), Collections.emptyList());

        assertThrows(EventProcessingException.class, () -> testSubject.handleError(context));
    }

    private static class MockError extends Error {
        public MockError() {super("Mock");}
    }
}
