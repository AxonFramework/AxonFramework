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

package io.axoniq.axonhub.client.query.subscription;

import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QueryUpdate;
import io.axoniq.axonhub.SubscriptionQuery;
import io.axoniq.axonhub.SubscriptionQueryRequest;
import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by Sara Pellegrini on 18/06/2018.
 * sara.pellegrini@gmail.com
 */
public class AxonHubSubscriptionQueryResultTest {


    private SubscriptionQuery queryMessage;

    private FakeStreamObserver<SubscriptionQueryRequest> requestObserver;

    private AxonHubConfiguration configuration;

    private SubscriptionQueryResponse update;

    private SubscriptionQueryResponse initialResult;


    @Before
    public void setUp(){
        requestObserver = new FakeStreamObserver<>();
        queryMessage = SubscriptionQuery.newBuilder().build();
        update = SubscriptionQueryResponse.newBuilder().setUpdate(QueryUpdate.newBuilder()).build();
        initialResult = SubscriptionQueryResponse.newBuilder().setInitialResponse(QueryResponse.newBuilder()).build();
        configuration = new AxonHubConfiguration();
        configuration.setContext("context");
        configuration.setComponentName("component");
        configuration.setClientName("client");
    }

    @Test
    public void testSubscribeUpdates() {
        SubscriptionQueryBackpressure backPressure = new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
        AxonHubSubscriptionQueryResult target = new AxonHubSubscriptionQueryResult(
                queryMessage, responseStream -> requestObserver, configuration, backPressure, 10, () -> {});
        target.onNext(update);
        target.onNext(update);
        List<QueryUpdate> updates = new ArrayList<>();
        target.get().updates().subscribe(updates::add);
        assertEquals(2,updates.size());
    }

    @Test
    public void testSubscribeInitialResponse() {
        SubscriptionQueryBackpressure backPressure = new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
        AxonHubSubscriptionQueryResult target = new AxonHubSubscriptionQueryResult(
                queryMessage, responseStream -> requestObserver, configuration, backPressure, 10, () -> {});
        List<QueryResponse> result = new ArrayList<>();
        target.get().initialResult().subscribe(result::add);
        target.onNext(initialResult);
        target.onNext(initialResult);
        assertEquals(1, result.size());
    }

    @Test
    public void testErrorOverflowStrategy() {
        SubscriptionQueryBackpressure backPressure = new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
        AxonHubSubscriptionQueryResult target = new AxonHubSubscriptionQueryResult(
                queryMessage, responseStream -> requestObserver, configuration, backPressure, 2, () -> {});
        target.onNext(update);
        target.onNext(update);
        target.onNext(update);
        assertEquals(1, requestObserver.completedCount());
    }
}