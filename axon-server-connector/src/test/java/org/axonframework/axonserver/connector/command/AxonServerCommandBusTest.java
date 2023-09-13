/*
 * Copyright (c) 2010-2023. Axon Framework
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
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.grpc.command.CommandSubscription;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.TargetContextResolver;
import org.axonframework.axonserver.connector.TestTargetContextResolver;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.commandhandling.CommandBusSpanFactory;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DefaultCommandBusSpanFactory;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.lifecycle.ShutdownInProgressException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.awaitility.Awaitility.await;
import static org.axonframework.axonserver.connector.TestTargetContextResolver.BOUNDED_CONTEXT;
import static org.axonframework.axonserver.connector.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test class to cover all the operations performed by the {@link AxonServerCommandBus}.
 *
 * @author Marc Gathier
 */
class AxonServerCommandBusTest {

    private DummyMessagePlatformServer dummyMessagePlatformServer;

    private AxonServerConnectionManager axonServerConnectionManager;
    private AxonServerConfiguration configuration;
    private final SimpleCommandBus localSegment = SimpleCommandBus.builder().build();
    private final Serializer serializer = TestSerializer.xStreamSerializer();
    private final TargetContextResolver<CommandMessage<?>> targetContextResolver =
            spy(new TestTargetContextResolver<>());

    private AxonServerCommandBus testSubject;

    private AxonServerConnection mockConnection;
    private CommandChannel mockCommandChannel;
    private TestSpanFactory spanFactory;
    private CommandBusSpanFactory commandBusSpanFactory;

    @BeforeEach
    void setup() throws Exception {
        spanFactory = new TestSpanFactory();
        commandBusSpanFactory = DefaultCommandBusSpanFactory.builder().spanFactory(spanFactory).build();
        dummyMessagePlatformServer = new DummyMessagePlatformServer();
        dummyMessagePlatformServer.start();

        configuration = new AxonServerConfiguration();
        configuration.setServers(dummyMessagePlatformServer.getAddress());
        configuration.setClientId("JUnit");
        configuration.setComponentName("JUnit");
        configuration.setPermits(100);
        configuration.setNewPermitsThreshold(10);
        configuration.setNrOfNewPermits(1000);
        configuration.setContext(BOUNDED_CONTEXT);

        mockConnection = mock(AxonServerConnection.class);
        mockCommandChannel = mock(CommandChannel.class);

        when(mockConnection.commandChannel()).thenReturn(mockCommandChannel);

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
                                          .spanFactory(commandBusSpanFactory)
                                          .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        dummyMessagePlatformServer.stop();
        axonServerConnectionManager.shutdown();
        testSubject.disconnect().get(5, TimeUnit.SECONDS);
    }

