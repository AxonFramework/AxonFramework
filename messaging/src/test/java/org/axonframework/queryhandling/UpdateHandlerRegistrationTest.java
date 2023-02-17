/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    void completeClosesTheRegistration() {
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
