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
 */

package org.axonframework.queryhandling.backpressure;

import org.axonframework.queryhandling.UpdateHandler;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link DebounceBackpressure}.
 *
 * @author Milan Savic
 */
@RunWith(MockitoJUnitRunner.class)
public class DebounceBackpressureTest {

    @Mock
    private UpdateHandler<String, String> updateHandler;
    private DebounceBackpressure<String, String> sampleBackpressure;

    @Before
    public void setUp() {
        sampleBackpressure = new DebounceBackpressure<>(updateHandler, 200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testDebounceBackpressure() throws InterruptedException {
        sampleBackpressure.onInitialResult("Initial");
        verify(updateHandler).onInitialResult("Initial");

        RuntimeException exception = new RuntimeException("oops");
        sampleBackpressure.onCompletedExceptionally(exception);
        verify(updateHandler).onCompletedExceptionally(exception);

        sampleBackpressure.onUpdate("Update1");
        Thread.sleep(150);
        sampleBackpressure.onUpdate("Update2");
        sampleBackpressure.onUpdate("Update3");
        Thread.sleep(80);
        sampleBackpressure.onUpdate("Update4");
        Thread.sleep(220);
        sampleBackpressure.onUpdate("Update5");
        verify(updateHandler).onUpdate("Update4");

        sampleBackpressure.onCompleted();
        verify(updateHandler).onCompleted();
    }
}
