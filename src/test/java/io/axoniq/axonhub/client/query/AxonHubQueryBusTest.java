/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.query;

import com.google.protobuf.ByteString;
import io.axoniq.axonhub.ProcessingInstruction;
import io.axoniq.axonhub.ProcessingKey;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.platform.MetaDataValue;
import io.axoniq.platform.SerializedObject;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.responsetypes.InstanceResponseType;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.axoniq.axonhub.client.common.AssertUtils.assertWithin;
import static org.axonframework.common.ObjectUtils.getOrDefault;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
public class AxonHubQueryBusTest {

    private AxonHubQueryBus queryBus;
    private DummyMessagePlatformServer dummyMessagePlatformServer;
    private XStreamSerializer ser;
    private AxonHubConfiguration conf;
    private SimpleQueryBus localSegment;
    private PlatformConnectionManager platformConnectionManager;
    private AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserverRef;


    @Before
    public void setup() throws Exception {
        conf = new AxonHubConfiguration();
        conf.setServers("localhost:4343");
        conf.setClientName("JUnit");
        conf.setComponentName("JUnit");
        conf.setInitialNrOfPermits(100);
        conf.setNewPermitsThreshold(10);
        conf.setNrOfNewPermits(1000);
        localSegment = new SimpleQueryBus();
        ser = new XStreamSerializer();
        queryBus = new AxonHubQueryBus(new PlatformConnectionManager(conf),
                                       conf,
                                       localSegment,
                                       ser,
                                       ser,
                                       new QueryPriorityCalculator() {
                                       });
        dummyMessagePlatformServer = new DummyMessagePlatformServer(4343);
        dummyMessagePlatformServer.start();
        platformConnectionManager = mock(PlatformConnectionManager.class);
        inboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            inboundStreamObserverRef.set(invocationOnMock.getArgument(0));
            return new StreamObserver<QueryProviderOutbound>() {
                @Override
                public void onNext(QueryProviderOutbound commandProviderOutbound) {
                    System.out.println(commandProviderOutbound);
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println("Error:" + throwable);
                }

                @Override
                public void onCompleted() {
                    System.out.println("Completed");
                }
            };
        }).when(platformConnectionManager).getQueryStream(any(), any());
    }

    @After
    public void tearDown() {
        dummyMessagePlatformServer.stop();
    }

    @Test
    public void subscribe() throws Exception {
        Registration response = queryBus.subscribe("testQuery", String.class, q -> "test");
        Thread.sleep(1000);
        assertEquals(1, dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()).size());

        response.cancel();
        Thread.sleep(100);
        assertEquals(0, dummyMessagePlatformServer.subscriptions("testQuery", String.class.getName()).size());
    }

    @Test
    public void query() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World",
                                                                              ResponseTypes.instanceOf(String.class));

        assertEquals("test", queryBus.query(queryMessage).get().getPayload());
    }


    @Test
    public void processQuery() throws Exception {

        AxonHubQueryBus queryBus2 = new AxonHubQueryBus(platformConnectionManager,
                                                        conf,
                                                        localSegment,
                                                        ser,
                                                        ser,
                                                        new QueryPriorityCalculator() {
                                                        });
        Registration response = queryBus2.subscribe("testQuery", String.class, q -> "test: " + q.getPayloadType());

        QueryProviderInbound inboundMessage = testQueryMessage();
        inboundStreamObserverRef.get().onNext(inboundMessage);

        response.close();
    }

    @Test
    public void scatterGather() {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World",
                                                                              ResponseTypes.instanceOf(String.class))
                .andMetaData(MetaData.with("repeat", 10).and("interval", 10));

        assertEquals(10, queryBus.scatterGather(queryMessage, 12, TimeUnit.SECONDS).count());
    }

    @Test
    public void scatterGatherTimeout() {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World",
                                                                              ResponseTypes.instanceOf(String.class))
                .andMetaData(MetaData.with("repeat", 10).and("interval", 100));

        assertTrue(8 > queryBus.scatterGather(queryMessage, 550, TimeUnit.MILLISECONDS).count());
    }

    @Test
    public void dispatchInterceptor() {
        List<Object> results = new LinkedList<>();
        queryBus.registerDispatchInterceptor(messages -> (a, b) -> {
            results.add(b.getPayload());
            return b;
        });
        queryBus.query(new GenericQueryMessage<>("payload", new InstanceResponseType<>(String.class)));
        assertEquals("payload", results.get(0));
        assertEquals(1, results.size());
    }

    @Test
    public void handlerInterceptor() {
        AxonHubQueryBus bus = new AxonHubQueryBus(platformConnectionManager, conf, new SimpleQueryBus(), ser, ser,
                                                  new QueryPriorityCalculator() {});
        bus.subscribe("testQuery", String.class, q -> "test: " + q.getPayloadType());
        List<Object> results = new LinkedList<>();
        bus.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            results.add("Interceptor executed");
            return interceptorChain.proceed();
        });
        QueryProviderInbound inboundMessage = testQueryMessage();
        inboundStreamObserverRef.get().onNext(inboundMessage);
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, results.size()));
        assertWithin(1, TimeUnit.SECONDS, ()-> assertEquals("Interceptor executed", results.get(0)));
    }

    private QueryProviderInbound testQueryMessage() {
        org.axonframework.serialization.SerializedObject<byte[]> response =
                ser.serialize(ResponseTypes.instanceOf(String.class), byte[].class);
        return QueryProviderInbound.newBuilder().setQuery(
                QueryRequest.newBuilder().setQuery("testQuery")
                            .setResponseType(SerializedObject.newBuilder().setData(
                                    ByteString.copyFrom(response.getData()))
                                        .setType(response.getType().getName())
                                        .setRevision(getOrDefault(response.getType().getRevision(), "")))
                            .setPayload(SerializedObject.newBuilder()
                                                .setData(ByteString.copyFromUtf8("<string>Hello</string>"))
                                                .setType(String.class.getName()))
        ).build();
    }


}
