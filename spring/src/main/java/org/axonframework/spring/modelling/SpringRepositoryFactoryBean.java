/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.modelling;

import org.axonframework.config.Configuration;
import org.axonframework.modelling.command.Repository;
import org.axonframework.spring.config.SpringAggregateConfigurer;
import org.springframework.beans.factory.FactoryBean;

/**
 * Supplies the {@link Repository} created by the {@link SpringAggregateConfigurer} to the Spring Application Context.
 * This will allow it to be injected as a bean.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.1
 * @param <T> The aggregate type
 */
public class SpringRepositoryFactoryBean<T> implements FactoryBean<Repository<T>> {

    private final Class<T> aggregateClass;
    private Configuration configuration;

    /**
     * Constructs the {@link SpringRepositoryFactoryBean} for the provided {@code aggregateClass}.
     *
     * @param aggregateClass The aggregate's class
     */
    public SpringRepositoryFactoryBean(Class<T> aggregateClass) {
        this.aggregateClass = aggregateClass;
    }

    @Override
    public Repository<T> getObject() throws Exception {
        return configuration.aggregateConfiguration(aggregateClass).repository();
    }

    @Override
    public Class<?> getObjectType() {
        return Repository.class;
    }

    /**
     * Sets the {@link Configuration} that the {@link Repository} will be retrieved from.
     *
     * @param configuration The {@link Configuration} for usage in the factory bean.
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
