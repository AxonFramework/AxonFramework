/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.utils.MockException;
import org.junit.Test;

import java.util.Collections;

public class PropagatingErrorHandlerTest {

    private PropagatingErrorHandler testSubject = PropagatingErrorHandler.instance();

    @Test(expected = MockError.class)
    public void handleErrorRethrowsOriginalWhenError() throws Exception {
        testSubject.handleError(new ErrorContext("test", new MockError(), Collections.emptyList()));
    }

    @Test(expected = MockException.class)
    public void handleErrorRethrowsOriginalWhenException() throws Exception {
        testSubject.handleError(new ErrorContext("test", new MockException(), Collections.emptyList()));
    }

    @Test(expected = EventProcessingException.class)
    public void handleErrorWrapsOriginalWhenThrowable() throws Exception {
        testSubject.handleError(new ErrorContext("test", new Throwable("Unknown"), Collections.emptyList()));
    }



    private static class MockError extends Error {
        public MockError() {super("Mock");}
    }
}
