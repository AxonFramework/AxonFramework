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

package org.axonframework.javax.springboot.autoconfig;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.javax.common.jpa.EntityManagerProvider;
import org.axonframework.javax.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.javax.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.javax.springboot.util.RegisterDefaultEntities;
import org.axonframework.javax.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.JdbcAutoConfiguration;
import org.axonframework.springboot.autoconfig.JpaAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.sql.SQLException;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * Auto configuration class for Axon's JPA specific infrastructure components.
 *
 * @author Allard Buijze
 * @since 3.0.3
 */
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
@AutoConfigureBefore({JpaAutoConfiguration.class, JdbcAutoConfiguration.class})
@RegisterDefaultEntities(packages = {
        "org.axonframework.javax.eventhandling.tokenstore",
        "org.axonframework.javax.eventhandling.deadletter.jpa",
        "org.axonframework.javax.modelling.saga.repository.jpa",
})
public class JpaJavaxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntityManagerProvider entityManagerProvider() {
        return new ContainerManagedEntityManagerProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenStore tokenStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
        return JpaTokenStore.builder()
                            .entityManagerProvider(entityManagerProvider)
                            .serializer(serializer)
                            .build();
    }

    @Lazy
    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    public JpaSagaStore sagaStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
        return JpaSagaStore.builder()
                           .entityManagerProvider(entityManagerProvider)
                           .serializer(serializer)
                           .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource)
            throws SQLException {
        return new SQLErrorCodesResolver(dataSource);
    }
}
