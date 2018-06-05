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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link WindowBackpressure}.
 *
 * @author Milan Savic
 */
@RunWith(MockitoJUnitRunner.class)
public class WindowBackpressureTest {

    @Mock
    private UpdateHandler<String, String> updateHandler;
    @Mock
    private Function<List<String>, String> reductionFunction;
    private WindowBackpressure<String, String> windowBackpressure;

    @Before
    public void setUp() {
        given(reductionFunction.apply(Arrays.asList("Update1", "Update2", "Update3"))).willReturn("UpdateReduction");
        windowBackpressure = new WindowBackpressure<>(updateHandler, reductionFunction, 0, 200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testWindowBackpressure() throws InterruptedException {
        windowBackpressure.onInitialResult("Initial");
        verify(updateHandler).onInitialResult("Initial");

        RuntimeException exception = new RuntimeException("oops");
        windowBackpressure.onCompletedExceptionally(exception);
        verify(updateHandler).onCompletedExceptionally(exception);

        windowBackpressure.onUpdate("Update1");
        windowBackpressure.onUpdate("Update2");
        windowBackpressure.onUpdate("Update3");
        Thread.sleep(220);
        windowBackpressure.onUpdate("Update4");
        verify(reductionFunction).apply(Arrays.asList("Update1", "Update2", "Update3"));
        verify(updateHandler).onUpdate("UpdateReduction");

        windowBackpressure.onCompleted();
        verify(updateHandler).onCompleted();
    }
}
