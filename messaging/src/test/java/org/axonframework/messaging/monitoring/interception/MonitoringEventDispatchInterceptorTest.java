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

package org.axonframework.messaging.monitoring.interception;

import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.function.Consumer;

import static org.mockito.Mockito.*;

class MonitoringEventDispatchInterceptorTest {

    private final MessageMonitor.MonitorCallback callback = mock(MessageMonitor.MonitorCallback.class);
    private final MessageMonitor<? super EventMessage> messageMonitor = m -> callback;
    private final ProcessingContext processingContext = mock(ProcessingContext.class);
    private final EventMessage message = mock(EventMessage.class);
    @SuppressWarnings("unchecked")
    private final MessageDispatchInterceptorChain<EventMessage> interceptorChain = mock(MessageDispatchInterceptorChain.class);

    private final MonitoringEventDispatchInterceptor testSubject = new MonitoringEventDispatchInterceptor(messageMonitor);

    @Test
    void reportSuccessOnAfterCommit() {
        when(processingContext.isStarted()).thenReturn(true);
        testSubject.interceptOnDispatch(message, processingContext, interceptorChain);

        //noinspection unchecked
        ArgumentCaptor<Consumer<ProcessingContext>> captor = ArgumentCaptor.forClass(Consumer.class);

        verify(processingContext).runOnAfterCommit(captor.capture());
        captor.getValue().accept(processingContext);

        verify(callback).reportSuccess();
    }

    @Test
    void reportFailureOnAfterCommit() {
        when(processingContext.isStarted()).thenReturn(true);
        testSubject.interceptOnDispatch(message, processingContext, interceptorChain);

        ArgumentCaptor<ProcessingLifecycle.ErrorHandler> captor = ArgumentCaptor.forClass(ProcessingLifecycle.ErrorHandler.class);

        verify(processingContext).onError(captor.capture());
        var exception = new RuntimeException("failure");
        captor.getValue().handle(processingContext, mock(ProcessingLifecycle.Phase.class), exception);

        verify(callback).reportFailure(exception);
    }

    @Test
    void noopWhenContextIsNull() {
        testSubject.interceptOnDispatch(message, null, interceptorChain);

        verify(callback, never()).reportSuccess();
        verify(callback, never()).reportFailure(any(Throwable.class));
        verify(callback, never()).reportIgnored();
    }
}
