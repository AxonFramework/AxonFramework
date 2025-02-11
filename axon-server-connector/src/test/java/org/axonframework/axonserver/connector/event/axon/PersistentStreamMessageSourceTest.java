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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.common.Registration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentStreamMessageSourceTest {

    private static ScheduledExecutorService TEST_SCHEDULER;

    @Mock
    private Consumer<List<? extends EventMessage<?>>> eventConsumer;

    private PersistentStreamMessageSource messageSource;

    @BeforeAll
    static void beforeAll() {
        TEST_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    static void afterAll() {
        TEST_SCHEDULER.shutdown();
    }

    @BeforeEach
    void setUp() {
        String streamName = UUID.randomUUID().toString();
        Configuration configurer = DefaultConfigurer.defaultConfiguration()
                                                    .buildConfiguration();
        messageSource = new PersistentStreamMessageSource(
                streamName,
                configurer,
                new PersistentStreamProperties(streamName, 1, "example", Collections.emptyList(), "HEAD", null),
                TEST_SCHEDULER,
                1
        );
    }

    @Test
    void subscribeShouldReturnValidRegistration() {
        // when
        Registration registration = messageSource.subscribe(eventConsumer);

        // then
        assertNotNull(registration);
        assertTrue(registration.cancel());
    }

    @Test
    void subscribingTwiceWithSameConsumerShouldNotThrowException() {
        // given
        messageSource.subscribe(eventConsumer);

        // when/then
        assertDoesNotThrow(() -> messageSource.subscribe(eventConsumer));
    }

    @Test
    void subscribingWithDifferentConsumerShouldThrowException() {
        // given
        messageSource.subscribe(eventConsumer);
        Consumer<List<? extends EventMessage<?>>> anotherConsumer = mock(Consumer.class);

        // when/then
        assertThrows(IllegalStateException.class,
                     () -> messageSource.subscribe(anotherConsumer));
    }

    @Test
    void cancellingRegistrationShouldAllowNewSubscription() {
        // given
        Registration registration = messageSource.subscribe(eventConsumer);
        registration.cancel();
        Consumer<List<? extends EventMessage<?>>> newConsumer = mock(Consumer.class);

        // when/then
        assertDoesNotThrow(() -> messageSource.subscribe(newConsumer));
    }

    @Test
    void registrationCancelShouldBeIdempotent() {
        // given
        Registration registration = messageSource.subscribe(eventConsumer);

        // when
        boolean firstCancel = registration.cancel();
        boolean secondCancel = registration.cancel();

        // then
        assertTrue(firstCancel);
        assertTrue(secondCancel);
    }
}