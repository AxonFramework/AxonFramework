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

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Context;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Factory for creating {@link UnitOfWork} instances that are instrumented with tracing.
 * <p>
 * This factory creates units of work that automatically start a tracing span before invocation, track the unit of work
 * lifecycle through various phases, and properly end the span on completion or error.
 * <p>
 * The span is managed by the configured {@link Span} supplier and is stored as a resource in the unit of work's
 * {@link Context}. The span scope is also maintained to ensure proper tracing context propagation.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class TracingUnitOfWorkFactory implements UnitOfWorkFactory {

    private static final Context.ResourceKey<Span> SPAN_RESOURCE_KEY =
            Context.ResourceKey.withLabel("tracing-span");

    private final Supplier<Span> spanSupplier;
    private final UnitOfWorkFactory delegate;

    /**
     * Initializes a factory with the given {@code spanSupplier} and a delegate {@link UnitOfWorkFactory}. The unit of
     * work's lifecycle will be traced using spans provided by the {@code spanSupplier}.
     *
     * @param spanSupplier The supplier used to create spans for tracing the units of work.
     * @param delegate     The delegate factory used to create units of work.
     */
    public TracingUnitOfWorkFactory(@Nonnull Supplier<Span> spanSupplier,
                                    @Nonnull UnitOfWorkFactory delegate) {
        Objects.requireNonNull(spanSupplier, "Span supplier cannot be null");
        Objects.requireNonNull(delegate, "Delegate UnitOfWorkFactory cannot be null");
        this.spanSupplier = spanSupplier;
        this.delegate = delegate;
    }

    /**
     * Creates a new {@link UnitOfWork} that is instrumented with tracing.
     * <p>
     * The created unit of work will:
     * <ul>
     *     <li>Start a new tracing span before invocation using the configured span supplier.</li>
     *     <li>Make the span current and maintain the span scope throughout the unit of work lifecycle.</li>
     *     <li>End the span when the unit of work completes successfully.</li>
     *     <li>Record exceptions and end the span when an error occurs during any phase of the unit of work.</li>
     * </ul>
     * The span is stored as resources in the unit of work's context using resource key with label
     * "tracing-span".
     *
     * @return A new traced unit of work.
     */
    @Override
    public UnitOfWork create() {
        var unitOfWork = delegate.create(); //todo: maybe below hooks should be registered before create?

        Span span = spanSupplier.get();
        span.start();
        try (SpanScope ignored = span.makeCurrent()) { // works as long as the the UnitOfWork doesn't change threads
            unitOfWork.onError((ctx, phase, error) -> {
                var contextSpan = ctx.getResource(SPAN_RESOURCE_KEY);
                if (contextSpan != null) {
                    span.recordException(error);
                    span.end();
                }
            });

            unitOfWork.whenComplete(ctx -> {
                var contextSpan = ctx.getResource(SPAN_RESOURCE_KEY);
                if (contextSpan != null) {
                    span.end();
                }
            });
        } catch (Exception e) {
            span.recordException(e);
            span.end();
            return unitOfWork;
        }

        return unitOfWork;
    }
}
