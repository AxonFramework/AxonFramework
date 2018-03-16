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

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link ThrottleFirstBackpressure}.
 *
 * @author Milan Savic
 */
public class ThrottleFirstBackpressureTest {

    @SuppressWarnings("unchecked")
    private final UpdateHandler<String, String> updateHandler = mock(UpdateHandler.class);
    @SuppressWarnings("unchecked")
    private final ThrottleFirstBackpressure<String, String> throttleFirstBackpressure = new ThrottleFirstBackpressure<>(
            updateHandler,
            0,
            200,
            TimeUnit.MILLISECONDS);

    @Test
    public void testThrottleFirstBackpressure() throws InterruptedException {
        throttleFirstBackpressure.onInitialResult("Initial");
        verify(updateHandler).onInitialResult("Initial");

        RuntimeException exception = new RuntimeException("oops");
        throttleFirstBackpressure.onError(exception);
        verify(updateHandler).onError(exception);

        throttleFirstBackpressure.onUpdate("Update1");
        throttleFirstBackpressure.onUpdate("Update2");
        Thread.sleep(210);
        throttleFirstBackpressure.onUpdate("Update3");
        throttleFirstBackpressure.onUpdate("Update4");
        Thread.sleep(210);
        verify(updateHandler).onUpdate("Update1");
        verify(updateHandler).onUpdate("Update3");

        throttleFirstBackpressure.onCompleted();
        verify(updateHandler).onCompleted();
    }
}
