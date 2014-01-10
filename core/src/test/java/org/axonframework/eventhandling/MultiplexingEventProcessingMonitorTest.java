/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.domain.GenericEventMessage;
import org.junit.*;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class MultiplexingEventProcessingMonitorTest {

    private EventProcessingMonitor mockMonitor;
    private MultiplexingEventProcessingMonitor testSubject;
    private AsyncEventListener member;

    @Before
    public void setUp() throws Exception {
        mockMonitor = mock(EventProcessingMonitor.class);
        testSubject = new MultiplexingEventProcessingMonitor(mockMonitor);
        member = mock(AsyncEventListener.class);
    }

    @Test
    public void testMonitorConfirmsImmediateOnUnregisteredEvent() throws Exception {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        testSubject.onEventProcessingCompleted(messages);

        verify(mockMonitor).onEventProcessingCompleted(messages);
    }

    @Test
    public void testMonitorReportsFailureImmediateOnUnregisteredEvent() throws Exception {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        final RuntimeException cause = new RuntimeException();
        testSubject.onEventProcessingFailed(messages, cause);

        verify(mockMonitor).onEventProcessingFailed(messages, cause);
    }

    @Test
    public void testMonitorConfirmsOnOnlyDirectInvocations() {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        testSubject.prepare(messages.get(0));
        testSubject.onEventProcessingCompleted(messages);

        verify(mockMonitor).onEventProcessingCompleted(messages);
    }

    @Test
    public void testMonitorReportsFailureOnOnlyDirectInvocations() throws Exception {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        final RuntimeException cause = new RuntimeException();
        testSubject.prepare(messages.get(0));
        testSubject.onEventProcessingFailed(messages, cause);

        verify(mockMonitor).onEventProcessingFailed(messages, cause);
    }

    @Test
    public void testMonitorConfirmsWhenAllAsyncRequestsConfirm() {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        testSubject.prepare(messages.get(0));
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.onEventProcessingCompleted(messages);
        testSubject.onEventProcessingCompleted(messages);
        testSubject.onEventProcessingCompleted(messages);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
        verify(mockMonitor, never()).onEventProcessingFailed(isA(List.class), any(Throwable.class));

        testSubject.onEventProcessingCompleted(messages);

        verify(mockMonitor, never()).onEventProcessingFailed(isA(List.class), any(Throwable.class));
        verify(mockMonitor).onEventProcessingCompleted(messages);
    }

    @Test
    public void testMonitorReportsFailureWhenAllAsyncRequestsFail() {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        final RuntimeException cause = new RuntimeException();

        testSubject.prepare(messages.get(0));
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.onEventProcessingFailed(messages, cause);
        testSubject.onEventProcessingFailed(messages, cause);
        testSubject.onEventProcessingFailed(messages, cause);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
        verify(mockMonitor, never()).onEventProcessingFailed(isA(List.class), any(Throwable.class));

        testSubject.onEventProcessingFailed(messages, cause);

        verify(mockMonitor).onEventProcessingFailed(messages, cause);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
    }

    @Test
    public void testMonitorReportsFailureWhenOnlyOneAsyncRequestsFails() {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"));
        final RuntimeException cause = new RuntimeException();

        testSubject.prepare(messages.get(0));
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.onEventProcessingCompleted(messages);
        testSubject.onEventProcessingFailed(messages, cause);
        testSubject.onEventProcessingCompleted(messages);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
        verify(mockMonitor, never()).onEventProcessingFailed(isA(List.class), any(Throwable.class));

        testSubject.onEventProcessingCompleted(messages);

        verify(mockMonitor).onEventProcessingFailed(messages, cause);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
    }

    @Test
    public void testMonitorReportsFailureWhenOnlyOneAsyncRequestsFails_Batched() {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"),
                                                                 new GenericEventMessage("test2"));
        final RuntimeException cause = new RuntimeException();

        testSubject.prepare(messages.get(0));
        testSubject.prepare(messages.get(1));
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(1), member);
        testSubject.prepareForInvocation(messages.get(1), member);
        testSubject.prepareForInvocation(messages.get(1), member);
        testSubject.onEventProcessingCompleted(messages);
        testSubject.onEventProcessingFailed(messages.subList(0, 1), cause);
        testSubject.onEventProcessingCompleted(messages.subList(1, 2));
        testSubject.onEventProcessingCompleted(messages);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
        verify(mockMonitor, never()).onEventProcessingFailed(isA(List.class), any(Throwable.class));

        testSubject.onEventProcessingCompleted(messages);

        verify(mockMonitor).onEventProcessingFailed(messages.subList(0, 1), cause);
        verify(mockMonitor).onEventProcessingCompleted(messages.subList(1, 2));
    }

    @Test
    public void testMonitorReportsFailureWhenOnlyAllAsyncMessagesFail_Batched() {
        final List<GenericEventMessage> messages = Arrays.asList(new GenericEventMessage("test"),
                                                                 new GenericEventMessage("test2"));
        final RuntimeException cause = new RuntimeException();

        testSubject.prepare(messages.get(0));
        testSubject.prepare(messages.get(1));
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(0), member);
        testSubject.prepareForInvocation(messages.get(1), member);
        testSubject.prepareForInvocation(messages.get(1), member);
        testSubject.prepareForInvocation(messages.get(1), member);
        testSubject.onEventProcessingCompleted(messages);
        testSubject.onEventProcessingFailed(messages.subList(0, 1), cause);
        testSubject.onEventProcessingCompleted(messages.subList(1, 2));
        testSubject.onEventProcessingCompleted(messages);
        verify(mockMonitor, never()).onEventProcessingCompleted(isA(List.class));
        verify(mockMonitor, never()).onEventProcessingFailed(isA(List.class), any(Throwable.class));

        testSubject.onEventProcessingFailed(messages, cause);

        verify(mockMonitor).onEventProcessingFailed(messages, cause);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPreparingForEventIsRequiredBeforeRegisteringAsyncInvocation() {
        testSubject.prepareForInvocation(new GenericEventMessage("test"), member);
    }

    private interface AsyncEventListener extends EventListener, EventProcessingMonitorSupport {

    }
}
