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

import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.common.configuration.StubLifecycleRegistry;
import org.axonframework.messaging.core.ApplicationContext;
import org.axonframework.messaging.core.ConfigurationApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceOverridingProcessingContextLifecycleTest {

    @Test
    void lifecycleCallbacksSeeOverriddenApplicationContext() {
        TestComponent rootComponent = new TestComponent("root");
        TestComponent moduleComponent = new TestComponent("module");

        ApplicationContext rootContext = applicationContextWith(rootComponent);
        ApplicationContext moduleContext = applicationContextWith(moduleComponent);

        UnitOfWork unitOfWork = new SimpleUnitOfWorkFactory(rootContext).create();
        AtomicReference<TestComponent> onCommitComponent = new AtomicReference<>();
        AtomicReference<TestComponent> onCompleteComponent = new AtomicReference<>();

        unitOfWork.executeWithResult(context -> {
            ProcessingContext overridden =
                    context.withResource(ProcessingContext.APPLICATION_CONTEXT_RESOURCE, moduleContext);
            overridden.onCommit(ctx -> {
                onCommitComponent.set(ctx.component(TestComponent.class));
                return CompletableFuture.completedFuture(null);
            });
            overridden.whenComplete(ctx -> onCompleteComponent.set(ctx.component(TestComponent.class)));
            return CompletableFuture.completedFuture(null);
        }).join();

        assertThat(onCommitComponent.get()).isSameAs(moduleComponent);
        assertThat(onCompleteComponent.get()).isSameAs(moduleComponent);
    }

    private static ApplicationContext applicationContextWith(TestComponent component) {
        DefaultComponentRegistry registry = new DefaultComponentRegistry();
        registry.registerComponent(TestComponent.class, c -> component);
        return new ConfigurationApplicationContext(registry.build(new StubLifecycleRegistry()));
    }

    private static class TestComponent {
        private final String name;

        private TestComponent(String name) {
            this.name = name;
        }
    }
}
