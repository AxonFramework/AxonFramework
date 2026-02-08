/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.unitofwork;

import org.axonframework.messaging.core.ApplicationContext;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceOverridingProcessingContextTest {

    private static final ResourceKey<String> TEST_KEY = ResourceKey.withLabel("testKey");

    @Nested
    class ComponentResolution {

        @Test
        void resolvesComponentFromApplicationContextResourceWhenPresent() {
            // given
            String expectedComponent = "moduleComponent";
            ApplicationContext moduleContext = mock(ApplicationContext.class);
            when(moduleContext.component(String.class)).thenReturn(expectedComponent);

            StubProcessingContext delegate = new StubProcessingContext();
            ProcessingContext testSubject = new ResourceOverridingProcessingContext<>(
                    delegate, ProcessingContext.APPLICATION_CONTEXT, moduleContext
            );

            // when
            String result = testSubject.component(String.class);

            // then
            assertThat(result).isEqualTo(expectedComponent);
        }

        @Test
        void resolvesNamedComponentFromApplicationContextResourceWhenPresent() {
            // given
            String expectedComponent = "namedModuleComponent";
            ApplicationContext moduleContext = mock(ApplicationContext.class);
            when(moduleContext.component(String.class, "myName")).thenReturn(expectedComponent);

            StubProcessingContext delegate = new StubProcessingContext();
            ProcessingContext testSubject = new ResourceOverridingProcessingContext<>(
                    delegate, ProcessingContext.APPLICATION_CONTEXT, moduleContext
            );

            // when
            String result = testSubject.component(String.class, "myName");

            // then
            assertThat(result).isEqualTo(expectedComponent);
        }

        @Test
        void fallsBackToDelegateWhenNoApplicationContextResourceIsPresent() {
            // given
            String expectedComponent = "rootComponent";
            ApplicationContext rootContext = mock(ApplicationContext.class);
            when(rootContext.component(String.class)).thenReturn(expectedComponent);
            when(rootContext.component(String.class, null)).thenReturn(expectedComponent);

            StubProcessingContext delegate = new StubProcessingContext(rootContext);
            ProcessingContext testSubject = new ResourceOverridingProcessingContext<>(
                    delegate, TEST_KEY, "someValue"
            );

            // when
            String result = testSubject.component(String.class);

            // then
            assertThat(result).isEqualTo(expectedComponent);
        }

        @Test
        void nestedWithResourceOverridesComponentResolution() {
            // given
            String expectedComponent = "deepModuleComponent";
            ApplicationContext moduleContext = mock(ApplicationContext.class);
            when(moduleContext.component(String.class)).thenReturn(expectedComponent);

            StubProcessingContext delegate = new StubProcessingContext();
            // First wrap with a different key, then with APPLICATION_CONTEXT
            ProcessingContext firstWrap = new ResourceOverridingProcessingContext<>(
                    delegate, TEST_KEY, "someValue"
            );
            ProcessingContext testSubject = firstWrap.withResource(
                    ProcessingContext.APPLICATION_CONTEXT, moduleContext
            );

            // when
            String result = testSubject.component(String.class);

            // then
            assertThat(result).isEqualTo(expectedComponent);
        }
    }

    @Nested
    class LifecycleCallbackDelegation {

        private UnitOfWork unitOfWork;
        private ProcessingContext overriddenContext;
        private ApplicationContext moduleContext;

        @BeforeEach
        void setUp() {
            moduleContext = mock(ApplicationContext.class);
            unitOfWork = new UnitOfWork(
                    "test-uow", Runnable::run, true, EmptyApplicationContext.INSTANCE
            );
        }

        @Test
        void onPhaseCallbackReceivesOverridingContext() {
            // given
            AtomicReference<ProcessingContext> capturedContext = new AtomicReference<>();

            unitOfWork.onPreInvocation(ctx -> {
                overriddenContext = ctx.withResource(
                        ProcessingContext.APPLICATION_CONTEXT, moduleContext
                );
                overriddenContext.onCommit(commitCtx -> {
                    capturedContext.set(commitCtx);
                    return CompletableFuture.completedFuture(null);
                });
                return CompletableFuture.completedFuture(null);
            });

            // when
            unitOfWork.execute().join();

            // then
            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().getResource(ProcessingContext.APPLICATION_CONTEXT))
                    .isSameAs(moduleContext);
        }

        @Test
        void whenCompleteCallbackReceivesOverridingContext() {
            // given
            AtomicReference<ProcessingContext> capturedContext = new AtomicReference<>();

            unitOfWork.onPreInvocation(ctx -> {
                overriddenContext = ctx.withResource(
                        ProcessingContext.APPLICATION_CONTEXT, moduleContext
                );
                overriddenContext.whenComplete(capturedContext::set);
                return CompletableFuture.completedFuture(null);
            });

            // when
            unitOfWork.execute().join();

            // then
            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().getResource(ProcessingContext.APPLICATION_CONTEXT))
                    .isSameAs(moduleContext);
        }

        @Test
        void onErrorCallbackReceivesOverridingContext() {
            // given
            AtomicReference<ProcessingContext> capturedContext = new AtomicReference<>();
            RuntimeException expectedError = new RuntimeException("test error");

            unitOfWork.onPreInvocation(ctx -> {
                overriddenContext = ctx.withResource(
                        ProcessingContext.APPLICATION_CONTEXT, moduleContext
                );
                overriddenContext.onError((errorCtx, phase, error) -> capturedContext.set(errorCtx));
                return CompletableFuture.completedFuture(null);
            });

            unitOfWork.onInvocation(ctx -> CompletableFuture.failedFuture(expectedError));

            // when
            unitOfWork.execute().exceptionally(e -> null).join();

            // then
            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().getResource(ProcessingContext.APPLICATION_CONTEXT))
                    .isSameAs(moduleContext);
        }

        @Test
        void lifecycleCallbackComponentResolutionUsesOverriddenApplicationContext() {
            // given
            String expectedComponent = "moduleComponent";
            when(moduleContext.component(String.class)).thenReturn(expectedComponent);
            AtomicReference<String> capturedComponent = new AtomicReference<>();

            unitOfWork.onPreInvocation(ctx -> {
                overriddenContext = ctx.withResource(
                        ProcessingContext.APPLICATION_CONTEXT, moduleContext
                );
                overriddenContext.onCommit(commitCtx -> {
                    capturedComponent.set(commitCtx.component(String.class));
                    return CompletableFuture.completedFuture(null);
                });
                return CompletableFuture.completedFuture(null);
            });

            // when
            unitOfWork.execute().join();

            // then
            assertThat(capturedComponent.get()).isEqualTo(expectedComponent);
        }
    }
}
