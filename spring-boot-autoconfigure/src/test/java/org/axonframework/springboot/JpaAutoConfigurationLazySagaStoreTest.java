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

package org.axonframework.springboot;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests JPA auto-configuration lazy initializes the provided {@link SagaStore} bean by verifying that the mocked
 * {@link EntityManager} is not used until the {@code SagaStore} is retrieved from the {@link ApplicationContext}.
 *
 * @author Steven van Beelen
 */
@EnableAutoConfiguration(exclude = {
        AxonServerAutoConfiguration.class,
        AxonServerBusAutoConfiguration.class,
        AxonServerActuatorAutoConfiguration.class
})
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(classes = JpaAutoConfigurationLazySagaStoreTest.TestContext.class)
class JpaAutoConfigurationLazySagaStoreTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * The last {@link org.mockito.Mockito#verify(Object)} operation checks whether the
     * {@code JpaSagaStore#addNamedQueriesTo(EntityManager)} operation is called, signaling that the
     * {@code JpaSagaStore} is being initialized.
     */
    @Test
    void contextInitialization() {
        EntityManagerProvider entityManagerProvider = applicationContext.getBean(EntityManagerProvider.class);
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        verifyNoInteractions(entityManager);

        SagaStore<?> sagaStore = applicationContext.getBean(SagaStore.class);
        assertTrue(sagaStore instanceof JpaSagaStore);

        verify(entityManager).getEntityManagerFactory();
    }

    static class TestContext {

        @Bean
        public EntityManagerProvider entityManagerProvider() {
            EntityManager entityManager = mock(EntityManager.class);
            when(entityManager.getEntityManagerFactory()).thenReturn(mock(EntityManagerFactory.class));

            EntityManagerProvider entityManagerProvider = mock(EntityManagerProvider.class);
            when(entityManagerProvider.getEntityManager()).thenReturn(entityManager);

            return entityManagerProvider;
        }
    }
}
