package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.SimpleMember;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.*;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringHttpCommandBusConnectorTest {

    private static final String MEMBER_NAME = "memberName";
    private static final URI ENDPOINT = URI.create("endpoint");
    private static final Member DESTINATION = new SimpleMember<>(MEMBER_NAME, ENDPOINT, null);
    private static final CommandMessage<String> COMMAND_MESSAGE = GenericCommandMessage.asCommandMessage("command");

    private static final byte[] SERIALIZED_COMMAND_METADATA = {};
    private static final byte[] SERIALIZED_COMMAND_PAYLOAD = {};
    private static final String COMMAND_RESULT = "result";
    private static final byte[] SERIALIZED_RESULT_DATA = new byte[]{};
    private static final Exception COMMAND_ERROR = new Exception();
    private static final byte[] SERIALIZED_ERROR_DATA = {};

    @InjectMocks
    private SpringHttpCommandBusConnector testSubject;
    @Mock
    private CommandBus localCommandBus;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private Serializer serializer;

    private URI expectedUri;
    @Mock
    private SerializedObject<byte[]> serializedMetaData;
    @Mock
    private SerializedObject<byte[]> serializedPayload;
    @Mock
    private SerializedObject<byte[]> serializedResult;
    @Mock
    private SerializedObject<byte[]> serializedError;
    @Mock
    private CommandCallback<String, String> commandCallback;
    @Mock
    private MessageHandler<? super CommandMessage<?>> messageHandler;

    @Before
    public void setUp() throws Exception {
        expectedUri = new URI(ENDPOINT.getScheme(), ENDPOINT.getUserInfo(), ENDPOINT.getHost(),
                ENDPOINT.getPort(), "/spring-command-bus-connector/command", null, null);

        when(serializedMetaData.getContentType()).thenReturn(byte[].class);
        when(serializedMetaData.getData()).thenReturn(SERIALIZED_COMMAND_METADATA);

        SerializedType serializedPayloadType = mock(SerializedType.class);
        when(serializedPayloadType.getName()).thenReturn(String.class.getName());
        when(serializedPayloadType.getRevision()).thenReturn(null);
        when(serializedPayload.getType()).thenReturn(serializedPayloadType);
        when(serializedPayload.getContentType()).thenReturn(byte[].class);
        when(serializedPayload.getData()).thenReturn(SERIALIZED_COMMAND_PAYLOAD);

        SerializedType serializedResultType = mock(SerializedType.class);
        when(serializedResultType.getName()).thenReturn(String.class.getName());
        when(serializedResultType.getRevision()).thenReturn(null);
        when(serializedResult.getType()).thenReturn(serializedResultType);
        when(serializedResult.getData()).thenReturn(SERIALIZED_RESULT_DATA);

        SerializedType serializedErrorType = mock(SerializedType.class);
        when(serializedErrorType.getName()).thenReturn(Exception.class.getName());
        when(serializedErrorType.getRevision()).thenReturn(null);
        when(serializedError.getType()).thenReturn(serializedErrorType);
        when(serializedError.getData()).thenReturn(SERIALIZED_ERROR_DATA);

        when(serializer.serialize(COMMAND_MESSAGE.getMetaData(), byte[].class)).thenReturn(serializedMetaData);
        when(serializer.serialize(COMMAND_MESSAGE.getPayload(), byte[].class)).thenReturn(serializedPayload);
        when(serializer.serialize(COMMAND_RESULT, byte[].class)).thenReturn(serializedResult);
        when(serializer.serialize(COMMAND_ERROR, byte[].class)).thenReturn(serializedError);
        when(serializer.deserialize(new SimpleSerializedObject<>(SERIALIZED_COMMAND_PAYLOAD, byte[].class,
                String.class.getName(), null))).thenReturn(COMMAND_MESSAGE.getPayload());
        when(serializer.deserialize(new SerializedMetaData<>(SERIALIZED_COMMAND_METADATA, byte[].class)))
                .thenReturn(COMMAND_MESSAGE.getMetaData());
        when(serializer.getConverter()).thenReturn(new ChainingConverter());
    }

    @Test
    public void testSendWithoutCallbackSucceeds() throws Exception {
        HttpEntity<SpringHttpDispatchMessage> expectedHttpEntity = new HttpEntity<>(buildDispatchMessage(false));

        testSubject.send(DESTINATION, COMMAND_MESSAGE);

        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST),
                eq(expectedHttpEntity), argThat(new ParameterizedTypeReferenceMatcher<>()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendWithoutCallbackThrowsExceptionForMissingDestinationURI() throws Exception {
        SimpleMember<String> faultyDestination = new SimpleMember<>(MEMBER_NAME, null, null);
        testSubject.send(faultyDestination, COMMAND_MESSAGE);
    }

    @Test
    public void testSendWithCallbackSucceedsAndReturnsSucceeded() throws Exception {
        SimpleSerializedObject<byte[]> expectedSerializedResult =
                new SimpleSerializedObject<>(SERIALIZED_RESULT_DATA, byte[].class, String.class.getName(), null);
        when(serializer.deserialize(expectedSerializedResult)).thenReturn(COMMAND_RESULT);

        HttpEntity<SpringHttpDispatchMessage> expectedHttpEntity = new HttpEntity<>(buildDispatchMessage(true));
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(), true, COMMAND_RESULT, serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                argThat(new ParameterizedTypeReferenceMatcher<String>()))).thenReturn(testResponseEntity);

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                argThat(new ParameterizedTypeReferenceMatcher<>()));
        verify(serializer).deserialize(expectedSerializedResult);
        verify(commandCallback).onSuccess(COMMAND_MESSAGE, COMMAND_RESULT);
    }

    @Test
    public void testSendWithCallbackSucceedsAndReturnsFailed() throws Exception {
        SimpleSerializedObject<byte[]> expectedSerializedError =
                new SimpleSerializedObject<>(SERIALIZED_ERROR_DATA, byte[].class, Exception.class.getName(), null);
        when(serializer.deserialize(expectedSerializedError)).thenReturn(COMMAND_ERROR);

        HttpEntity<SpringHttpDispatchMessage> expectedHttpEntity = new HttpEntity<>(buildDispatchMessage(true));
        SpringHttpReplyMessage<String> testReplyMessage =
                new SpringHttpReplyMessage<>(COMMAND_MESSAGE.getIdentifier(), false, COMMAND_ERROR, serializer);
        ResponseEntity<SpringHttpReplyMessage<String>> testResponseEntity =
                new ResponseEntity<>(testReplyMessage, HttpStatus.OK);
        when(restTemplate.exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                argThat(new ParameterizedTypeReferenceMatcher<String>()))).thenReturn(testResponseEntity);

        testSubject.send(DESTINATION, COMMAND_MESSAGE, commandCallback);

        verify(serializer).serialize(COMMAND_MESSAGE.getMetaData(), byte[].class);
        verify(serializer).serialize(COMMAND_MESSAGE.getPayload(), byte[].class);
        verify(restTemplate).exchange(eq(expectedUri), eq(HttpMethod.POST), eq(expectedHttpEntity),
                argThat(new ParameterizedTypeReferenceMatcher<>()));
        verify(serializer).deserialize(expectedSerializedError);
        verify(commandCallback).onFailure(COMMAND_MESSAGE, COMMAND_ERROR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tesSendWithCallbackThrowsExceptionForMissingDestinationURI() throws Exception {
        SimpleMember<String> faultyDestination = new SimpleMember<>(MEMBER_NAME, null, null);
        testSubject.send(faultyDestination, COMMAND_MESSAGE, new NoOpCallback());
    }

    @Test
    public void testSubscribeSubscribesCommandHandlerForCommandNameToLocalCommandBus() throws Exception {
        String expectedCommandName = "commandName";

        testSubject.subscribe(expectedCommandName, messageHandler);

        verify(localCommandBus).subscribe(expectedCommandName, messageHandler);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReceiveCommandHandlesCommandWithCallbackSucceedsAndCallsSuccess() throws Exception {
        SimpleSerializedObject<byte[]> expectedSerializedResult =
                new SimpleSerializedObject<>(SERIALIZED_RESULT_DATA, byte[].class, String.class.getName(), null);
        when(serializer.deserialize(expectedSerializedResult)).thenReturn(COMMAND_RESULT);
        doAnswer(a -> {
            SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback<String,String> callback =
                    (SpringHttpCommandBusConnector.SpringHttpReplyFutureCallback) a.getArguments()[1];
            callback.onSuccess(COMMAND_MESSAGE, COMMAND_RESULT);
            return a;
        }).when(localCommandBus).dispatch(any(), any());

        SpringHttpReplyMessage result =
                (SpringHttpReplyMessage) testSubject.receiveCommand(buildDispatchMessage(true)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertTrue(result.isSuccess());
        assertEquals(COMMAND_RESULT, result.getReturnValue(serializer));

        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    public void testReceiveCommandHandlesCommandWithCallbackSucceedsAndCallsFailure() throws Exception {
        SimpleSerializedObject<byte[]> expectedSerializedError =
                new SimpleSerializedObject<>(SERIALIZED_ERROR_DATA, byte[].class, Exception.class.getName(), null);
        when(serializer.deserialize(expectedSerializedError)).thenReturn(COMMAND_ERROR);
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
        assertEquals(COMMAND_ERROR, result.getError(serializer));

        verify(localCommandBus).dispatch(any(), any());
    }

    @Test
    public void testReceiveCommandHandlesCommandWithCallbackFails() throws Exception {
        doThrow(Exception.class).when(localCommandBus).dispatch(any(), any());

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
        doThrow(Exception.class).when(localCommandBus).dispatch(any());

        SpringHttpReplyMessage result =
                (SpringHttpReplyMessage) testSubject.receiveCommand(buildDispatchMessage(false)).get();

        assertEquals(COMMAND_MESSAGE.getIdentifier(), result.getCommandIdentifier());
        assertFalse(result.isSuccess());

        verify(localCommandBus).dispatch(any());
    }

    private <C> SpringHttpDispatchMessage<C> buildDispatchMessage(boolean expectReply) {
        return new SpringHttpDispatchMessage<>(COMMAND_MESSAGE, serializer, expectReply);
    }

    private class ParameterizedTypeReferenceMatcher<R> extends BaseMatcher<ParameterizedTypeReference<SpringHttpReplyMessage<R>>> {

        private ParameterizedTypeReference<SpringHttpReplyMessage<R>> expected =
                new ParameterizedTypeReference<SpringHttpReplyMessage<R>>() { };

        @Override
        public boolean matches(Object actual) {
            return actual instanceof ParameterizedTypeReference &&
                    ((ParameterizedTypeReference) actual).getType().getTypeName()
                            .equals(expected.getType().getTypeName());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Failed to match expected ParameterizedTypeReference [" + expected + "]");
        }

    }

}
