/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jpa;


import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JpaEventProcessingSdlqFactoryTest {

    private final TransactionManager transactionManager = mock(TransactionManager.class);
    private final EntityManagerProvider entityManagerProvider = mock(EntityManagerProvider.class);

    @SuppressWarnings("rawtypes")
    private JpaEventProcessingSdlqFactory.Builder builder;

    @BeforeEach
    void newBuilder() {
        builder = JpaEventProcessingSdlqFactory.builder();
    }

    @Test
    void whenAllRequiredPropertiesAreSetCreatesFactoryWhichCanCreateAQueue() {
        JpaEventProcessingSdlqFactory<?> factory = builder
                .transactionManager(transactionManager)
                .serializer(TestSerializer.JACKSON.getSerializer())
                .entityManagerProvider(entityManagerProvider)
                .build();

        assertNotNull(factory);
        assertNotNull(factory.getSdlq("group"));
    }

    @Test
    void whenAllPropertiesAreSetCreatesFactory() {
        JpaEventProcessingSdlqFactory<?> factory = builder
                .transactionManager(transactionManager)
                .serializer(TestSerializer.JACKSON.getSerializer())
                .entityManagerProvider(entityManagerProvider)
                .clearConverters()
                .addConverter(new EventMessageDeadLetterJpaConverter())
                .maxSequences(5)
                .maxSequenceSize(5)
                .queryPageSize(10)
                .claimDuration(Duration.of(10L, ChronoUnit.DAYS))
                .build();

        assertNotNull(factory);
    }

    @Test
    void whenEventSerializerIsMissingDontCreateFactory() {
        builder
                .transactionManager(transactionManager)
                .genericSerializer(TestSerializer.JACKSON.getSerializer())
                .entityManagerProvider(entityManagerProvider);

        assertThrows(AxonConfigurationException.class, () -> builder.build());
    }

    @Test
    void whenGenericSerializerIsMissingDontCreateFactory() {
        builder
                .transactionManager(transactionManager)
                .eventSerializer(TestSerializer.JACKSON.getSerializer())
                .entityManagerProvider(entityManagerProvider);

        assertThrows(AxonConfigurationException.class, () -> builder.build());
    }

    @Test
    void whenTransactionManagerIsMissingDontCreateFactory() {
        builder
                .serializer(TestSerializer.JACKSON.getSerializer())
                .entityManagerProvider(entityManagerProvider);

        assertThrows(AxonConfigurationException.class, () -> builder.build());
    }

    @Test
    void whenEntityManagerProviderIsMissingDontCreateFactory() {
        builder
                .transactionManager(transactionManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(AxonConfigurationException.class, () -> builder.build());
    }

    @Test
    void whenSettingEntityManagerProviderWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.entityManagerProvider(null));
    }

    @Test
    void whenSettingTransactionManagerWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.transactionManager(null));
    }

    @Test
    void whenSettingSerializerWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.serializer(null));
    }

    @Test
    void whenSettingEventSerializerWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.eventSerializer(null));
    }

    @Test
    void whenSettingGenericSerializerWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.genericSerializer(null));
    }

    @Test
    void whenAddingConverterWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.addConverter(null));
    }
}
