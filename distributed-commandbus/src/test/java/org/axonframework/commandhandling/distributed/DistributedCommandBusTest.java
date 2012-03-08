package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.junit.*;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DistributedCommandBusTest {

    private CommandBus testSubject;
    private CommandBus mockLocalSegment;
    private CommandBusConnector mockConnector;
    private RoutingStrategy mockRoutingStrategy;
    private CommandHandler<Object> mockHandler;
    private CommandMessage<?> message;
    private CommandCallback<Object> callback;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        mockLocalSegment = mock(CommandBus.class);
        mockConnector = mock(CommandBusConnector.class);
        mockRoutingStrategy = mock(RoutingStrategy.class);
        when(mockRoutingStrategy.getRoutingKey(isA(CommandMessage.class))).thenReturn("key");

        testSubject = new DistributedCommandBus(mockConnector, mockRoutingStrategy);
        mockHandler = mock(CommandHandler.class);
        message = new GenericCommandMessage<Object>(new Object());
        callback = new FutureCallback<Object>();
    }

    @Test
    public void testDispatchIsDelegatedToConnection_WithCallback() throws Exception {
        testSubject.dispatch(message, callback);

        verify(mockRoutingStrategy).getRoutingKey(message);
        verify(mockConnector).send("key", message, callback);
    }

    @Test
    public void testDispatchIsDelegatedToConnection_WithoutCallback() throws Exception {
        testSubject.dispatch(message);

        verify(mockRoutingStrategy).getRoutingKey(message);
        verify(mockConnector).send("key", message);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = CommandDispatchException.class)
    public void testDispatchErrorIsPropagated_WithCallback() throws Exception {
        doThrow(new Exception()).when(mockConnector).send(anyString(),
                                                          any(CommandMessage.class),
                                                          any(CommandCallback.class));
        testSubject.dispatch(message, callback);
    }

    @Test(expected = CommandDispatchException.class)
    public void testDispatchErrorIsPropagated_WithoutCallback() throws Exception {
        doThrow(new Exception()).when(mockConnector).send(anyString(), any(CommandMessage.class));
        testSubject.dispatch(message);
    }

    @Test
    public void testSubscribeIsDoneOnConnector() {
        testSubject.subscribe(Object.class, mockHandler);

        verify(mockConnector).subscribe(Object.class, mockHandler);
    }

    @Test
    public void testUnsubscribeIsDoneOnConnector() {
        testSubject.unsubscribe(Object.class, mockHandler);

        verify(mockConnector).unsubscribe(Object.class, mockHandler);
    }
}
