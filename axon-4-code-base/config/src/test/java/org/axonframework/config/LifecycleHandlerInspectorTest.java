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

package org.axonframework.config;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates the internals of the {@link LifecycleHandlerInspector}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(MockitoExtension.class)
class LifecycleHandlerInspectorTest {

    public static final int TEST_PHASE = 1;

    @Test
    void nothingIsRegisteredForNullComponent(@Mock Configuration configuration) {
        LifecycleHandlerInspector.registerLifecycleHandlers(configuration, null);

        verifyNoInteractions(configuration);
    }

    @Test
    void axonConfigurationExceptionIsThrownForLifecycleHandlerMethodWithParameters(
            @Mock Configuration configuration) {
        assertThrows(
                AxonConfigurationException.class,
                () -> LifecycleHandlerInspector.registerLifecycleHandlers(
                        configuration, new ComponentWithFaultyLifecycleHandler()
                )
        );
    }

    @Test
    void lifecycleHandlerWithReturnTypeCompletableFutureIsRegistered(@Mock Configuration config)
            throws ExecutionException, InterruptedException {
        String asyncShutdownResult = "some result";
        ComponentWithLifecycleHandlers testComponent = new ComponentWithLifecycleHandlers(asyncShutdownResult);
        ArgumentCaptor<LifecycleHandler> lifecycleHandlerCaptor = ArgumentCaptor.forClass(LifecycleHandler.class);

        LifecycleHandlerInspector.registerLifecycleHandlers(config, testComponent);

        verify(config).onShutdown(eq(TEST_PHASE), lifecycleHandlerCaptor.capture());

        CompletableFuture<?> resultFuture = lifecycleHandlerCaptor.getValue().run();
        assertEquals(asyncShutdownResult, resultFuture.get());
    }

    @Test
    void lifecycleAwareComponentsRegisterHandlers(@Mock Configuration config) {
        LifecycleHandlerInspector.registerLifecycleHandlers(config, new ComponentWithLifecycle(
                r -> {
                    r.onStart(42, () -> {
                    });
                    r.onShutdown(24, () -> {
                    });
                }));

        verify(config).onStart(eq(42), any(LifecycleHandler.class));
        verify(config).onShutdown(eq(24), any(LifecycleHandler.class));
    }

    @Test
    void lifecycleHandlerWithoutReturnTypeCompletableFutureIsRegistered(@Mock Configuration config) {
        AtomicBoolean started = new AtomicBoolean(false);
        ComponentWithLifecycleHandlers testComponent = new ComponentWithLifecycleHandlers(started);
        ArgumentCaptor<LifecycleHandler> lifecycleHandlerCaptor = ArgumentCaptor.forClass(LifecycleHandler.class);

        LifecycleHandlerInspector.registerLifecycleHandlers(config, testComponent);

        verify(config).onStart(eq(TEST_PHASE), lifecycleHandlerCaptor.capture());

        lifecycleHandlerCaptor.getValue().run();
        assertTrue(started.get());
    }

    @Test
    void lifecycleHandlerThrownExceptionIsWrappedInLifecycleHandlerInvocationException(@Mock Configuration config)
            throws InterruptedException {
        ComponentWithFailingLifecycleHandler testComponent = new ComponentWithFailingLifecycleHandler();
        ArgumentCaptor<LifecycleHandler> lifecycleHandlerCaptor = ArgumentCaptor.forClass(LifecycleHandler.class);

        LifecycleHandlerInspector.registerLifecycleHandlers(config, testComponent);

        verify(config).onShutdown(eq(TEST_PHASE), lifecycleHandlerCaptor.capture());

        CompletableFuture<?> result = lifecycleHandlerCaptor.getValue().run();
        assertTrue(result.isCompletedExceptionally());

        try {
            result.get();
            fail("Expected an ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(LifecycleHandlerInvocationException.class.isAssignableFrom(e.getCause().getClass()));
        }
    }

    private static class ComponentWithFaultyLifecycleHandler {

        @SuppressWarnings("unused")
        @StartHandler(phase = TEST_PHASE)
        public void start(String someParameter) {
            // Some start up process
        }
    }

    private static class ComponentWithLifecycle implements Lifecycle {

        private final Consumer<LifecycleRegistry> registration;

        public ComponentWithLifecycle(Consumer<LifecycleRegistry> registration) {
            this.registration = registration;
        }

        @Override
        public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
            registration.accept(lifecycle);
        }
    }

    private static class ComponentWithLifecycleHandlers {

        private final AtomicBoolean started;
        private final String asyncShutdownResult;

        private ComponentWithLifecycleHandlers(AtomicBoolean started) {
            this.started = started;
            this.asyncShutdownResult = "some result";
        }

        private ComponentWithLifecycleHandlers(String asyncShutdownResult) {
            this.started = new AtomicBoolean(false);
            this.asyncShutdownResult = asyncShutdownResult;
        }

        @StartHandler(phase = TEST_PHASE)
        public void start() {
            started.set(true);
        }

        @SuppressWarnings("unused")
        @ShutdownHandler(phase = TEST_PHASE)
        public CompletableFuture<String> shutdownAsync() {
            return CompletableFuture.completedFuture(asyncShutdownResult);
        }
    }

    private static class ComponentWithFailingLifecycleHandler {

        @ShutdownHandler(phase = TEST_PHASE)
        public void shutdown() {
            throw new RuntimeException("some test exception");
        }
    }
}
