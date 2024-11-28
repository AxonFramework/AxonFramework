/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionAck;
import io.axoniq.axonserver.grpc.control.ClientIdentification;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformServiceGrpc;
import io.axoniq.axonserver.grpc.event.CancelScheduledEventRequest;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventSchedulerGrpc;
import io.axoniq.axonserver.grpc.event.RescheduleEventRequest;
import io.axoniq.axonserver.grpc.event.ScheduleEventRequest;
import io.axoniq.axonserver.grpc.event.ScheduleToken;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.utils.TestSerializer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.eventhandling.scheduling.quartz.QuartzScheduleToken;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedNameUtils;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AxonServerEventScheduler}.
 *
 * @author Marc Gathier
 */
public class AxonServerEventSchedulerTest {

    private static Server server;
    private AxonServerConnectionManager connectionManager;

    private AxonServerEventScheduler testSubject;

    private static final Map<String, Event> scheduled = new ConcurrentHashMap<>();
    private static final AtomicBoolean sendResponse = new AtomicBoolean(true);

    @BeforeAll
    static void startServer() throws Exception {
        server = ServerBuilder.forPort(18024)
                              .addService(new PlatformServiceGrpc.PlatformServiceImplBase() {
                                  @Override
                                  public void getPlatformServer(ClientIdentification request,
                                                                StreamObserver<PlatformInfo> responseObserver) {
                                      responseObserver.onNext(
                                              PlatformInfo.newBuilder().setSameConnection(true).build()
                                      );
                                      responseObserver.onCompleted();
                                  }

                                  @Override
                                  public StreamObserver<PlatformInboundInstruction> openStream(
                                          StreamObserver<PlatformOutboundInstruction> responseObserver
                                  ) {
                                      return new StreamObserver<>() {
                                          @Override
                                          public void onNext(PlatformInboundInstruction platformInboundInstruction) {
                                              // Not needed
                                          }

                                          @Override
                                          public void onError(Throwable throwable) {
                                              // Not needed
                                          }

                                          @Override
                                          public void onCompleted() {
                                              // Not needed
                                          }
                                      };
                                  }
                              }).addService(new EventSchedulerGrpc.EventSchedulerImplBase() {
                    @Override
                    public void scheduleEvent(ScheduleEventRequest request,
                                              StreamObserver<ScheduleToken> responseObserver) {
                        if (sendResponse.get()) {
                            String id = UUID.randomUUID().toString();
                            scheduled.put(id, request.getEvent());
                            responseObserver.onNext(ScheduleToken.newBuilder().setToken(id).build());
                            responseObserver.onCompleted();
                        }
                    }

                    @Override
                    public void rescheduleEvent(RescheduleEventRequest request,
                                                StreamObserver<ScheduleToken> responseObserver) {
                        String token = request.getToken();
                        if (request.getToken().isEmpty()) {
                            token = UUID.randomUUID().toString();
                        }
                        scheduled.put(token, request.getEvent());
                        responseObserver.onNext(ScheduleToken.newBuilder()
                                                             .setToken(token)
                                                             .build());
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void cancelScheduledEvent(CancelScheduledEventRequest request,
                                                     StreamObserver<InstructionAck> responseObserver) {
                        if (!scheduled.containsKey(request.getToken())) {
                            ErrorMessage errorMessage = ErrorMessage.newBuilder()
                                                                    .setMessage("Schedule not found")
                                                                    .addDetails("Detail1")
                                                                    .addDetails("Detail2")
                                                                    .setErrorCode("AXONIQ-2610")
                                                                    .build();
                            responseObserver.onNext(InstructionAck.newBuilder()
                                                                  .setSuccess(false)
                                                                  .setError(errorMessage)
                                                                  .build());
                        } else {
                            scheduled.remove(request.getToken());
                            responseObserver.onNext(InstructionAck.newBuilder().setSuccess(true).build());
                        }
                        responseObserver.onCompleted();
                    }
                }).build();
        server.start();
    }

    @AfterAll
    static void shutdown() throws Exception {
        server.shutdownNow().awaitTermination();
    }

    @BeforeEach
    void setUp() {
        sendResponse.set(true);
        AxonServerConfiguration axonserverConfiguration = AxonServerConfiguration.builder()
                                                                                 .servers("localhost:18024")
                                                                                 .build();
        connectionManager = AxonServerConnectionManager.builder()
                                                       .axonServerConfiguration(axonserverConfiguration)
                                                       .build();
        testSubject = AxonServerEventScheduler.builder()
                                              .eventSerializer(TestSerializer.xStreamSerializer())
                                              .connectionManager(connectionManager)
                                              .build();
        testSubject.start();
    }

    @Test
    void schedule() {
        org.axonframework.eventhandling.scheduling.ScheduleToken token =
                testSubject.schedule(Instant.now().plus(Duration.ofMinutes(5)), "TestEvent");
        assertInstanceOf(SimpleScheduleToken.class, token);
        SimpleScheduleToken simpleScheduleToken = (SimpleScheduleToken) token;
        assertNotNull(scheduled.get(simpleScheduleToken.getTokenId()));
    }

    @Test
    void scheduleCustomContext() {
        sendResponse.set(true);
        AxonServerConfiguration axonserverConfiguration = AxonServerConfiguration.builder()
                                                                                 .servers("localhost:18024")
                                                                                 .build();
        connectionManager = AxonServerConnectionManager.builder()
                                                       .axonServerConfiguration(axonserverConfiguration)
                                                       .build();

        AxonServerConnectionManager spyConnectionManager = spy(connectionManager);

        testSubject = AxonServerEventScheduler.builder()
                                              .eventSerializer(TestSerializer.xStreamSerializer())
                                              .connectionManager(spyConnectionManager)
                                              .defaultContext("textContext")
                                              .build();
        testSubject.start();

        org.axonframework.eventhandling.scheduling.ScheduleToken token =
                testSubject.schedule(Instant.now().plus(Duration.ofMinutes(5)), "TestEvent");
        assertInstanceOf(SimpleScheduleToken.class, token);
        SimpleScheduleToken simpleScheduleToken = (SimpleScheduleToken) token;
        assertNotNull(scheduled.get(simpleScheduleToken.getTokenId()));
        verify(spyConnectionManager).getConnection("textContext");
    }

    @Test
    void scheduleTimeout() {
        sendResponse.set(false);
        testSubject = AxonServerEventScheduler.builder()
                                              .eventSerializer(TestSerializer.xStreamSerializer())
                                              .connectionManager(connectionManager)
                                              .requestTimeout(500, TimeUnit.MILLISECONDS)
                                              .build();
        testSubject.start();

        try {
            testSubject.schedule(Instant.now().plus(Duration.ofMinutes(5)), "TestEvent");
            fail("Expected Exception");
        } catch (Exception e) {
            assertInstanceOf(AxonServerException.class, e);
            assertTrue(e.getMessage().contains("Timeout"));
        }
    }

    @Test
    void scheduleWithDuration() {
        org.axonframework.eventhandling.scheduling.ScheduleToken token =
                testSubject.schedule(Duration.ofMinutes(5), "TestEvent");
        assertInstanceOf(SimpleScheduleToken.class, token);
        SimpleScheduleToken simpleScheduleToken = (SimpleScheduleToken) token;
        assertNotNull(scheduled.get(simpleScheduleToken.getTokenId()));
    }

    @Test
    void scheduleDuringShutdown() {
        testSubject.shutdownDispatching();
        assertThrows(IllegalArgumentException.class, () -> testSubject.schedule(Duration.ofMinutes(5), "TestEvent"));
    }

    @Test
    void cancelSchedule() {
        scheduled.put("12345", Event.newBuilder().build());
        testSubject.cancelSchedule(new SimpleScheduleToken("12345"));
    }

    @Test
    void cancelWithQuartzScheduleToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.cancelSchedule(new QuartzScheduleToken("job", "12345"))
        );
    }

    @Test
    void reschedule() {
        String token = "12345";
        EventMessage<String> testEvent =
                new GenericEventMessage<>(QualifiedNameUtils.fromDottedName("test.event"), "Updated", MetaData.with("updated", "true"));

        scheduled.put(token, Event.newBuilder().build());
        testSubject.reschedule(new SimpleScheduleToken(token), Duration.ofDays(1), testEvent);

        assertNotNull(scheduled.get(token));
        assertEquals(1, scheduled.get(token).getMetaDataCount());
    }

    @Test
    void rescheduleWithoutToken() {
        EventMessage<String> testEvent =
                new GenericEventMessage<>(QualifiedNameUtils.fromDottedName("test.event"), "Updated", MetaData.with("updated", "true"));
        org.axonframework.eventhandling.scheduling.ScheduleToken token =
                testSubject.reschedule(null, Duration.ofDays(1), testEvent);

        assertInstanceOf(SimpleScheduleToken.class, token);
        SimpleScheduleToken simpleScheduleToken = (SimpleScheduleToken) token;
        assertNotNull(scheduled.get(simpleScheduleToken.getTokenId()));
    }
}
