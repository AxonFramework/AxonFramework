/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.command;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandProviderInbound;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.*;
import org.axonframework.commandhandling.*;
import org.axonframework.common.Registration;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.axonserver.connector.ErrorCode.UNSUPPORTED_INSTRUCTION;
import static org.axonframework.axonserver.connector.TestTargetContextResolver.BOUNDED_CONTEXT;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test class to cover all the operations performed by the {@link AxonServerCommandBus}.
 *
 * @author Marc Gathier
 */
public class AxonServerCommandBusTest {

    private DummyMessagePlatformServer dummyMessagePlatformServer;

    private AxonServerConfiguration configuration;
    private Serializer serializer = XStreamSerializer.defaultSerializer();
    private SimpleCommandBus localSegment = SimpleCommandBus.builder().build();
    private AxonServerConnectionManager axonServerConnectionManager;
    private TargetContextResolver<CommandMessage<?>> targetContextResolver = spy(new TestTargetContextResolver<>());

    private AxonServerCommandBus testSubject;

    @Before
    public void setup() throws Exception {
        dummyMessagePlatformServer = new DummyMessagePlatformServer(4344);
        dummyMessagePlatformServer.start();

        configuration = new AxonServerConfiguration();
        configuration.setServers("localhost:4344");
        configuration.setClientId("JUnit");
        configuration.setComponentName("JUnit");
        configuration.setInitialNrOfPermits(100);
        configuration.setNewPermitsThreshold(10);
        configuration.setNrOfNewPermits(1000);
        configuration.setContext(BOUNDED_CONTEXT);
        axonServerConnectionManager = spy(AxonServerConnectionManager.builder()
                                                                     .axonServerConfiguration(configuration)
                                                                     .build());
        testSubject = AxonServerCommandBus.builder()
                                          .axonServerConnectionManager(axonServerConnectionManager)
                                          .configuration(configuration)
                                          .localSegment(localSegment)
                                          .serializer(serializer)
                                          .routingStrategy(command -> "RoutingKey")
                                          .targetContextResolver(targetContextResolver)
                                          .build();
    }

    @After
    public void tearDown() {
        dummyMessagePlatformServer.stop();
        axonServerConnectionManager.shutdown();
        testSubject.disconnect();
    }

