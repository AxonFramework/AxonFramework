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
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenTelemetrySpanTest {

    private final SpanBuilder spanBuilder = Mockito.mock(SpanBuilder.class);
    private final io.opentelemetry.api.trace.Span span = Mockito.mock(io.opentelemetry.api.trace.Span.class);

    private OpenTelemetrySpan openTelemetrySpan;
    private List<Scope> createdScopesList = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenAnswer(invocation -> {
            Scope mock = mock(Scope.class);
            createdScopesList.add(mock);
            return mock;
        });

        openTelemetrySpan = new OpenTelemetrySpan(spanBuilder);
    }

    @Test
    void startingTheSpanMakesItStartWithCurrentScope() {
        openTelemetrySpan.start();

        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();
        assertEquals(1, createdScopesList.size());
    }

    @Test
    void endingTheSpanClosesScopeAndEndsIt() {
        Span start = openTelemetrySpan.start();

        start.end();
        verify(createdScopesList.get(0)).close();
        verify(span).end();
    }

    @Test
    void onlyClosesWhenAllScopesAreClosed() {
        Span axonSpan = openTelemetrySpan.start();
        axonSpan.start();

        axonSpan.end();
        verify(createdScopesList.get(0), never()).close();
        // The last created scope is closed first, it's a tree
        verify(createdScopesList.get(1), times(1)).close();
        verify(span, never()).end();

        axonSpan.end();
        verify(createdScopesList.get(1), times(1)).close();
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
