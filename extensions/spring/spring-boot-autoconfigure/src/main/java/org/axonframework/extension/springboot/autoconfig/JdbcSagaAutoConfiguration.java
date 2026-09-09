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

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.conversion.GeneralConverter;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.jdbc.GenericSagaSqlSchema;
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.axonframework.modelling.saga.repository.jdbc.SagaSqlSchema;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Autoconfiguration class for the JDBC {@link SagaStore} of the Axon Framework 4 Saga carried by {@code axon-legacy}.
 * <p>
 * Active only when {@code axon-legacy} is on the classpath and the application has a {@link DataSource}, and only
 * registers a store when the application declares none itself. Runs after {@link JpaSagaAutoConfiguration}, so that an
 * application with both JPA and JDBC available keeps the JPA store, as it did in Axon Framework 4.
 * <p>
 * The two mutually exclusive beans are Axon Framework 4's arrangement: an application declaring its own
 * {@link SagaSqlSchema} gets a store using it, and an application declaring none gets a store on the generic schema.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@AutoConfiguration(after = {JpaSagaAutoConfiguration.class, JdbcAutoConfiguration.class})
@ConditionalOnClass(JdbcSagaStore.class)
@ConditionalOnBean({DataSource.class, ConnectionProvider.class})
public class JdbcSagaAutoConfiguration {

    /**
     * Builds a JDBC {@link SagaStore} on the generic schema.
     *
     * @param connectionProvider the provider of the connections the store runs its statements on
     * @param converter          the converter converting a Saga to and from its stored form
     * @return a JDBC Saga store on the generic schema
     */
    @Bean
    @ConditionalOnMissingBean({SagaStore.class, SagaSqlSchema.class})
    public JdbcSagaStore sagaStoreNoSchema(ConnectionProvider connectionProvider, GeneralConverter converter) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .converter(converter)
                            .sqlSchema(new GenericSagaSqlSchema())
                            .build();
    }

    /**
     * Builds a JDBC {@link SagaStore} on the {@link SagaSqlSchema} the application declared.
     *
     * @param connectionProvider the provider of the connections the store runs its statements on
     * @param converter          the converter converting a Saga to and from its stored form
     * @param sqlSchema          the schema describing the Saga tables
     * @return a JDBC Saga store on the given schema
     */
    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    @ConditionalOnBean(SagaSqlSchema.class)
    public JdbcSagaStore sagaStoreWithSchema(ConnectionProvider connectionProvider,
                                             GeneralConverter converter,
                                             SagaSqlSchema sqlSchema) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .converter(converter)
                            .sqlSchema(sqlSchema)
                            .build();
    }
}
