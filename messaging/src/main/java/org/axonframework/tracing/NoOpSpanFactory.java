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

package org.axonframework.tracing;

import org.axonframework.messaging.Message;

import java.util.function.Supplier;

/**
 * {@link SpanFactory} implementation that creates a {@link NoOpSpan}. This span does not do any tracing at all. It's
 * used as a fallback when there is no tracing implementation available, so framework code does not have to check.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class NoOpSpanFactory implements SpanFactory {

    /**
     * Singleton instance of the {@link NoOpSpanFactory}, which is used for configuration when there is no specific
     * implementation configured.
     */
    public static final NoOpSpanFactory INSTANCE = new NoOpSpanFactory();

    @Override
    public Span createRootTrace(Supplier<String> operationNameSupplier) {
        return new NoOpSpan();
    }

    @Override
    public Span createHandlerSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                  boolean isChildTrace,
                                  Message<?>... linkedParents) {
        return new NoOpSpan();
    }

    @Override
    public Span createDispatchSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                   Message<?>... linkedSiblings) {
        return new NoOpSpan();
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier) {
        return new NoOpSpan();
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier, Message<?> message) {
        return new NoOpSpan();
    }

    @Override
    public void registerSpanAttributeProvider(SpanAttributesProvider supplier) {
        // Do nothing
    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        return message;
    }

    public static class NoOpSpan implements Span {

        @Override
        public Span start() {
            return this;
        }

        @Override
        public SpanScope makeCurrent() {
            return () -> {};
        }

        @Override
        public void end() {
            // Do nothing
        }

        @Override
        public Span recordException(Throwable t) {
            return this;
        }

        @Override
        public Span addAttribute(String key, String value) {
            return this;
        }
    }
}
