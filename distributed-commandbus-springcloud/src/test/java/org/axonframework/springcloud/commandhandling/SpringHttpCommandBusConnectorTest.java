package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;

import static java.util.Collections.singletonMap;
import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringHttpCommandBusConnectorTest {

    private static final String MEMBER_NAME = "memberName";
    private static final URI ENDPOINT = URI.create("endpoint");
    private static final Member DESTINATION = new SimpleMember<>(MEMBER_NAME, ENDPOINT, false, null);
    private static final CommandMessage<String> COMMAND_MESSAGE =
            new GenericCommandMessage<>("command", singletonMap("commandKey", "commandValue"));

    private static final CommandResultMessage<String> COMMAND_RESULT =
            new GenericCommandResultMessage<>("result", singletonMap("commandResultKey", "CommandResultValue"));
    private static final Exception COMMAND_ERROR = new Exception("oops");

    private SpringHttpCommandBusConnector testSubject;
    @Mock
    private CommandBus localCommandBus;
    @Mock
    private RestTemplate restTemplate;
    private Serializer serializer;

    private URI expectedUri;
    @Mock
    private CommandCallback<String, String> commandCallback;
    @Mock
    private MessageHandler<? super CommandMessage<?>> messageHandler;

    @Before
    public void setUp() throws Exception {
        serializer = spy(JacksonSerializer.builder().build());
        expectedUri = new URI(ENDPOINT.getScheme(),
                              ENDPOINT.getUserInfo(),
                              ENDPOINT.getHost(),
                              ENDPOINT.getPort(),
                              ENDPOINT.getPath() + "/spring-command-bus-connector/command",
                              null,
                              null);

        testSubject = SpringHttpCommandBusConnector.builder()
                                                   .localCommandBus(localCommandBus)
                                                   .restOperations(restTemplate)
                                                   .serializer(serializer)
                                                   .build();
    }

    @Test
    public void testSendWithoutCallbackSucceeds() {
        HttpEntity<SpringHttpDispatchMessage> expectedHttpEntity = new HttpEntity<>(buildDispatchMessage(false));

        testSubject.send(DESTINATION, COMMAND_MESSAGE);

        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST),
                                      eq(expectedHttpEntity), argThat(new ParameterizedTypeReferenceMatcher<>()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendWithoutCallbackThrowsExceptionForMissingDestinationURI() {
        SimpleMember<String> faultyDestination = new SimpleMember<>(MEMBER_NAME, null, false, null);
        testSubject.send(faultyDestination, COMMAND_MESSAGE);
    }

    @Test
    public void testSendWithCallbackSucceedsAndReturnsSucceeded() {
        HttpEntity<SpringHttpDispatchMessage> expectedHttpEntity = new HttpEntity<>(buildDispatchMessage(true));
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(), true, COMMAND_RESULT, serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri),
                                   eq(HttpMethod.POST),
                                   eq(expectedHttpEntity),
                                   argThat(new ParameterizedTypeReferenceMatcher<String>()))
        ).thenReturn(testResponseEntity);

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                                      argThat(new ParameterizedTypeReferenceMatcher<>()));

        SerializedObject<byte[]> serializedPayload = serializer.serialize(COMMAND_RESULT.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetaData = serializer.serialize(COMMAND_RESULT.getMetaData(), byte[].class);
        //noinspection unchecked
        ArgumentCaptor<SerializedObject<byte[]>> serializedObjectCaptor =
                ArgumentCaptor.forClass(SerializedObject.class);
        verify(serializer, times(2)).deserialize(serializedObjectCaptor.capture());

        assertEquals(serializedPayload.getType(), serializedObjectCaptor.getAllValues().get(0).getType());
        assertEquals(serializedPayload.getContentType(), serializedObjectCaptor.getAllValues().get(0).getContentType());
        assertTrue(Arrays.equals(serializedPayload.getData(), serializedObjectCaptor.getAllValues().get(0).getData()));

        assertEquals(serializedMetaData.getType(), serializedObjectCaptor.getAllValues().get(1).getType());
        assertEquals(serializedMetaData.getContentType(), serializedObjectCaptor.getAllValues().get(1).getContentType());
        assertTrue(Arrays.equals(serializedMetaData.getData(), serializedObjectCaptor.getAllValues().get(1).getData()));

        //noinspection unchecked
        ArgumentCaptor<CommandMessage<? extends String>> commandMessageArgumentCaptor =
                ArgumentCaptor.forClass(CommandMessage.class);
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<? extends String>> commandResultMessageArgumentCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(commandCallback).onSuccess(commandMessageArgumentCaptor.capture(),
                                          commandResultMessageArgumentCaptor.capture());
        assertEquals(COMMAND_MESSAGE.getMetaData(), commandMessageArgumentCaptor.getValue().getMetaData());
        assertEquals(COMMAND_MESSAGE.getPayload(), commandMessageArgumentCaptor.getValue().getPayload());
        assertEquals(COMMAND_RESULT.getMetaData(), commandResultMessageArgumentCaptor.getValue().getMetaData());
        assertEquals(COMMAND_RESULT.getPayload(), commandResultMessageArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void testSendWithCallbackSucceedsAndReturnsFailed() {
        HttpEntity<SpringHttpDispatchMessage> expectedHttpEntity = new HttpEntity<>(buildDispatchMessage(true));
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(),
                                             false,
                                             asCommandResultMessage(COMMAND_ERROR),
                                             serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri),
                                   eq(HttpMethod.POST),
                                   eq(expectedHttpEntity),
                                   argThat(new ParameterizedTypeReferenceMatcher<String>()))
        ).thenReturn(testResponseEntity);

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                                      argThat(new ParameterizedTypeReferenceMatcher<>()));
        SerializedObject<byte[]> serializedObject = serializer.serialize(COMMAND_ERROR, byte[].class);
        //noinspection unchecked
        ArgumentCaptor<SerializedObject<byte[]>> serializedObjectCaptor =
                ArgumentCaptor.forClass(SerializedObject.class);
        verify(serializer).deserialize(serializedObjectCaptor.capture());
        assertEquals(serializedObject.getType(), serializedObjectCaptor.getValue().getType());
        assertEquals(serializedObject.getContentType(), serializedObjectCaptor.getValue().getContentType());
        assertTrue(Arrays.equals(serializedObject.getData(), serializedObjectCaptor.getValue().getData()));
        verify(commandCallback).onFailure(eq(COMMAND_MESSAGE), any(Exception.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendWithCallbackThrowsExceptionForMissingDestinationURI() {
        SimpleMember<String> faultyDestination = new SimpleMember<>(MEMBER_NAME, null, false, null);
        testSubject.send(faultyDestination, COMMAND_MESSAGE, new NoOpCallback());
    }

    @Test
    public void testSubscribeSubscribesCommandHandlerForCommandNameToLocalCommandBus() {
        String expectedCommandName = "commandName";

        testSubject.subscribe(expectedCommandName, messageHandler);

        verify(localCommandBus).subscribe(expectedCommandName, messageHandler);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReceiveCommandHandlesCommandWithCallbackSucceedsAndCallsSuccess() throws Exception {
        doAnswer(a -> {
            SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback<String, String> callback =
                    (SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback) a.getArguments()[1];
            callback.onSuccess(COMMAND_MESSAGE, COMMAND_RESULT);
            return a;
        }).when(localCommandBus).dispatch(any(), any());

        SpringHttpReplyMessage result =
                (SpringHttpReplyMessage) testSubject.receiveCommand(buildDispatchMessage(true)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertTrue(result.isSuccess());
        CommandResultMessage commandResultMessage = result.getCommandResultMessage(serializer);
        assertEquals(COMMAND_RESULT.getPayload(), commandResultMessage.getPayload());
        assertEquals(COMMAND_RESULT.getMetaData(), commandResultMessage.getMetaData());

        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    public void testReceiveCommandHandlesCommandWithCallbackSucceedsAndCallsFailure() throws Exception {
        doAnswer(a -> {
            SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback callback =
                    (SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback) a.getArguments()[1];
            callback.onFailure(COMMAND_MESSAGE, COMMAND_ERROR);
            return a;
        }).when(localCommandBus).dispatch(any(), any());

        SpringHttpReplyMessage result =
                (SpringHttpReplyMessage) testSubject.receiveCommand(buildDispatchMessage(true)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertFalse(result.isSuccess());
        assertEquals(COMMAND_ERROR.getMessage(), result.getError(serializer).getMessage());

        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    public void testReceiveCommandHandlesCommandWithCallbackFails() throws Exception {
        doThrow(RuntimeException.class).when(localCommandBus).dispatch(any(), any());

        SpringHttpReplyMessage result =
                (SpringHttpReplyMessage) testSubject.receiveCommand(buildDispatchMessage(true)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertFalse(result.isSuccess());

        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    public void testReceiveCommandHandlesCommandWithoutCallback() throws Exception {
        String result = (String) testSubject.receiveCommand(buildDispatchMessage(false)).get();

        assertEquals("", result);

        verify(localCommandBus).dispatch(any());
    }

    @Test
    public void testReceiveCommandHandlesCommandWithoutCallbackThrowsException() throws Exception {
        doThrow(RuntimeException.class).when(localCommandBus).dispatch(any());

        SpringHttpReplyMessage result =
                (SpringHttpReplyMessage) testSubject.receiveCommand(buildDispatchMessage(false)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertFalse(result.isSuccess());

        verify(localCommandBus).dispatch(any());
    }

    @Test
    public void testSendWithCallbackToLocalMember() {
        SimpleMember<String> localDestination = new SimpleMember<>(MEMBER_NAME, null, true, null);
        testSubject.send(localDestination, COMMAND_MESSAGE, new NoOpCallback());

        verifyNoMoreInteractions(restTemplate);
        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    public void testSendWithoutCallbackToLocalMember() {
        SimpleMember<String> localDestination = new SimpleMember<>(MEMBER_NAME, null, true, null);
        testSubject.send(localDestination, COMMAND_MESSAGE);

        verifyNoMoreInteractions(restTemplate);
        verify(localCommandBus).dispatch(any());
    }


    private <C> SpringHttpDispatchMessage<C> buildDispatchMessage(boolean expectReply) {
        return new SpringHttpDispatchMessage<>(COMMAND_MESSAGE, serializer, expectReply);
    }

    private class ParameterizedTypeReferenceMatcher<R> implements
            ArgumentMatcher<ParameterizedTypeReference<SpringHttpReplyMessage<R>>> {

        private ParameterizedTypeReference<SpringHttpReplyMessage<R>> expected =
                new ParameterizedTypeReference<SpringHttpReplyMessage<R>>() {
                };

        @Override
        public boolean matches(ParameterizedTypeReference<SpringHttpReplyMessage<R>> actual) {
            return actual != null &&
                    actual.getType().getTypeName()
                          .equals(expected.getType().getTypeName());
        }
    }
}
