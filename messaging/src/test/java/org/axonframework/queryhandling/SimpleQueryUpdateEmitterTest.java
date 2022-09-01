package org.axonframework.queryhandling;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

/**
 * Test class validating the {@link SimpleQueryUpdateEmitter}.
 *
 * @author Allard Buijze
 * @author Corrado Musumeci
 * @author Steven van Beelen
 */
class SimpleQueryUpdateEmitterTest {

    private final SimpleQueryUpdateEmitter testSubject = SimpleQueryUpdateEmitter.builder().build();

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
        testSubject.emit(any -> true, List.of("text1","text2"));
        testSubject.emit(any -> true, List.of("text3","text4"));
        result.complete();

        StepVerifier.create(result.getUpdates().map(Message::getPayload))
                    .expectNext(List.of("text1","text2"), List.of("text3","text4"))
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