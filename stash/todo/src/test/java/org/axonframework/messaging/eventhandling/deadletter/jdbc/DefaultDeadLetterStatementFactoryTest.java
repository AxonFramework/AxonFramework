/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter.jdbc;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultDeadLetterStatementFactory}.
 *
 * @author Steven van Beelen
 */
class DefaultDeadLetterStatementFactoryTest {

    @Test
    void buildWithNullSchemaThrowsAxonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder = DefaultDeadLetterStatementFactory.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.schema(null));
    }

    @Test
    void buildWithNullGenericConverterThrowsAxonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder = DefaultDeadLetterStatementFactory.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.genericConverter(null));
    }

    @Test
    void buildWithNullEventConverterThrowsAxonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder = DefaultDeadLetterStatementFactory.builder();

        assertThrows(AxonConfigurationException.class, () -> testBuilder.eventConverter(null));
    }

    @Test
    void buildWithoutTheGenericConverterThrowsAxonConfigurationException() {
        JacksonConverter jacksonConverter = new JacksonConverter();
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder =
                DefaultDeadLetterStatementFactory.builder()
                                                 .eventConverter(new DelegatingEventConverter(jacksonConverter));

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutTheEventConverterThrowsAxonConfigurationException() {
        DefaultDeadLetterStatementFactory.Builder<?> testBuilder =
                DefaultDeadLetterStatementFactory.builder()
                                                 .genericConverter(new JacksonConverter());

        assertThrows(AxonConfigurationException.class, testBuilder::build);
    }
}
