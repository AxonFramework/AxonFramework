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

package org.axonframework.messaging.core.correlation;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.core.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.core.correlation.DefaultCorrelationDataProviderRegistry;
import org.axonframework.messaging.core.correlation.MessageOriginProvider;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultCorrelationDataProviderRegistry}.
 *
 * @author Steven van Beelen
 */
class DefaultCorrelationDataProviderRegistryTest {

    private CorrelationDataProviderRegistry testSubject;

    private Configuration config;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultCorrelationDataProviderRegistry();

        config = mock(Configuration.class);
    }

    @Test
    void registeredCorrelationDataProvidersCanBeRetrieved() {
        CorrelationDataProvider testProvider = new MessageOriginProvider();
        testSubject.registerProvider(c -> testProvider);

        List<CorrelationDataProvider> providers = testSubject.correlationDataProviders(config);
        assertThat(providers).size().isEqualTo(1);
        assertThat(providers).contains(testProvider);
    }

    @Test
    void registeredCorrelationDataProvidersAreCreatedOnlyOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        testSubject.registerProvider(c -> {
            counter.incrementAndGet();
            return new MessageOriginProvider();
        });

        testSubject.correlationDataProviders(config);
        testSubject.correlationDataProviders(config);
        testSubject.correlationDataProviders(config);
        assertThat(counter).hasValue(1);
    }
}