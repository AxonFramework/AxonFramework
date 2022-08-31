/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Test class validating the {@link SimpleQueryUpdateEmitter}.
 *
 * @author Allard Buijze
 * @author Corrado Musumeci
 * @author Steven van Beelen
 */
class SimpleQueryUpdateEmitterTest {

    private final TestSpanFactory spanFactory = new TestSpanFactory();
    private final SimpleQueryUpdateEmitter testSubject = SimpleQueryUpdateEmitter.builder()
                                                                                 .spanFactory(spanFactory)
                                                                                 .build();

    @Test
    void completingRegistrationOldApi() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                SubscriptionQueryBackpressure.defaultBackpressure(),
                1024
        );

        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext("some-awesome-text")
                    .verifyComplete();
    }

    @Test
    void concurrentUpdateEmitting() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> registration = testSubject.registerUpdateHandler(queryMessage, 128);

        ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 100; i++) {
            executors.submit(() -> testSubject.emit(q -> true, "Update"));
        }
        executors.shutdown();
        StepVerifier.create(registration.getUpdates())
                    .expectNextCount(100)
                    .then(() -> testSubject.complete(q -> true))
                    .verifyComplete();
    }

    @Test
    void concurrentUpdateEmitting_WithBackpressure() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> registration = testSubject.registerUpdateHandler(queryMessage, SubscriptionQueryBackpressure.defaultBackpressure(), 128);

        ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 100; i++) {
            executors.submit(() -> {
                testSubject.emit(q -> true, "Update");
            });
        }
        executors.shutdown();
        StepVerifier.create(registration.getUpdates())
                    .expectNextCount(100)
                    .then(() -> testSubject.complete(q -> true))
                    .verifyComplete();
    }

    @Test
    void cancelingRegistrationDoesNotCompleteFluxOfUpdatesOldApi() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                SubscriptionQueryBackpressure.defaultBackpressure(),
                1024
        );

        testSubject.emit(any -> true, "some-awesome-text");
        result.getRegistration().cancel();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext("some-awesome-text")
                    .verifyTimeout(Duration.ofMillis(500));
    }

    @Test
    void completingRegistration() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext("some-awesome-text")
                    .verifyComplete();
    }

    @Test
    void queryUpdateEmitterIsTraced() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        spanFactory.verifySpanCompleted("SimpleQueryUpdateEmitter.emit");
        spanFactory.verifySpanHasType("SimpleQueryUpdateEmitter.emit", TestSpanFactory.TestSpanType.INTERNAL);
        spanFactory.verifySpanCompleted("QueryUpdateEmitter.emit chatMessages");
        spanFactory.verifySpanHasType("QueryUpdateEmitter.emit chatMessages", TestSpanFactory.TestSpanType.DISPATCH);
    }

    @Test
    void differentUpdateAreDisambiguatedAndWrongTypesAreFilteredBasedOnQueryTypes() {
        SubscriptionQueryMessage<String, List<String>, Integer> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(Integer.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext(1234)
                    .verifyComplete();
    }

    @Test
    void cancelingRegistrationDoesNotCompleteFluxOfUpdates() {
        SubscriptionQueryMessage<String, List<String>, String> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.instanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        result.getRegistration().cancel();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext("some-awesome-text")
                    .verifyTimeout(Duration.ofMillis(500));
    }
}
