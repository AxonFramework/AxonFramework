/*
 * Copyright (c) 2010-2016. Axon Framework
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
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class MultiMessageMonitorTest {

    @Test
    public void test_onMessageIngested_SingleMessageMonitor_failure(){
        MessageMonitor<Message<?>> messageMonitorMock = Mockito.mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback = Mockito.mock(MessageMonitor.MonitorCallback.class);
        MultiMessageMonitor multiMessageMonitor = new MultiMessageMonitor(Arrays.asList(messageMonitorMock));
        Message messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMonitorMock.onMessageIngested(messageMock)).thenReturn(callback);

        MessageMonitor.MonitorCallback monitorCallback = multiMessageMonitor.onMessageIngested(messageMock);
        Throwable throwable = new Throwable();
        monitorCallback.reportFailure(throwable);

        Mockito.verify(messageMonitorMock).onMessageIngested(Matchers.same(messageMock));
        Mockito.verify(callback).reportFailure(Matchers.same(throwable));
    }

    @Test
    public void test_onMessageIngested_SingleMessageMonitor_success(){
        MessageMonitor<Message<?>> messageMonitorMock = Mockito.mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback = Mockito.mock(MessageMonitor.MonitorCallback.class);
        MultiMessageMonitor multiMessageMonitor = new MultiMessageMonitor(Arrays.asList(messageMonitorMock));
        Message messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMonitorMock.onMessageIngested(messageMock)).thenReturn(callback);

        MessageMonitor.MonitorCallback monitorCallback = multiMessageMonitor.onMessageIngested(messageMock);
        monitorCallback.reportSuccess();

        Mockito.verify(messageMonitorMock).onMessageIngested(Matchers.same(messageMock));
        Mockito.verify(callback).reportSuccess();
    }

    @Test
    public void test_onMessageIngested_MultipleMessageMonitors(){
        MessageMonitor<Message<?>> messageMonitorMock1 = Mockito.mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback1 = Mockito.mock(MessageMonitor.MonitorCallback.class);
        MessageMonitor<Message<?>> messageMonitorMock2 = Mockito.mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback2 = Mockito.mock(MessageMonitor.MonitorCallback.class);
        MultiMessageMonitor multiMessageMonitor = new MultiMessageMonitor(Arrays.asList(messageMonitorMock1, messageMonitorMock2));
        Message messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMonitorMock1.onMessageIngested(messageMock)).thenReturn(callback1);
        Mockito.when(messageMonitorMock2.onMessageIngested(messageMock)).thenReturn(callback2);

        multiMessageMonitor.onMessageIngested(messageMock).reportSuccess();

        Mockito.verify(messageMonitorMock1).onMessageIngested(Matchers.same(messageMock));
        Mockito.verify(callback1).reportSuccess();
        Mockito.verify(messageMonitorMock2).onMessageIngested(Matchers.same(messageMock));
        Mockito.verify(callback2).reportSuccess();
    }
}
