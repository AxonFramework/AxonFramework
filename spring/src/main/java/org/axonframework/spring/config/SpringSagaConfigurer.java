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

package org.axonframework.spring.config;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A {@link ConfigurationEnhancer} implementation that configures a Saga based on configuration found in the Application
 * Context.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
// TODO #3097 Fix as part of referred to issue
public class SpringSagaConfigurer implements ConfigurationEnhancer, ApplicationContextAware {

    private final Class<?> sagaType;
    private String sagaStore;
    private ApplicationContext applicationContext;

    /**
     * Initialize the Saga for given {@code sagaType}.
     *
     * @param sagaType The type of Saga to configure.
     */
    public SpringSagaConfigurer(Class<?> sagaType) {
        this.sagaType = sagaType;
    }

    /**
     * Sets the bean name of the {@link SagaStore} to configure.
     *
     * @param sagaStore the bean name of the {@link SagaStore} to configure.
     */
    public void setSagaStore(String sagaStore) {
        this.sagaStore = sagaStore;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
//        configurer.eventProcessing()
//                  .registerSaga(sagaType,
//                                sagaConfigurer -> {
//                                    if (sagaStore != null && !"".equals(sagaStore)) {
//                                        noinspection unchecked
//                                        sagaConfigurer.configureSagaStore(
//                                                c -> applicationContext.getBean(sagaStore, SagaStore.class)
//                                        );
//                                    }
//                                });
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
