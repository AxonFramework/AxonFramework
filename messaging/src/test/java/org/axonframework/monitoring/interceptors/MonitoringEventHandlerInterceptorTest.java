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

package org.axonframework.monitoring.interceptors;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.mockito.Mockito.*;

class MonitoringEventHandlerInterceptorTest {

    private final MessageMonitor.MonitorCallback callback = mock(MessageMonitor.MonitorCallback.class);
    private final MessageMonitor<? super EventMessage> messageMonitor = m -> callback;
    private final ProcessingContext processingContext = mock(ProcessingContext.class);
    private final EventMessage message = mock(EventMessage.class);
    @SuppressWarnings("unchecked")
    private final MessageHandlerInterceptorChain<EventMessage> interceptorChain = mock(MessageHandlerInterceptorChain.class);

    private final MonitoringEventHandlerInterceptor testSubject = new MonitoringEventHandlerInterceptor(messageMonitor);

    @Test
    void reportSuccessOnAfterCommit() {
        when(processingContext.isStarted()).thenReturn(true);
        testSubject.interceptOnHandle(message, processingContext, interceptorChain);

        //noinspection unchecked
        ArgumentCaptor<Function<ProcessingContext, CompletableFuture<?>>> captor = ArgumentCaptor.forClass(Function.class);

        verify(processingContext).onAfterCommit(captor.capture());
        captor.getValue().apply(processingContext).join();

        verify(callback).reportSuccess();
    }

    @Test
    void reportFailureOnAfterCommit() {
        when(processingContext.isStarted()).thenReturn(true);
        testSubject.interceptOnHandle(message, processingContext, interceptorChain);

        ArgumentCaptor<ProcessingLifecycle.ErrorHandler> captor = ArgumentCaptor.forClass(ProcessingLifecycle.ErrorHandler.class);

        verify(processingContext).onError(captor.capture());
        var exception = new RuntimeException("failure");
        captor.getValue().handle(processingContext, mock(ProcessingLifecycle.Phase.class), exception);

        verify(callback).reportFailure(exception);
    }

    @Test
    void noopWhenContextNotStarted() {
        testSubject.interceptOnHandle(message, processingContext, interceptorChain);

        verify(callback, never()).reportSuccess();
        verify(callback, never()).reportFailure(any(Throwable.class));
        verify(callback, never()).reportIgnored();
    }
}
