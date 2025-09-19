/*
 * Copyright (c) 2010-2025. Axon Framework
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
import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.tracing.DefaultQueryUpdateEmitterSpanFactory;
import org.axonframework.queryhandling.tracing.QueryUpdateEmitterSpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.axonframework.messaging.responsetypes.ResponseTypes.*;
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
    private final QueryUpdateEmitterSpanFactory queryBusSpanFactory = DefaultQueryUpdateEmitterSpanFactory
            .builder()
            .spanFactory(spanFactory)
            .build();
    private final SimpleQueryUpdateEmitter testSubject = new SimpleQueryUpdateEmitter();

    @Test
    void completingRegistrationOldApi() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(queryMessage, 1024);

        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext("some-awesome-text")
                    .verifyComplete();
    }

    @Test
    void concurrentUpdateEmitting() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration registration = testSubject.registerUpdateHandler(queryMessage, 128);

        ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 100; i++) {
            executors.submit(() -> testSubject.emit(q -> true, "Update"));
        }
        executors.shutdown();
        StepVerifier.create(registration.updates())
                    .expectNextCount(100)
                    .then(() -> testSubject.complete(q -> true))
                    .verifyComplete();
    }

    @Test
    void concurrentUpdateEmitting_WithBackpressure() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration registration = testSubject.registerUpdateHandler(queryMessage, 128);

        ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 100; i++) {
            executors.submit(() -> testSubject.emit(q -> true, "Update"));
        }
        executors.shutdown();
        StepVerifier.create(registration.updates())
                    .expectNextCount(100)
                    .then(() -> testSubject.complete(q -> true))
                    .verifyComplete();
    }

    @Test
    void cancelingRegistrationDoesNotCompleteFluxOfUpdatesOldApi() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(queryMessage, 1024);

        testSubject.emit(any -> true, "some-awesome-text");
        result.registration().cancel();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext("some-awesome-text")
                    .verifyTimeout(Duration.ofMillis(500));
    }

    @Test
    void completingRegistration() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext("some-awesome-text")
                    .verifyComplete();
    }

    @Test
    void queryUpdateEmitterIsTraced() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        result.complete();

        spanFactory.verifySpanCompleted("QueryUpdateEmitter.scheduleQueryUpdateMessage");
        spanFactory.verifySpanHasType("QueryUpdateEmitter.scheduleQueryUpdateMessage",
                                      TestSpanFactory.TestSpanType.INTERNAL);
        spanFactory.verifySpanCompleted("QueryUpdateEmitter.emitQueryUpdateMessage");
        spanFactory.verifySpanHasType("QueryUpdateEmitter.emitQueryUpdateMessage",
                                      TestSpanFactory.TestSpanType.DISPATCH);
    }

    @Test
    void differentUpdateAreDisambiguatedAndWrongTypesAreFilteredBasedOnQueryTypes() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(Integer.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext(1234)
                    .verifyComplete();
    }

    @Test
    void updateResponseTypeFilteringWorksForMultipleInstanceOfWithArrayAndList() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), multipleInstancesOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        testSubject.emit(any -> true, Optional.of("optional-payload"));
        testSubject.emit(any -> true, Optional.empty());
        testSubject.emit(any -> true, new String[]{"array-item-1", "array-item-2"});
        testSubject.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        testSubject.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        testSubject.emit(any -> true, Mono.just("mono-item"));
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNextMatches(actual -> equalTo(new String[]{"array-item-1", "array-item-2"}).matches(actual))
                    .expectNextMatches(actual -> equalTo(Arrays.asList("list-item-1", "list-item-2")).matches(actual))
                    .verifyComplete();
    }

    @Test
    void updateResponseTypeFilteringWorksForOptionalInstanceOf() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), optionalInstanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        testSubject.emit(any -> true, Optional.of("optional-payload"));
        testSubject.emit(any -> true, Optional.empty());
        testSubject.emit(any -> true, new String[]{"array-item-1", "array-item-2"});
        testSubject.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        testSubject.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        testSubject.emit(any -> true, Mono.just("mono-item"));
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext(Optional.of("optional-payload"), Optional.empty())
                    .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateResponseTypeFilteringWorksForPublisherOf() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), publisherOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        testSubject.emit(any -> true, 1234);
        testSubject.emit(any -> true, Optional.of("optional-payload"));
        testSubject.emit(any -> true, Optional.empty());
        testSubject.emit(any -> true, new String[]{"array-item-1", "array-item-2"});
        testSubject.emit(any -> true, Arrays.asList("list-item-1", "list-item-2"));
        testSubject.emit(any -> true, Flux.just("flux-item-1", "flux-item-2"));
        testSubject.emit(any -> true, Mono.just("mono-item"));
        testSubject.emit(any -> true, Mono.empty());
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNextMatches(publisher -> {
                        try {
                            StepVerifier.create((Publisher<String>) publisher)
                                        .expectNext("flux-item-1", "flux-item-2")
                                        .verifyComplete();
                        } catch (Exception e) {
                            return false;
                        }
                        return true;
                    })
                    .expectNextMatches(publisher -> {
                        try {
                            StepVerifier.create((Publisher<String>) publisher)
                                        .expectNext("mono-item").verifyComplete();
                        } catch (Exception e) {
                            return false;
                        }
                        return true;
                    })
                    .expectNextMatches(publisher -> {
                        try {
                            StepVerifier.create((Publisher<String>) publisher)
                                        .verifyComplete();
                        } catch (Exception e) {
                            return false;
                        }
                        return true;
                    })
                    .verifyComplete();
    }

    @Test
    void multipleInstanceUpdatesAreDelivered() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), multipleInstancesOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, Arrays.asList("text1", "text2"));
        testSubject.emit(any -> true, Arrays.asList("text3", "text4"));
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext(Arrays.asList("text1", "text2"), Arrays.asList("text3", "text4"))
                    .verifyComplete();
    }

    @Test
    void optionalUpdatesAreDelivered() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                optionalInstanceOf(String.class), optionalInstanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, Optional.of("text1"));
        testSubject.emit(any -> true, Optional.of("text2"));
        result.complete();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext(Optional.of("text1"), Optional.of("text2"))
                    .verifyComplete();
    }

    @Test
    void cancelingRegistrationDoesNotCompleteFluxOfUpdates() {
        SubscriptionQueryMessage queryMessage = new GenericSubscriptionQueryMessage(
                new MessageType("chatMessages"), "some-payload",
                multipleInstancesOf(String.class), instanceOf(String.class)
        );

        UpdateHandlerRegistration result = testSubject.registerUpdateHandler(
                queryMessage,
                1024
        );

        result.updates().subscribe();
        testSubject.emit(any -> true, "some-awesome-text");
        result.registration().cancel();

        StepVerifier.create(result.updates().map(Message::payload))
                    .expectNext("some-awesome-text")
                    .verifyTimeout(Duration.ofMillis(500));
    }
}