    @Test
    public void dispatch() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is the payload");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            if (result.isExceptional()) {
                failure.set(result.exceptionResult());
            } else {
                resultHolder.set(result.getPayload());
            }
            waiter.countDown();
        });

        waiter.await();
        assertNull(failure.get());
        assertEquals("this is the payload", resultHolder.get());

        verify(targetContextResolver).resolveContext(commandMessage);
        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    public void fireAndForgetUsesDefaultCallback() throws InterruptedException {
        testSubject.disconnect();
        CommandCallback<Object, Object> mockDefaultCommandCallback = mock(CommandCallback.class);
        testSubject = AxonServerCommandBus.builder()
                                          .axonServerConnectionManager(axonServerConnectionManager)
                                          .configuration(configuration)
                                          .localSegment(localSegment)
                                          .serializer(serializer)
                                          .routingStrategy(command -> "RoutingKey")
                                          .targetContextResolver(targetContextResolver)
                                          .defaultCommandCallback(mockDefaultCommandCallback)
                                          .build();

        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is the payload");
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(i -> {
            cdl.countDown();
            return null;
        }).when(mockDefaultCommandCallback).onResult(any(), any());

        testSubject.dispatch(commandMessage);

        assertTrue("Expected default callback to have been invoked", cdl.await(1, TimeUnit.SECONDS));
        verify(mockDefaultCommandCallback).onResult(eq(commandMessage), any());
    }

    @Test
    public void dispatchWhenChannelThrowsAnException() throws InterruptedException {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is the payload");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicBoolean failure = new AtomicBoolean(false);
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        when(axonServerConnectionManager.getChannel(anyString())).thenThrow(new RuntimeException("oops"));

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            if (result.isExceptional()) {
                failure.set(true);
                throwable.set(result.exceptionResult());
            }
            waiter.countDown();
        });

        waiter.await();
        assertTrue(failure.get());
        assertTrue(throwable.get() instanceof AxonServerCommandDispatchException);
        assertEquals(ErrorCode.COMMAND_DISPATCH_ERROR.errorCode(),
                     ((AxonServerCommandDispatchException) throwable.get()).getErrorCode());

        verify(targetContextResolver).resolveContext(commandMessage);
        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    public void dispatchWithError() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is an error request");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicBoolean failure = new AtomicBoolean(false);

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            if (result.isExceptional()) {
                failure.set(true);
            } else {
                resultHolder.set(result.getPayload());
            }
            waiter.countDown();
        });

        waiter.await();
        assertTrue(failure.get());

        verify(targetContextResolver).resolveContext(commandMessage);
        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    public void dispatchWithConcurrencyException() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is a concurrency issue");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<CommandResultMessage> resultHolder = new AtomicReference<>();

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            resultHolder.set(result);
            waiter.countDown();
        });

        waiter.await();
        assertTrue(resultHolder.get().isExceptional());
        assertTrue(resultHolder.get().exceptionResult() instanceof ConcurrencyException);

        verify(targetContextResolver).resolveContext(commandMessage);
        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    public void dispatchWithExceptionFromHandler() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("give me an exception");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<CommandResultMessage> resultHolder = new AtomicReference<>();

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            resultHolder.set(result);
            waiter.countDown();
        });

        waiter.await();
        assertTrue(resultHolder.get().isExceptional());
        assertEquals(CommandExecutionException.class, resultHolder.get().exceptionResult().getClass());
        assertEquals(
                "give me an exception",
                ((CommandExecutionException) resultHolder.get().exceptionResult()).getDetails().orElse(null)
        );

        verify(targetContextResolver).resolveContext(commandMessage);
        verify(axonServerConnectionManager).getChannel(BOUNDED_CONTEXT);
    }

    @Test
    public void subscribe() {
        Registration registration = testSubject.subscribe(String.class.getName(), c -> "Done");
        assertWithin(100, TimeUnit.MILLISECONDS, () ->
                assertNotNull(dummyMessagePlatformServer.subscriptions(String.class.getName())));
        registration.cancel();
        assertWithin(100, TimeUnit.MILLISECONDS, () ->
                assertNull(dummyMessagePlatformServer.subscriptions(String.class.getName())));
    }

    @Test
    public void processCommand() {
        AxonServerConnectionManager mockAxonServerConnectionManager = mock(AxonServerConnectionManager.class);
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            inboundStreamObserverRef.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<CommandProviderInbound>();
        }).when(mockAxonServerConnectionManager).getCommandStream(any(), any());
        AxonServerCommandBus testSubject2 =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(mockAxonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .requestStreamFactory(os -> new TestStreamObserver<>())
                                    .routingStrategy(command -> "RoutingKey")
                                    .build();
        testSubject2.subscribe(String.class.getName(), c -> c.getMetaData().get("test1"));

        SerializedObject commandPayload =
                SerializedObject.newBuilder()
                                .setType(String.class.getName())
                                .setData(ByteString.copyFromUtf8("<string>test</string>"))
                                .build();
        Command command = Command.newBuilder()
                                 .setName(String.class.getName())
                                 .setPayload(commandPayload)
                                 .putMetaData("test1", MetaDataValue.newBuilder().setTextValue("Text").build())
                                 .build();
        String instructionId = "instructionId";
        inboundStreamObserverRef.get().onNext(CommandProviderInbound.newBuilder()
                                                                    .setInstructionId(instructionId)
                                                                    .setCommand(command)
                                                                    .build());

        //noinspection unchecked
        verify(mockAxonServerConnectionManager).getCommandStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    public void unsupportedCommandInstruction() {
        AxonServerConnectionManager mockAxonServerConnectionManager = mock(AxonServerConnectionManager.class);
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            inboundStreamObserverRef.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<CommandProviderInbound>();
        }).when(mockAxonServerConnectionManager).getCommandStream(any(), any());
        TestStreamObserver<CommandProviderOutbound> requestStream = new TestStreamObserver<>();
        AxonServerCommandBus testSubject2 =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(mockAxonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .requestStreamFactory(os -> requestStream)
                                    .routingStrategy(command -> "RoutingKey")
                                    .build();
        testSubject2.subscribe(String.class.getName(), c -> c.getMetaData().get("test1"));

        String instructionId = "instructionId";
        inboundStreamObserverRef.get().onNext(CommandProviderInbound.newBuilder()
                                                                    .setInstructionId(instructionId)
                                                                    .build());

        assertTrue(requestStream.sentMessages()
                                .stream()
                                .anyMatch(outbound -> outbound.getRequestCase()
                                                              .equals(CommandProviderOutbound.RequestCase.ACK)
                                        && !outbound.getAck().getSuccess()
                                        && outbound.getAck().getError().getErrorCode()
                                                   .equals(UNSUPPORTED_INSTRUCTION.errorCode())
                                        && outbound.getAck().getInstructionId().equals(instructionId)));
    }

    @Test
    public void unsupportedCommandInstructionWithoutInstructionId() {
        AxonServerConnectionManager mockAxonServerConnectionManager = mock(AxonServerConnectionManager.class);
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            inboundStreamObserverRef.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<CommandProviderInbound>();
        }).when(mockAxonServerConnectionManager).getCommandStream(any(), any());
        TestStreamObserver<CommandProviderOutbound> requestStream = new TestStreamObserver<>();
        AxonServerCommandBus testSubject2 =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(mockAxonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .requestStreamFactory(os -> requestStream)
                                    .routingStrategy(command -> "RoutingKey")
                                    .build();
        testSubject2.subscribe(String.class.getName(), c -> c.getMetaData().get("test1"));

        inboundStreamObserverRef.get().onNext(CommandProviderInbound.newBuilder().build());

        assertEquals(0, requestStream.sentMessages().size());
    }

    @Test
    public void resubscribe() throws Exception {
        testSubject.subscribe(String.class.getName(), c -> "Done");
        assertWithin(
                1,
                TimeUnit.SECONDS,
                () -> assertNotNull(dummyMessagePlatformServer.subscriptions(String.class.getName()))
        );

        reset(axonServerConnectionManager);
        dummyMessagePlatformServer.stop();
        assertNull(dummyMessagePlatformServer.subscriptions(String.class.getName()));

        dummyMessagePlatformServer.start();
        assertWithin(
                5,
                TimeUnit.SECONDS,
                () -> assertNotNull(dummyMessagePlatformServer.subscriptions(String.class.getName()))
        );

        //noinspection unchecked
        verify(axonServerConnectionManager, atLeastOnce()).getCommandStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    public void dispatchInterceptor() {
        List<Object> results = new LinkedList<>();
        testSubject.registerDispatchInterceptor(messages -> (a, b) -> {
            results.add(b.getPayload());
            return b;
        });
        testSubject.dispatch(new GenericCommandMessage<>("payload"));
        assertEquals("payload", results.get(0));
        assertEquals(1, results.size());
    }

    @Test
    public void reconnectAfterConnectionLost() {
        testSubject.subscribe(String.class.getName(), c -> "Done");
        assertWithin(
                1,
                TimeUnit.SECONDS,
                () -> assertNotNull(dummyMessagePlatformServer.subscriptions(String.class.getName()))
        );

        reset(axonServerConnectionManager);
        dummyMessagePlatformServer.simulateError(String.class.getName());
        assertWithin(
                2,
                TimeUnit.SECONDS,
                () -> assertNotNull(dummyMessagePlatformServer.subscriptions(String.class.getName()))
        );

        //noinspection unchecked
        verify(axonServerConnectionManager, atLeastOnce()).getCommandStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }
}
