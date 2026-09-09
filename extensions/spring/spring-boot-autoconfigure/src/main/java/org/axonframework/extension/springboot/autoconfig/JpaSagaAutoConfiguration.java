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

package org.axonframework.extension.springboot.autoconfig;

import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.conversion.GeneralConverter;
import org.axonframework.extension.springboot.util.RegisterDefaultEntities;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * Autoconfiguration class for the JPA {@link SagaStore} of the Axon Framework 4 Saga carried by {@code axon-legacy}.
 * <p>
 * Active only when {@code axon-legacy} is on the classpath and the application has an {@link EntityManagerFactory},
 * and only registers a store when the application declares none itself.
 * <p>
 * Separate from {@link JpaAutoConfiguration}, which is where Axon Framework 4 declared this bean. The Saga entities
 * have to be added to the persistence unit through {@link RegisterDefaultEntities}, and that annotation cannot name a
 * package that may be absent, which it would be for an application without {@code axon-legacy}.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@AutoConfiguration(after = JpaAutoConfiguration.class)
@ConditionalOnClass({JpaSagaStore.class, EntityManagerFactory.class})
@ConditionalOnBean(EntityManagerFactory.class)
@RegisterDefaultEntities(packages = "org.axonframework.modelling.saga.repository.jpa")
public class JpaSagaAutoConfiguration {

    /**
     * Builds a JPA {@link SagaStore}.
     * <p>
     * Lazy, as it was in Axon Framework 4, so that an application without Sagas does not touch its
     * {@link jakarta.persistence.EntityManager EntityManager} on account of this bean existing.
     *
     * @param entityManagerProvider the provider of the entity manager persisting the Sagas
     * @param converter             the converter converting a Saga to and from its stored form
     * @return a JPA Saga store
     */
    @Lazy
    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    public JpaSagaStore sagaStore(EntityManagerProvider entityManagerProvider, GeneralConverter converter) {
        return JpaSagaStore.builder()
                           .entityManagerProvider(entityManagerProvider)
                           .converter(converter)
                           .build();
    }
}
