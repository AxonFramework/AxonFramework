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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.equalTo;

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
        spanFactory.verifySpanCompleted("SimpleQueryUpdateEmitter.doEmit");
        spanFactory.verifySpanHasType("SimpleQueryUpdateEmitter.doEmit", TestSpanFactory.TestSpanType.DISPATCH);
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
    void updateResponseTypeFilteringWorksForMultipleInstanceOfWithArrayAndList() {
        SubscriptionQueryMessage<String, List<String>, List<String>> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.multipleInstancesOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        testSubject.emit(any -> true, Optional.of("optional-payload"));
        testSubject.emit(any -> true, Optional.empty());
        testSubject.emit(any -> true, new String[] { "array-item-1", "array-item-2" });
        testSubject.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        testSubject.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        testSubject.emit(any -> true, Mono.just("mono-item"));
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
        			.expectNextMatches(actual -> equalTo(new String[] { "array-item-1", "array-item-2" }).matches(actual) )
        			.expectNextMatches(actual -> equalTo(Arrays.asList("list-item-1", "list-item-2")).matches(actual) )
                    .verifyComplete();
    }

    @Test
    void updateResponseTypeFilteringWorksForOptionaInstanceOf() {
        SubscriptionQueryMessage<String, List<String>, Optional<String>> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.optionalInstanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        testSubject.emit(any -> true, Optional.of("optional-payload"));
        testSubject.emit(any -> true, Optional.empty());
        testSubject.emit(any -> true, new String[] { "array-item-1", "array-item-2" });
        testSubject.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        testSubject.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        testSubject.emit(any -> true, Mono.just("mono-item"));
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
        			.expectNext(Optional.of("optional-payload"), Optional.empty() )
                    .verifyComplete();
    }

	@Test
    @SuppressWarnings("unchecked")
    void updateResponseTypeFilteringWorksForPublisherOf() {
        SubscriptionQueryMessage<String, List<String>, Publisher<String>> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.publisherOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        testSubject.emit(any -> true, Optional.of("optional-payload"));
        testSubject.emit(any -> true, Optional.empty());
        testSubject.emit(any -> true, new String[] { "array-item-1", "array-item-2" });
        testSubject.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        testSubject.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        testSubject.emit(any -> true, Mono.just("mono-item"));
        testSubject.emit(any -> true, Mono.empty());
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
        			.expectNextMatches( publisher -> {
        				try {
        					StepVerifier.create((Publisher<String>) publisher).expectNext("flux-item-1", "flux-item-2").verifyComplete();
        				} catch (Exception e) {
        					return false;
        				}
        				return true;
        			})
        			.expectNextMatches( publisher -> {
        				try {
        					StepVerifier.create((Publisher<String>) publisher).expectNext("mono-item").verifyComplete();
        				} catch (Exception e) {
        					return false;
        				}
        				return true;
        			})
        			.expectNextMatches( publisher -> {
        				try {
        					StepVerifier.create((Publisher<String>) publisher).verifyComplete();
        				} catch (Exception e) {
        					return false;
        				}
        				return true;
        			})
                    .verifyComplete();
    }

    @Test
    void multipleInstanceUpdatesAreDelivered() {
        SubscriptionQueryMessage<String, List<String>, List<String>> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.multipleInstancesOf(String.class),
                ResponseTypes.multipleInstancesOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, Arrays.asList("text1","text2"));
        testSubject.emit(any -> true, Arrays.asList("text3","text4"));
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext(Arrays.asList("text1","text2"), Arrays.asList("text3","text4"))
                    .verifyComplete();
    }

    @Test
    void optionalUpdatesAreDelivered() {
        SubscriptionQueryMessage<String, Optional<String>, Optional<String>> queryMessage = new GenericSubscriptionQueryMessage<>(
                "some-payload",
                "chatMessages",
                ResponseTypes.optionalInstanceOf(String.class),
                ResponseTypes.optionalInstanceOf(String.class)
        );

        UpdateHandlerRegistration<Object> result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.getUpdates().subscribe();
        testSubject.emit(any -> true, Optional.of("text1"));
        testSubject.emit(any -> true, Optional.of("text2"));
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext(Optional.of("text1"),Optional.of("text2"))
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
