/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.command;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandProviderInbound;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.axoniq.axonserver.grpc.command.CommandSubscription;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.TestStreamObserver;
import org.axonframework.axonserver.connector.TestTargetContextResolver;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.ShutdownInProgressException;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.axonframework.axonserver.connector.ErrorCode.UNSUPPORTED_INSTRUCTION;
import static org.axonframework.axonserver.connector.TestTargetContextResolver.BOUNDED_CONTEXT;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test class to cover all the operations performed by the {@link AxonServerCommandBus}.
 *
 * @author Marc Gathier
 */
class AxonServerCommandBusTest {

    private DummyMessagePlatformServer dummyMessagePlatformServer;

    private AxonServerConnectionManager axonServerConnectionManager;
    private AxonServerConfiguration configuration;
    private SimpleCommandBus localSegment = SimpleCommandBus.builder().build();
    private Serializer serializer = XStreamSerializer.defaultSerializer();
    private TargetContextResolver<CommandMessage<?>> targetContextResolver = spy(new TestTargetContextResolver<>());

    private AxonServerCommandBus testSubject;

    @BeforeEach
    void setup() throws Exception {
        dummyMessagePlatformServer = new DummyMessagePlatformServer();
        dummyMessagePlatformServer.start();

        configuration = new AxonServerConfiguration();
        configuration.setServers(dummyMessagePlatformServer.getAddress());
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
                                          .loadFactorProvider(command -> 36)
                                          .build();
    }

    @AfterEach
    void tearDown() {
        dummyMessagePlatformServer.stop();
        axonServerConnectionManager.shutdown();
        testSubject.disconnect();
    }

