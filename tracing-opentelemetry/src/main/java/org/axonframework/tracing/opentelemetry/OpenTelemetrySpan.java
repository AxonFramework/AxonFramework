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

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.axonframework.tracing.Span;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * {@link Span} implementation that uses OpenTelemetry's {@link io.opentelemetry.api.trace.Span} to provide tracing
 * capabilities to an application.
 * <p>
 * These traces should always be created using the {@link OpenTelemetrySpanFactory} since this will make sure the proper
 * parent context is extracted before creating the {@link Span}.
 */
public class OpenTelemetrySpan implements Span {

    private final SpanBuilder spanBuilder;
    private io.opentelemetry.api.trace.Span span;
    private Scope activeScope;

    /**
     * Creates the span, based on the {@link SpanBuilder} provided. This {@link SpanBuilder} will supply the
     * {@link io.opentelemetry.api.trace.Span} when the {@link #start()} method is invoked.
     *
     * @param spanBuilder The provider of the {@link io.opentelemetry.api.trace.Span}.
     */
    OpenTelemetrySpan(SpanBuilder spanBuilder) {
        this.spanBuilder = spanBuilder;
    }

    public Span start() {
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
    public Span recordException(Throwable t) {
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
