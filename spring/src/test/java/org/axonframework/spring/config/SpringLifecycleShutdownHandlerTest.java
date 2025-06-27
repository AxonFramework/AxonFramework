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

package org.axonframework.spring.config;

import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SpringLifecycleShutdownHandler}.
 *
 * @author Allard Buijze
 */
class SpringLifecycleShutdownHandlerTest {

    private Supplier<CompletableFuture<?>> action;

    private SpringLifecycleShutdownHandler testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        action = mock();
        doReturn(CompletableFuture.completedFuture(null)).when(action)
                                                         .get();

        testSubject = new SpringLifecycleShutdownHandler(42, action);
    }

    @Test
    void phaseIsRegisteredCorrectly() {
        assertEquals(42, testSubject.getPhase());
    }

    @Test
    void isRunningReflectsCorrectState() {
        assertFalse(testSubject.isRunning());
        testSubject.start();
        assertTrue(testSubject.isRunning());
        testSubject.stop();
        assertFalse(testSubject.isRunning());
    }

    @Test
    void actionIsInvokedOnShutdown() {
        testSubject.stop();
        verify(action).get();
    }

    @Test
    void callbackInvokedOnAsyncShutdown() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        doReturn(future).when(action).get();

        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        testSubject.stop(() -> callbackInvoked.set(true));

        verify(action).get();
        assertFalse(callbackInvoked.get());

        future.complete(null);
        assertTrue(callbackInvoked.get());
    }
}