    @Test
    void dispatch() throws Exception {
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
    void fireAndForgetUsesDefaultCallback() throws InterruptedException {
        testSubject.disconnect();
        //noinspection unchecked
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

        assertTrue(cdl.await(1, TimeUnit.SECONDS), "Expected default callback to have been invoked");
        verify(mockDefaultCommandCallback).onResult(eq(commandMessage), any());
    }

    @Test
    void dispatchWhenChannelThrowsAnException() throws InterruptedException {
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
    void dispatchWithError() throws Exception {
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
    void dispatchWithConcurrencyException() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is a concurrency issue");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<CommandResultMessage<? extends String>> resultHolder = new AtomicReference<>();

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
    void dispatchWithExceptionFromHandler() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("give me an exception");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<CommandResultMessage<? extends String>> resultHolder = new AtomicReference<>();

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
    void subscribe() {
        Registration registration = testSubject.subscribe(String.class.getName(), c -> "Done");
        assertWithin(500, TimeUnit.MILLISECONDS, () ->
                assertNotNull(dummyMessagePlatformServer.subscriptions(String.class.getName())));
        registration.cancel();
        assertWithin(500, TimeUnit.MILLISECONDS, () ->
                assertNull(dummyMessagePlatformServer.subscriptions(String.class.getName())));
    }

    @Test
    void processCommand() {
        CommandProviderInbound testCommandMessage = testCommandMessage();

        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef =
                buildInboundCommandStreamObserverReference();
        AxonServerCommandBus testSubject =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(axonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .requestStreamFactory(os -> new TestStreamObserver<>())
                                    .routingStrategy(command -> "RoutingKey")
                                    .build();
        testSubject.subscribe(String.class.getName(), c -> c.getMetaData().get("test1"));

        inboundStreamObserverRef.get().onNext(testCommandMessage);

        //noinspection unchecked
        verify(axonServerConnectionManager).getCommandStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    void unsupportedCommandInstruction() {
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef =
                buildInboundCommandStreamObserverReference();
        TestStreamObserver<CommandProviderOutbound> requestStream = new TestStreamObserver<>();
        AxonServerCommandBus testSubject =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(axonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .requestStreamFactory(os -> requestStream)
                                    .routingStrategy(command -> "RoutingKey")
                                    .build();
        testSubject.subscribe(String.class.getName(), c -> c.getMetaData().get("test1"));

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
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef =
                buildInboundCommandStreamObserverReference();
        TestStreamObserver<CommandProviderOutbound> requestStream = new TestStreamObserver<>();
        AxonServerCommandBus testSubject =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(axonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .requestStreamFactory(os -> requestStream)
                                    .routingStrategy(command -> "RoutingKey")
                                    .build();
        testSubject.subscribe(String.class.getName(), c -> c.getMetaData().get("test1"));

        inboundStreamObserverRef.get().onNext(CommandProviderInbound.newBuilder().build());

        assertEquals(0, requestStream.sentMessages().size());
    }

    @Test
    void resubscribe() throws Exception {
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
        verify(axonServerConnectionManager, atLeastOnce())
                .getCommandStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class));
    }

    @Test
    void dispatchInterceptor() {
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
    void reconnectAfterConnectionLost() {
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
        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> verify(
                        axonServerConnectionManager, atLeastOnce()
                ).getCommandStream(eq(BOUNDED_CONTEXT), any(StreamObserver.class))
        );
    }

    @Test
    void subscribeWithLoadFactor() {
        testSubject.subscribe(String.class.getName(), c -> "Done");
        assertWithin(2, TimeUnit.SECONDS, () -> {
            Optional<CommandSubscription> subscription =
                    dummyMessagePlatformServer.subscriptionForCommand(String.class.getName());
            assertTrue(subscription.isPresent());
            assertEquals(36, subscription.get().getLoadFactor());
        });
    }

    @Test
    void resubscribeWithLoadFactor() throws IOException {
        testSubject.subscribe(String.class.getName(), c -> "Done");
        assertWithin(2, TimeUnit.SECONDS, () -> {
            Optional<CommandSubscription> subscription =
                    dummyMessagePlatformServer.subscriptionForCommand(String.class.getName());
            assertTrue(subscription.isPresent());
        });

        reset(axonServerConnectionManager);
        dummyMessagePlatformServer.stop();
        assertNull(dummyMessagePlatformServer.subscriptions(String.class.getName()));

        dummyMessagePlatformServer.start();
        assertWithin(5, TimeUnit.SECONDS, () -> {
            Optional<CommandSubscription> subscription =
                    dummyMessagePlatformServer.subscriptionForCommand(String.class.getName());
            assertTrue(subscription.isPresent());
            assertEquals(36, subscription.get().getLoadFactor());
        });
    }

    @Test
    void testLocalSegmentReturnsLocalCommandBus() {
        assertEquals(localSegment, testSubject.localSegment());
    }

    @Test
    void testDisconnectUnsubscribesAllRegisteredCommands() {
        String testCommandOne = "testCommandOne";
        String testCommandTwo = "testCommandTwo";
        testSubject.subscribe(testCommandOne, command -> "Done");
        testSubject.subscribe(testCommandTwo, command -> "Done");

        testSubject.disconnect();

        assertWithin(2, TimeUnit.SECONDS, () -> dummyMessagePlatformServer.isUnsubscribed(testCommandOne));
        assertWithin(2, TimeUnit.SECONDS, () -> dummyMessagePlatformServer.isUnsubscribed(testCommandTwo));
    }

    @Test
    void testDisconnectFinishesCommandsInTransit() throws InterruptedException {
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef =
                buildInboundCommandStreamObserverReference();
        CommandProviderInbound testCommandMessage = testCommandMessage();

        AxonServerCommandBus testSubject = AxonServerCommandBus.builder()
                                                               .axonServerConnectionManager(axonServerConnectionManager)
                                                               .configuration(configuration)
                                                               .localSegment(localSegment)
                                                               .serializer(serializer)
                                                               .requestStreamFactory(os -> new TestStreamObserver<>())
                                                               .routingStrategy(command -> "RoutingKey")
                                                               .build();

        // Create a lock for the slow handler and lock it immediately, to spoof the handler's slow/long process
        ReentrantLock slowHandlerLock = new ReentrantLock();
        slowHandlerLock.lock();
        AtomicBoolean commandHandled = new AtomicBoolean(false);
        AtomicBoolean commandArrived = new AtomicBoolean(false);
        String testCommand = String.class.getName();
        MessageHandler<CommandMessage<?>> testCommandHandler = command -> {
            try {
                commandArrived.set(true);
                slowHandlerLock.lock();
            } finally {
                slowHandlerLock.unlock();
            }
            commandHandled.set(true);
            return "Done";
        };
        testSubject.subscribe(testCommand, testCommandHandler);

        CompletableFuture<Void> disconnected;
        try {
            inboundStreamObserverRef.get().onNext(testCommandMessage);
            while(!commandArrived.get()) {
                Thread.sleep(1);
            }
        } finally {
            disconnected = testSubject.shutdownDispatching();
            slowHandlerLock.unlock();
        }

        // Wait until the disconnect-thread is finished prior to validating
        disconnected.join();
        assertTrue(commandHandled.get());
        assertTrue(disconnected.isDone());
    }

    @Test
    void testAfterShutdownDispatchingAnShutdownInProgressExceptionIsThrownOnDispatchInvocation() {
        testSubject.shutdownDispatching();

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThrows(
                        ShutdownInProgressException.class,
                        () -> testSubject.dispatch(new GenericCommandMessage<>("some-command"))
                )
        );
    }

    @Test
    void testShutdownDispatchingWaitsForCommandsInTransitToComplete() {
        AtomicBoolean commandHandled = new AtomicBoolean(false);
        // Commands containing "blocking" will sleep for 500 millis
        GenericCommandMessage<String> testCommand = new GenericCommandMessage<>("some-blocking-command");

        testSubject.dispatch(testCommand, (commandMessage, result) -> commandHandled.set(true));
        CompletableFuture<Void> dispatchingHasShutdown = testSubject.shutdownDispatching();

        // Wait on the shutdownDispatching-thread, after which the command should have been handled
        dispatchingHasShutdown.join();
        assertTrue(commandHandled.get());
        assertTrue(dispatchingHasShutdown.isDone());
    }

    private AtomicReference<StreamObserver<CommandProviderInbound>> buildInboundCommandStreamObserverReference() {
        AtomicReference<StreamObserver<CommandProviderInbound>> inboundStreamObserverRef = new AtomicReference<>();

        doAnswer(invocationOnMock -> {
            inboundStreamObserverRef.set(invocationOnMock.getArgument(1));
            return new TestStreamObserver<CommandProviderInbound>();
        }).when(axonServerConnectionManager).getCommandStream(any(), any());

        return inboundStreamObserverRef;
    }

    private CommandProviderInbound testCommandMessage() {
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

        return CommandProviderInbound.newBuilder()
                                     .setInstructionId("instructionId")
                                     .setCommand(command)
                                     .build();
    }
}
