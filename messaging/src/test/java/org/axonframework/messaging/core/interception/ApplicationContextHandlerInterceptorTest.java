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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.common.configuration.StubLifecycleRegistry;
import org.axonframework.messaging.core.ApplicationContext;
import org.axonframework.messaging.core.ConfigurationApplicationContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextHandlerInterceptorTest {

    @Test
    void interceptorRegistersApplicationContextResourceBeforeProceeding() {
        DefaultComponentRegistry registry = new DefaultComponentRegistry();
        TestComponent moduleComponent = new TestComponent("module");
        registry.registerComponent(TestComponent.class, c -> moduleComponent);
        ApplicationContext applicationContext =
                new ConfigurationApplicationContext(registry.build(new StubLifecycleRegistry()));

        ApplicationContextHandlerInterceptor interceptor =
                new ApplicationContextHandlerInterceptor(applicationContext);

        ProcessingContext baseContext = new StubProcessingContext();
        AtomicReference<ProcessingContext> capturedContext = new AtomicReference<>();

        MessageHandlerInterceptorChain<Message> chain = new CapturingChain(capturedContext);

        interceptor.interceptOnHandle(new DummyMessage(), baseContext, chain);

        ProcessingContext updatedContext = capturedContext.get();
        assertThat(updatedContext).isNotNull();
        assertThat(updatedContext.getResource(ProcessingContext.APPLICATION_CONTEXT_RESOURCE))
                .isSameAs(applicationContext);
        assertThat(updatedContext.component(TestComponent.class)).isSameAs(moduleComponent);
    }

    private static class TestComponent {
        private final String name;

        private TestComponent(String name) {
            this.name = name;
        }
    }

    private static class DummyMessage extends org.axonframework.messaging.core.GenericMessage {

        private DummyMessage() {
            super(new org.axonframework.messaging.core.MessageType("dummy"), "payload");
        }

        @Nonnull
        @Override
        public String identifier() {
            return "dummy";
        }
    }

    private record CapturingChain(AtomicReference<ProcessingContext> capturedContext)
            implements MessageHandlerInterceptorChain<Message> {

        @Nonnull
        @Override
        public MessageStream<?> proceed(@Nonnull Message message, @Nonnull ProcessingContext context) {
            capturedContext.set(context);
            return MessageStream.empty();
        }
    }
}
