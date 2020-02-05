/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link DefaultReactiveQueryGateway}.
 *
 * @author Milan Savic
 */
public class DefaultReactiveQueryGatewayTest {

    private DefaultReactiveQueryGateway reactiveQueryGateway;
    private QueryUpdateEmitter queryUpdateEmitter;

    @BeforeEach
    void setUp() {
        SimpleQueryBus queryBus = SimpleQueryBus.builder().build();
        queryUpdateEmitter = queryBus.queryUpdateEmitter();
        queryBus.subscribe(String.class.getName(), String.class, message -> "handled");
        queryBus.subscribe(String.class.getName(), String.class, message -> "handled");
        queryBus.subscribe(Integer.class.getName(), Integer.class, message -> {
            throw new RuntimeException();
        });
        QueryGateway queryGateway = DefaultQueryGateway.builder()
                                                       .queryBus(queryBus)
                                                       .build();
        reactiveQueryGateway = new DefaultReactiveQueryGateway(queryGateway);
    }

    @Test
    void testQuery() {
        StepVerifier.create(reactiveQueryGateway.query("criteria", String.class))
                    .expectNext("handled")
                    .verifyComplete();
    }

    @Test
    void testQueryFails() {
        StepVerifier.create(reactiveQueryGateway.query(5, Integer.class))
                    .verifyError(RuntimeException.class);
    }

    @Test
    void testScatterGather() {
        StepVerifier.create(reactiveQueryGateway.scatterGather("criteria",
                                                               ResponseTypes.instanceOf(String.class),
                                                               1,
                                                               TimeUnit.SECONDS))
                    .expectNext("handled", "handled")
                    .verifyComplete();
    }

    @Test
    void testScatterGatherFails() {
        StepVerifier.create(reactiveQueryGateway.scatterGather(6,
                                                               ResponseTypes.instanceOf(Integer.class),
                                                               1,
                                                               TimeUnit.SECONDS))
                    .expectNextCount(0)
                    .verifyComplete();
    }

    @Test
    void testSubscriptionQuery() {
        SubscriptionQueryResult<String, String> result = reactiveQueryGateway.subscriptionQuery("criteria",
                                                                                                String.class,
                                                                                                String.class);
        queryUpdateEmitter.emit(String.class, q -> true, "update");
        queryUpdateEmitter.complete(String.class, q -> true);
        StepVerifier.create(result.initialResult())
                    .expectNext("handled")
                    .verifyComplete();
        StepVerifier.create(result.updates())
                    .expectNext("update")
                    .verifyComplete();
    }

    @Test
    void testSubscriptionQueryFails() {
        SubscriptionQueryResult<Integer, Integer> result = reactiveQueryGateway.subscriptionQuery(6,
                                                                                                  Integer.class,
                                                                                                  Integer.class);
        StepVerifier.create(result.initialResult())
                    .verifyError(RuntimeException.class);
    }
}
