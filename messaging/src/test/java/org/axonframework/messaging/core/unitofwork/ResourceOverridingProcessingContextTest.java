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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.axonframework.messaging.core.unitofwork.ProcessingLifecycle.DefaultPhases.COMMIT;
import static org.axonframework.messaging.core.unitofwork.ProcessingLifecycle.DefaultPhases.INVOCATION;
import static org.axonframework.messaging.core.unitofwork.ProcessingLifecycle.DefaultPhases.PRE_INVOCATION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link ResourceOverridingProcessingContext}.
 *
 * @author Steven van Beelen
 */
class ResourceOverridingProcessingContextTest {

    @Test
    void resourcesReturnsCollectionOfAllResources() {
        Context.ResourceKey<String> testResourceKey = Context.ResourceKey.withLabel("my-resource");
        String testResourceValue = "my-resource";
        Context.ResourceKey<String> testOverrideKey = Context.ResourceKey.withLabel("overriding-resource");
        String testOverrideValue = "overriding-resource";

        ResourceOverridingProcessingContext<String> testSubject = new ResourceOverridingProcessingContext<>(
                new StubProcessingContext().withResource(testResourceKey, testResourceValue),
                testOverrideKey, testOverrideValue
        );

        Map<Context.ResourceKey<?>, Object> result = testSubject.resources();

        assertThat(result).containsKey(testResourceKey);
        assertThat(result).containsValue(testResourceValue);
        assertThat(result).containsKey(testOverrideKey);
        assertThat(result).containsValue(testOverrideValue);
    }

    @Nested
    class LifecycleCallbackContextOverrideTests {

        private final Context.ResourceKey<String> testResourceKey = Context.ResourceKey.withLabel("test-resource");
        private final Context.ResourceKey<String> testOverrideKey = Context.ResourceKey.withLabel("overriding-resource");
        private final String delegateResourceValue = "delegate-resource-value";
        private final String overriddenResourceValue = "overridden-resource-value";
        private LifecycleAwareStubProcessingContext delegate;
        private ResourceOverridingProcessingContext<String> testSubject;

        @BeforeEach
        void setUp() {
            delegate = new LifecycleAwareStubProcessingContext();
            delegate.putResource(testResourceKey, delegateResourceValue);
            delegate.putResource(testOverrideKey, "delegate-overridden-value");
            testSubject = new ResourceOverridingProcessingContext<>(delegate, testOverrideKey, overriddenResourceValue);
        }

        @Test
        void onCallbackReceivesOverridingContext() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();

            testSubject.on(INVOCATION, context -> {
                contextRef.set(context);
                return CompletableFuture.completedFuture(null);
            });

            delegate.triggerPhase(INVOCATION);

            assertContextContainsOverride(contextRef.get());
        }

        @Test
        void onPreInvocationCallbackReceivesOverridingContext() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();

            testSubject.onPreInvocation(context -> {
                contextRef.set(context);
                return CompletableFuture.completedFuture(null);
            });

            delegate.triggerPhase(PRE_INVOCATION);

            assertContextContainsOverride(contextRef.get());
        }

        @Test
        void runOnPreInvocationCallbackReceivesOverridingContext() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();

            testSubject.runOnPreInvocation(contextRef::set);

            delegate.triggerPhase(PRE_INVOCATION);

            assertContextContainsOverride(contextRef.get());
        }

        @Test
        void onErrorCallbackReceivesOverridingContext() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();
            IllegalStateException expectedException = new IllegalStateException("simulated");

            testSubject.onError((context, phase, error) -> {
                contextRef.set(context);
                assertThat(phase).isEqualTo(COMMIT);
                assertThat(error).isSameAs(expectedException);
            });

            delegate.triggerError(COMMIT, expectedException);

            assertContextContainsOverride(contextRef.get());
        }

        @Test
        void whenCompleteCallbackReceivesOverridingContext() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();

            testSubject.whenComplete(contextRef::set);

            delegate.triggerComplete();

            assertContextContainsOverride(contextRef.get());
        }

        @Test
        void doFinallyCallbackReceivesOverridingContextOnCompletion() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();

            testSubject.doFinally(contextRef::set);

            delegate.triggerComplete();

            assertContextContainsOverride(contextRef.get());
        }

        @Test
        void doFinallyCallbackReceivesOverridingContextOnError() {
            AtomicReference<ProcessingContext> contextRef = new AtomicReference<>();
            IllegalStateException expectedException = new IllegalStateException("simulated");

            testSubject.doFinally(contextRef::set);

            delegate.triggerError(COMMIT, expectedException);

            assertContextContainsOverride(contextRef.get());
        }

        private void assertContextContainsOverride(ProcessingContext context) {
            assertThat(context).isSameAs(testSubject);
            assertThat(context.getResource(testResourceKey)).isEqualTo(delegateResourceValue);
            assertThat(context.getResource(testOverrideKey)).isEqualTo(overriddenResourceValue);
        }
    }

    private static class LifecycleAwareStubProcessingContext extends StubProcessingContext {

        private final Map<ProcessingLifecycle.Phase, List<Function<ProcessingContext, CompletableFuture<?>>>> phaseActions =
                new HashMap<>();
        private final List<ProcessingLifecycle.ErrorHandler> errorHandlers = new ArrayList<>();
        private final List<Consumer<ProcessingContext>> completionHandlers = new ArrayList<>();

        @Override
        public ProcessingLifecycle on(@Nonnull ProcessingLifecycle.Phase phase,
                                      @Nonnull Function<ProcessingContext, CompletableFuture<?>> action) {
            phaseActions.computeIfAbsent(phase, p -> new ArrayList<>()).add(action);
            return this;
        }

        @Override
        public ProcessingLifecycle onError(@Nonnull ProcessingLifecycle.ErrorHandler action) {
            errorHandlers.add(action);
            return this;
        }

        @Override
        public ProcessingLifecycle whenComplete(@Nonnull Consumer<ProcessingContext> action) {
            completionHandlers.add(action);
            return this;
        }

        void triggerPhase(ProcessingLifecycle.Phase phase) {
            phaseActions.getOrDefault(phase, List.of()).forEach(action -> action.apply(this).join());
        }

        void triggerError(ProcessingLifecycle.Phase phase, Throwable error) {
            errorHandlers.forEach(handler -> handler.handle(this, phase, error));
        }

        void triggerComplete() {
            completionHandlers.forEach(handler -> handler.accept(this));
        }
    }
}
