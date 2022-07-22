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

package org.axonframework.tracing.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.axonframework.tracing.AxonSpan;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class OpenTelemetryAxonSpan implements AxonSpan {

    private final SpanBuilder spanBuilder;
    private Span span;
    private Scope activeScope;

    protected OpenTelemetryAxonSpan(SpanBuilder spanBuilder) {
        this.spanBuilder = spanBuilder;
    }

    public AxonSpan start() {
        if (activeScope == null) {
            span = spanBuilder.startSpan();
            activeScope = span.makeCurrent();
        }
        return this;
    }

    public void end() {
        Objects.requireNonNull(activeScope, "Span is not started!");
        activeScope.close();
        span.end();
    }


    @Override
    public AxonSpan recordException(Throwable t) {
        Objects.requireNonNull(activeScope, "Span is not started!");
        span.recordException(t);
        span.setStatus(StatusCode.ERROR, t.getMessage());
        return this;
    }

    @Override
    public void run(Runnable runnable) {
        try {
            this.start();
            runnable.run();
        } catch (Exception e) {
            this.recordException(e);
            throw e;
        } finally {
            this.end();
        }
    }

    @Override
    public <T> T runCallable(Callable<T> callable) throws Exception {
        try {
            this.start();
            return callable.call();
        } catch (Exception e) {
            this.recordException(e);
            throw e;
        } finally {
            this.end();
        }
    }

    @Override
    public <T> T runSupplier(Supplier<T> supplier) {
        try {
            this.start();
            return supplier.get();
        } catch (Exception e) {
            this.recordException(e);
            throw e;
        } finally {
            this.end();
        }
    }
}
