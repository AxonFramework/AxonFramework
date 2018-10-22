/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.monitoring;

import org.axonframework.messaging.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class MultiMessageMonitorTest {

    @Test
    public void test_onMessageIngested_SingleMessageMonitor_failure(){
        MessageMonitor<Message<?>> messageMonitorMock = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback = mock(MessageMonitor.MonitorCallback.class);
        MultiMessageMonitor multiMessageMonitor = new MultiMessageMonitor(Arrays.asList(messageMonitorMock));
        Message messageMock = mock(Message.class);
        Mockito.when(messageMonitorMock.onMessageIngested(messageMock)).thenReturn(callback);

        MessageMonitor.MonitorCallback monitorCallback = multiMessageMonitor.onMessageIngested(messageMock);
        Throwable throwable = new Throwable();
        monitorCallback.reportFailure(throwable);

        Mockito.verify(messageMonitorMock).onMessageIngested(ArgumentMatchers.same(messageMock));
        Mockito.verify(callback).reportFailure(ArgumentMatchers.same(throwable));
    }

    @Test
    public void test_onMessageIngested_SingleMessageMonitor_success(){
        MessageMonitor<Message<?>> messageMonitorMock = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback = mock(MessageMonitor.MonitorCallback.class);
        MultiMessageMonitor multiMessageMonitor = new MultiMessageMonitor(Arrays.asList(messageMonitorMock));
        Message messageMock = mock(Message.class);
        Mockito.when(messageMonitorMock.onMessageIngested(messageMock)).thenReturn(callback);

        MessageMonitor.MonitorCallback monitorCallback = multiMessageMonitor.onMessageIngested(messageMock);
        monitorCallback.reportSuccess();

        Mockito.verify(messageMonitorMock).onMessageIngested(ArgumentMatchers.same(messageMock));
        Mockito.verify(callback).reportSuccess();
    }

    @Test
    public void test_onMessageIngested_MultipleMessageMonitors(){
        MessageMonitor<Message<?>> messageMonitorMock1 = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback1 = mock(MessageMonitor.MonitorCallback.class);
        MessageMonitor<Message<?>> messageMonitorMock2 = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback2 = mock(MessageMonitor.MonitorCallback.class);
        MultiMessageMonitor multiMessageMonitor = new MultiMessageMonitor(Arrays.asList(messageMonitorMock1, messageMonitorMock2));
        Message messageMock = mock(Message.class);
        Mockito.when(messageMonitorMock1.onMessageIngested(messageMock)).thenReturn(callback1);
        Mockito.when(messageMonitorMock2.onMessageIngested(messageMock)).thenReturn(callback2);

        multiMessageMonitor.onMessageIngested(messageMock).reportSuccess();

        Mockito.verify(messageMonitorMock1).onMessageIngested(ArgumentMatchers.same(messageMock));
        Mockito.verify(callback1).reportSuccess();
        Mockito.verify(messageMonitorMock2).onMessageIngested(ArgumentMatchers.same(messageMock));
        Mockito.verify(callback2).reportSuccess();
    }
}
