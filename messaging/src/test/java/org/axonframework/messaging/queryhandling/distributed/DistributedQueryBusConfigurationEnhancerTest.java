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

package org.axonframework.messaging.queryhandling.distributed;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.queryhandling.QueryBus;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBus;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBusConfiguration;
import org.axonframework.messaging.queryhandling.distributed.DistributedQueryBusConfigurationEnhancer;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;
import org.axonframework.messaging.queryhandling.interception.InterceptingQueryBus;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DistributedQueryBusConfigurationEnhancer}.
 *
 * @author Mateusz Nowak
 */
class DistributedQueryBusConfigurationEnhancerTest {

    @Test
    void enhancesComponentRegistryWithDistributedQueryBus() {
        // given
        QueryBusConnector mockConnector = mock(QueryBusConnector.class);

        // when
        Configuration config =
                MessagingConfigurer.create()
                        .componentRegistry(cr -> {
                            cr.registerComponent(QueryBusConnector.class, c -> mockConnector);
                            cr.registerEnhancer(new DistributedQueryBusConfigurationEnhancer());
                        })
                        .build();

        // then
        QueryBus queryBus = config.getComponent(QueryBus.class);
        assertInstanceOf(InterceptingQueryBus.class, queryBus);
        assertTrue(config.hasComponent(DistributedQueryBusConfiguration.class));

        // Verify that the delegate of InterceptingQueryBus is a DistributedQueryBus
        assertInstanceOf(DistributedQueryBus.class, extractDelegate(queryBus));
    }

    @Test
    void noEnhancementsWhenNoQueryBusConnectorPresent() {
        // given / when
        Configuration config =
                MessagingConfigurer.create()
                        .componentRegistry(cr -> cr.registerEnhancer(
                                new DistributedQueryBusConfigurationEnhancer()
                        ))
                        .build();

        // then
        QueryBus queryBus = config.getComponent(QueryBus.class);
        // Intercepting at all times, since we have a default CorrelationDataInterceptor.
        assertInstanceOf(InterceptingQueryBus.class, queryBus);
        assertFalse(config.hasComponent(DistributedQueryBusConfiguration.class));
    }

    private static QueryBus extractDelegate(QueryBus queryBus) {
        try {
            Field delegateField = InterceptingQueryBus.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            return (QueryBus) delegateField.get(queryBus);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to extract delegate from InterceptingQueryBus", e);
        }
    }
}
