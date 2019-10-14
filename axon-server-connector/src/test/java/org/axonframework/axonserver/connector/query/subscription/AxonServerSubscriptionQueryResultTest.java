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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.*;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by Sara Pellegrini on 18/06/2018.
 * sara.pellegrini@gmail.com
 */
class AxonServerSubscriptionQueryResultTest {

    private SubscriptionQuery queryMessage;

    private FakeStreamObserver<SubscriptionQueryRequest> requestObserver;

    private AxonServerConfiguration configuration;

    private SubscriptionQueryResponse update;

    private SubscriptionQueryResponse initialResult;


    @BeforeEach
    void setUp(){
        requestObserver = new FakeStreamObserver<>();
        queryMessage = SubscriptionQuery.newBuilder().build();
        update = SubscriptionQueryResponse.newBuilder().setUpdate(QueryUpdate.newBuilder()).build();
        initialResult = SubscriptionQueryResponse.newBuilder().setInitialResult(QueryResponse.newBuilder()).build();
        configuration = new AxonServerConfiguration();
        configuration.setContext("context");
        configuration.setComponentName("component");
        configuration.setClientId("client");
    }

    @Test
    void testSubscribeUpdates() {
        SubscriptionQueryBackpressure backPressure = new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
        AxonServerSubscriptionQueryResult target = new AxonServerSubscriptionQueryResult(
                queryMessage, responseStream -> requestObserver, configuration, backPressure, 10, () -> {});
        target.onNext(update);
        target.onNext(update);
        List<QueryUpdate> updates = new ArrayList<>();
        target.get().updates().subscribe(updates::add);
        assertEquals(2,updates.size());
    }

    @Test
    void testSubscribeInitialResponse() {
        SubscriptionQueryBackpressure backPressure = new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
        AxonServerSubscriptionQueryResult target = new AxonServerSubscriptionQueryResult(
                queryMessage, responseStream -> requestObserver, configuration, backPressure, 10, () -> {});
        List<QueryResponse> result = new ArrayList<>();
        target.get().initialResult().subscribe(result::add);
        target.onNext(initialResult);
        target.onNext(initialResult);
        assertEquals(1, result.size());
    }

    @Test
    void testErrorOverflowStrategy() {
        SubscriptionQueryBackpressure backPressure = new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
        AxonServerSubscriptionQueryResult target = new AxonServerSubscriptionQueryResult(
                queryMessage, responseStream -> requestObserver, configuration, backPressure, 2, () -> {});
        target.onNext(update);
        target.onNext(update);
        target.onNext(update);
        assertEquals(1, requestObserver.completedCount());
    }
}
