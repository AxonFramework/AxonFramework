package org.axonframework.queryhandling;

import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link UpdateHandlerRegistration}.
 *
 * @author Steven van Beelen
 */
class UpdateHandlerRegistrationTest {

    @Test
    void testCompleteClosesTheRegistration() {
        AtomicBoolean registrationInvocation = new AtomicBoolean(false);
        AtomicBoolean completeInvocation = new AtomicBoolean(false);

        UpdateHandlerRegistration<Object> testSubject = new UpdateHandlerRegistration<>(
                () -> {
                    registrationInvocation.set(true);
                    return true;
                },
                Flux.empty(),
                () -> completeInvocation.set(true)
        );

        testSubject.complete();

        assertTrue(registrationInvocation.get());
        assertTrue(completeInvocation.get());
    }
}