package org.axonframework.metrics;

import org.axonframework.messaging.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DelegatingMessageMonitorTest {

    @Test
    public void test_onMessageIngested_SingleMessageMonitor_failure(){
        MessageMonitor<Message<?>> messageMonitorMock = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback = mock(MessageMonitor.MonitorCallback.class);
        DelegatingMessageMonitor delegatingMessageMonitor = new DelegatingMessageMonitor(Arrays.asList(messageMonitorMock));
        Message messageMock = mock(Message.class);
        when(messageMonitorMock.onMessageIngested(messageMock)).thenReturn(callback);

        MessageMonitor.MonitorCallback monitorCallback = delegatingMessageMonitor.onMessageIngested(messageMock);
        Optional<Throwable> runtimeException = Optional.of(new RuntimeException());
        monitorCallback.onFailure(runtimeException);

        verify(messageMonitorMock).onMessageIngested(same(messageMock));
        verify(callback).onFailure(same(runtimeException));
    }

    @Test
    public void test_onMessageIngested_SingleMessageMonitor_success(){
        MessageMonitor<Message<?>> messageMonitorMock = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback = mock(MessageMonitor.MonitorCallback.class);
        DelegatingMessageMonitor delegatingMessageMonitor = new DelegatingMessageMonitor(Arrays.asList(messageMonitorMock));
        Message messageMock = mock(Message.class);
        when(messageMonitorMock.onMessageIngested(messageMock)).thenReturn(callback);

        MessageMonitor.MonitorCallback monitorCallback = delegatingMessageMonitor.onMessageIngested(messageMock);
        monitorCallback.onSuccess();

        verify(messageMonitorMock).onMessageIngested(same(messageMock));
        verify(callback).onSuccess();
    }

    @Test
    public void test_onMessageIngested_MultipleMessageMonitors(){
        MessageMonitor<Message<?>> messageMonitorMock1 = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback1 = mock(MessageMonitor.MonitorCallback.class);
        MessageMonitor<Message<?>> messageMonitorMock2 = mock(MessageMonitor.class);
        MessageMonitor.MonitorCallback callback2 = mock(MessageMonitor.MonitorCallback.class);
        DelegatingMessageMonitor delegatingMessageMonitor = new DelegatingMessageMonitor(Arrays.asList(messageMonitorMock1, messageMonitorMock2));
        Message messageMock = mock(Message.class);
        when(messageMonitorMock1.onMessageIngested(messageMock)).thenReturn(callback1);
        when(messageMonitorMock2.onMessageIngested(messageMock)).thenReturn(callback2);

        delegatingMessageMonitor.onMessageIngested(messageMock).onSuccess();

        verify(messageMonitorMock1).onMessageIngested(same(messageMock));
        verify(callback1).onSuccess();
        verify(messageMonitorMock2).onMessageIngested(same(messageMock));
        verify(callback2).onSuccess();
    }
}