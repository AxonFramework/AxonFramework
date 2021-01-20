package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.jupiter.api.*;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

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
    void testCompletingRegistration() {
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
    void testCancelingRegistrationDoesNotCompleteFluxOfUpdates() {
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
}