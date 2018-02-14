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
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.platform.SerializedObject;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Author: marc
 */
public class AxonHubQueryBusTest {
    private AxonHubQueryBus queryBus;
    private DummyMessagePlatformServer dummyMessagePlatformServer;
    private XStreamSerializer ser;
    private AxonHubConfiguration conf;
    private SimpleQueryBus localSegment;


    @Before
    public void setup() throws Exception {
        conf = new AxonHubConfiguration();
        conf.setRoutingServers("localhost:4343");
        conf.setClientName("JUnit");
        conf.setComponentName("JUnit");
        conf.setInitialNrOfPermits(100);
        conf.setNewPermitsThreshold(10);
        conf.setNrOfNewPermits(1000);
        localSegment = new SimpleQueryBus();
        ser = new XStreamSerializer();
        queryBus = new AxonHubQueryBus(new PlatformConnectionManager(conf), conf, localSegment, ser, new QueryPriorityCalculator() {});
        dummyMessagePlatformServer = new DummyMessagePlatformServer(4343);
        dummyMessagePlatformServer.start();
    }

    @After
    public void tearDown() throws Exception {
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
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class);

        assertEquals("test", queryBus.query(queryMessage).get());
    }

    @Test
    public void processQuery() throws Exception {
        PlatformConnectionManager platformConnectionManager = mock(PlatformConnectionManager.class);
        AtomicReference<StreamObserver<QueryProviderInbound>> inboundStreamObserverRef = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            inboundStreamObserverRef.set( invocationOnMock.getArgument(0));
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

        AxonHubQueryBus queryBus2 = new AxonHubQueryBus(platformConnectionManager, conf, localSegment, ser, new QueryPriorityCalculator() {});
        Registration response = queryBus2.subscribe("testQuery", String.class, q -> "test: " + q.getPayloadType());
        QueryProviderInbound inboundMessage = QueryProviderInbound.newBuilder()
                .setQuery(QueryRequest.newBuilder()
                    .setQuery("testQuery")
                        .setResultName(String.class.getName())
                        .setPayload(SerializedObject.newBuilder()
                            .setData(ByteString.copyFromUtf8("<string>Hello</string>"))
                                .setType(String.class.getName())
                        )
                )
                .build();
        inboundStreamObserverRef.get().onNext(inboundMessage);

        response.close();

    }

    @Test
    public void queryAll() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class)
                .andMetaData(MetaData.with("repeat", 10).and("interval", 10));

        assertEquals(10, queryBus.queryAll(queryMessage, 2, TimeUnit.SECONDS).count());
    }

    @Test
    public void queryAllTimeout() throws Exception {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>("Hello, World", String.class)
                .andMetaData(MetaData.with("repeat", 10).and("interval", 100));

        assertTrue(8 > queryBus.queryAll(queryMessage, 550, TimeUnit.MILLISECONDS).count());
    }


}