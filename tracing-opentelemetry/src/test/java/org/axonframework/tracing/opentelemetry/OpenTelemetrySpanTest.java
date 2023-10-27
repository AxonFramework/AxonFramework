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

package org.axonframework.tracing.opentelemetry;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenTelemetrySpanTest {

    private final SpanBuilder spanBuilder = Mockito.mock(SpanBuilder.class);
    private final io.opentelemetry.api.trace.Span span = Mockito.mock(io.opentelemetry.api.trace.Span.class);
    private final List<Scope> openScopeList = new CopyOnWriteArrayList<>();

    private OpenTelemetrySpan openTelemetrySpan;

    @BeforeEach
    void setUp() {
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenAnswer(invocation -> {
            Scope mock = mock(Scope.class);
            openScopeList.add(mock);
            doAnswer((invocationOnMock) -> {
                openScopeList.remove(mock);
                return null;
            }).when(mock).close();
            return mock;
        });
        when(span.getSpanContext()).thenReturn(mock(SpanContext.class));

        openTelemetrySpan = new OpenTelemetrySpan(spanBuilder);
    }

    @Test
    void startingTheSpanStartsItButDoesNotMakeItCurrent() {
        openTelemetrySpan.start();

        verify(spanBuilder).startSpan();
        assertEquals(0, openScopeList.size());
    }

    @Test
    void startingTheSpanAndMakingItActiveMakesItCurrent() {
        openTelemetrySpan.start();
        SpanScope scope = openTelemetrySpan.makeCurrent();

        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();

        assertEquals(1, openScopeList.size());
        scope.close();
        assertEquals(0, openScopeList.size());
    }

    @Test
    void endingTheSpanDoesNotCloseOpenScopes() {
        Span start = openTelemetrySpan.start();
        start.makeCurrent();

        start.end();
        verify(openScopeList.get(0), never()).close();
        verify(span).end();
    }

    @Test
    void endsSpanEventWhenSpansAreStillCurrent() {
        Span axonSpan = openTelemetrySpan.start();
        axonSpan.start();
        axonSpan.makeCurrent();

        axonSpan.end();
        verify(span, times(1)).end();
    }

    @Test
    void recordsExceptionsOnSpan() {
        IllegalArgumentException exception = new IllegalArgumentException("This is my exception message");
        openTelemetrySpan.start().recordException(exception);

        verify(span).recordException(exception);
        verify(span).setStatus(StatusCode.ERROR, "This is my exception message");
    }
}
