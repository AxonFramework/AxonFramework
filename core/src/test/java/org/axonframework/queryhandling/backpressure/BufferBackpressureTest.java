/*
 * Copyright (c) 2010-2018. Axon Framework
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
 *
 *
 */

package org.axonframework.queryhandling.backpressure;

import org.axonframework.queryhandling.UpdateHandler;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link BufferBackpressure}.
 *
 * @author Milan Savic
 */
public class BufferBackpressureTest {

    private UpdateHandler<String, List<String>> updateHandler;
    private BufferBackpressure<String, String> bufferBackpressure;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        updateHandler = mock(UpdateHandler.class);
        bufferBackpressure = new BufferBackpressure(updateHandler, 2);
    }

    @Test
    public void testBufferBackpressure() {
        // given the setup and...
        RuntimeException exception = new RuntimeException("oops");

        // when
        bufferBackpressure.onInitialResult("Initial");
        bufferBackpressure.onUpdate("Update1");
        bufferBackpressure.onUpdate("Update2");
        bufferBackpressure.onUpdate("Update3");
        bufferBackpressure.onUpdate("Update4");
        bufferBackpressure.onError(exception);
        bufferBackpressure.onCompleted();

        // then
        verify(updateHandler).onInitialResult("Initial");
        verify(updateHandler).onUpdate(Arrays.asList("Update1", "Update2"));
        verify(updateHandler).onUpdate(Arrays.asList("Update3", "Update4"));
        verify(updateHandler).onError(exception);
        verify(updateHandler).onCompleted();
    }

}
