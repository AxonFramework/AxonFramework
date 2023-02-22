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

package org.axonframework.springboot.legacyjpa;

import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.legacyjpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.legacyjpa.SQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.legacyjpa.JpaSagaStore;
import org.axonframework.springboot.util.legacyjpa.ContainerManagedEntityManagerProvider;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JPA auto-configuration
 *
 * @author Sara Pellegrini
 */
class JpaJavaxAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class)
                                                    .withPropertyValues("axon.axonserver.enabled=false");
    }

    @SuppressWarnings("deprecation")
    @Test
    void contextInitialization() {
        testContext.run(context -> {
            Map<String, EntityManagerProvider> entityManagerProviders =
                    context.getBeansOfType(EntityManagerProvider.class);
            assertTrue(entityManagerProviders.containsKey("entityManagerProvider"));
            assertEquals(ContainerManagedEntityManagerProvider.class,
                         entityManagerProviders.get("entityManagerProvider").getClass());

            Map<String, TokenStore> tokenStores =
                    context.getBeansOfType(TokenStore.class);
            assertTrue(tokenStores.containsKey("tokenStore"));
            assertEquals(JpaTokenStore.class,
                         tokenStores.get("tokenStore").getClass());

            //noinspection rawtypes
            Map<String, SagaStore> sagaStores =
                    context.getBeansOfType(SagaStore.class);
            assertTrue(sagaStores.containsKey("sagaStore"));
            assertEquals(JpaSagaStore.class,
                         sagaStores.get("sagaStore").getClass());

            Map<String, SQLErrorCodesResolver> persistenceExceptionResolvers =
                    context.getBeansOfType(SQLErrorCodesResolver.class);
            assertTrue(persistenceExceptionResolvers.containsKey("persistenceExceptionResolver"));
            assertEquals(SQLErrorCodesResolver.class,
                         persistenceExceptionResolvers.get("persistenceExceptionResolver").getClass());
        });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class TestContext {

    }
}