    @Test
    void dispatch() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is the payload");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            spanFactory.verifySpanActive("CommandBus.dispatchDistributedCommand", commandMessage);
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
        verify(axonServerConnectionManager).getConnection(BOUNDED_CONTEXT);
        await().atMost(Duration.ofSeconds(3l))
                .untilAsserted(
                        () -> spanFactory.verifySpanCompleted("CommandBus.dispatchDistributedCommand")
                );
        await().atMost(Duration.ofSeconds(3l))
                .untilAsserted(
                        () -> spanFactory.verifySpanPropagated("CommandBus.dispatchDistributedCommand", commandMessage)
                );
    }

    @Test
    void equalPriorityMessagesProcessedInOrder() throws InterruptedException {
        AxonServerCommandBus singleThreadedTestSubject =
                AxonServerCommandBus.builder()
                                    .axonServerConnectionManager(axonServerConnectionManager)
                                    .configuration(configuration)
                                    .localSegment(localSegment)
                                    .serializer(serializer)
                                    .routingStrategy(command -> "RoutingKey")
                                    .targetContextResolver(targetContextResolver)
                                    .loadFactorProvider(command -> 36)
                                    .executorServiceBuilder((c, q) -> new ThreadPoolExecutor(
                                            1, 1, 5, TimeUnit.SECONDS, q
                                    ))
                                    .build();

        int commandCount = 1000;

        CountDownLatch startProcessingGate = new CountDownLatch(1);
        CountDownLatch finishProcessingGate = new CountDownLatch(commandCount);

        List<Long> expected = LongStream.range(0, commandCount)
                                        .boxed()
                                        .collect(Collectors.toList());
        List<Long> actual = new CopyOnWriteArrayList<>();

        AtomicReference<Function<Command, CompletableFuture<CommandResponse>>> commandHandlerRef = new AtomicReference<>();
        when(axonServerConnectionManager.getConnection(anyString())).thenReturn(mockConnection);
        doAnswer(i -> {
            commandHandlerRef.set(i.getArgument(0));
            return (io.axoniq.axonserver.connector.Registration) () -> CompletableFuture.completedFuture(null);
        }).when(mockCommandChannel)
          .registerCommandHandler(any(), anyInt(), eq("testCommand"));

        singleThreadedTestSubject.subscribe("testCommand", message -> {
            startProcessingGate.await();
            actual.add((long) message.getMetaData().get("index"));
            finishProcessingGate.countDown();
            return "ok";
        });

        assertWithin(1, TimeUnit.SECONDS, () -> assertNotNull(commandHandlerRef.get()));

        Function<Command, CompletableFuture<CommandResponse>> commandHandler = commandHandlerRef.get();
        for (int i = 0; i < commandCount; i++) {
            commandHandler.apply(Command.newBuilder()
                                        .setName("testCommand")
                                        .setMessageIdentifier(UUID.randomUUID().toString())
                                        .setPayload(SerializedObject.newBuilder()
                                                                    .setType("java.lang.String")
                                                                    .setData(ByteString.copyFromUtf8(
                                                                            "<string>Hello</string>"
                                                                    ))
                                        )
                                        .putMetaData("index", MetaDataValue.newBuilder().setNumberValue(i).build())
                                        .build());
        }
        startProcessingGate.countDown();
        //noinspection ResultOfMethodCallIgnored
        finishProcessingGate.await(30, TimeUnit.SECONDS);

        assertEquals(commandCount, actual.size());
        assertEquals(expected, actual);
    }

    @Test
    void fireAndForgetUsesDefaultCallback() throws InterruptedException {
        testSubject.disconnect().join();
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
        when(axonServerConnectionManager.getConnection(anyString())).thenReturn(mockConnection);
        when(mockCommandChannel.sendCommand(any())).thenThrow(new RuntimeException("oops"));

        testSubject.dispatch(commandMessage, (CommandCallback<String, String>) (cm, result) -> {
            if (result.isExceptional()) {
                failure.set(true);
                throwable.set(result.exceptionResult());
            }
            waiter.countDown();
        });

        waiter.await();
        assertTrue(failure.get());
        assertEquals(AxonServerCommandDispatchException.class, throwable.get().getClass());
        assertEquals(ErrorCode.COMMAND_DISPATCH_ERROR.errorCode(),
                     ((AxonServerCommandDispatchException) throwable.get()).getErrorCode());

        verify(targetContextResolver).resolveContext(commandMessage);
        verify(axonServerConnectionManager).getConnection(BOUNDED_CONTEXT);
        spanFactory.verifySpanHasException("CommandBus.dispatchDistributedCommand", RuntimeException.class);
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
        verify(axonServerConnectionManager).getConnection(BOUNDED_CONTEXT);
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
        verify(axonServerConnectionManager).getConnection(BOUNDED_CONTEXT);
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
        verify(axonServerConnectionManager).getConnection(BOUNDED_CONTEXT);
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
    void localSegmentReturnsLocalCommandBus() {
        assertEquals(localSegment, testSubject.localSegment());
    }

    @Test
    void disconnectUnsubscribesAllRegisteredCommands() {
        String testCommandOne = "testCommandOne";
        String testCommandTwo = "testCommandTwo";
        //noinspection resource
        testSubject.subscribe(testCommandOne, command -> "Done");
        //noinspection resource
        testSubject.subscribe(testCommandTwo, command -> "Done");

        testSubject.disconnect().join();

        await().atMost(Duration.ofSeconds(5))
               .pollDelay(Duration.ofMillis(250))
               .until(() -> dummyMessagePlatformServer.isUnsubscribed(testCommandOne));
        await().atMost(Duration.ofSeconds(5))
               .pollDelay(Duration.ofMillis(250))
               .until(() -> dummyMessagePlatformServer.isUnsubscribed(testCommandTwo));
    }

    @Test
    void afterShutdownDispatchingAnShutdownInProgressExceptionIsThrownOnDispatchInvocation() {
        testSubject.shutdownDispatching();

        GenericCommandMessage<String> command = new GenericCommandMessage<>("some-command");
        assertThrows(
                ShutdownInProgressException.class,
                () -> testSubject.dispatch(command)
        );
    }

    @Test
    void shutdownDispatchingWaitsForCommandsInTransitToComplete() {
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
}
