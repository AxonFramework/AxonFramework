/*
 * Copyright (c) 2010-2022. Axon Framework
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

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class NoopSpanFactory implements AxonSpanFactory {

    public static final NoopSpanFactory INSTANCE = new NoopSpanFactory();

    @Override
    public AxonSpan createRootTrace(String operationName) {
        return new NoopAxonSpan();
    }

    @Override
    public AxonSpan createHandlerSpan(String operationName, Message<?> parentMessage, boolean forceParent) {
        return new NoopAxonSpan();
    }

    @Override
    public AxonSpan createDispatchSpan(String operationName, Message<?> parentMessage) {
        return new NoopAxonSpan();
    }

    @Override
    public AxonSpan createInternalSpan(String operationName) {
        return new NoopAxonSpan();
    }

    @Override
    public AxonSpan createInternalSpan(String operationName, Message<?> message) {
        return new NoopAxonSpan();
    }

    @Override
    public void registerTagProvider(SpanAttributesProvider supplier) {
        // Do nothing
    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        return message;
    }

    static class NoopAxonSpan implements AxonSpan {

        @Override
        public AxonSpan start() {
            return this;
        }

        @Override
        public void end() {
        }

        @Override
        public AxonSpan recordException(Throwable t) {
            return this;
        }

        @Override
        public void run(Runnable runnable) {
            runnable.run();
        }

        @Override
        public Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        public <T> T runCallable(Callable<T> callable) throws Exception {
            return callable.call();
        }

        @Override
        public <T> T runSupplier(Supplier<T> supplier) {
            return supplier.get();
        }
    }
}
