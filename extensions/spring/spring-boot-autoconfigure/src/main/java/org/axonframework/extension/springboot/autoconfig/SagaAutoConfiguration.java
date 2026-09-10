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

import org.axonframework.extension.spring.config.SagaProcessorConfigurer;
import org.axonframework.extension.spring.config.SpringSagaLookup;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * Autoconfiguration class for the Axon Framework 4 Saga carried by {@code axon-legacy}.
 * <p>
 * Constructs the {@link SpringSagaLookup} that discovers {@link Saga @Saga} annotated beans, and the fallback
 * {@link SagaStore} for an application that declares none. Active only when {@code axon-legacy} is on the classpath.
 * <p>
 * Runs after the JPA and JDBC Saga auto configurations, so that a persistent store wins over the in-memory fallback.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@AutoConfiguration(after = {JpaSagaAutoConfiguration.class, JdbcSagaAutoConfiguration.class})
@ConditionalOnClass(SagaStore.class)
public class SagaAutoConfiguration {

    /**
     * Provides the Spring Saga lookup.
     *
     * @return the lookup scanning for {@link Saga @Saga} annotations for later Saga registrations
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringSagaLookup springSagaLookup() {
        return new SpringSagaLookup();
    }

    /**
     * Provides the Saga processor wiring, building one dedicated event processor per {@link Saga @Saga} bean.
     *
     * @return the enhancer building a Saga's dedicated event processor
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SagaProcessorConfigurer sagaProcessorConfigurer() {
        return new SagaProcessorConfigurer();
    }

    /**
     * Provides an in-memory {@link SagaStore}, for an application that declares no store of its own and has neither
     * JPA nor JDBC available.
     * <p>
     * Axon Framework 4 fell back to this store too, from inside its {@code EventProcessingModule}, so a project
     * migrating a Saga that was never given a store keeps running rather than failing to start. Note that this means
     * Saga state does not survive a restart, exactly as it did not before.
     *
     * @return an in-memory Saga store
     */
    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    public SagaStore<Object> sagaStore() {
        return new InMemorySagaStore();
    }
}
