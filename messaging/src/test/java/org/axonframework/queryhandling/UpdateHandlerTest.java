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

import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link UpdateHandler}.
 *
 * @author Steven van Beelen
 */
class UpdateHandlerTest {

    @Test
    void completeCancelsTheRegistration() {
        AtomicBoolean registrationInvocation = new AtomicBoolean(false);
        AtomicBoolean completeInvocation = new AtomicBoolean(false);

        UpdateHandler testSubject = new UpdateHandler(
                Flux.empty(),
                () -> registrationInvocation.set(true),
                () -> completeInvocation.set(true)
        );

        testSubject.complete();

        assertTrue(registrationInvocation.get());
        assertTrue(completeInvocation.get());
    }
}