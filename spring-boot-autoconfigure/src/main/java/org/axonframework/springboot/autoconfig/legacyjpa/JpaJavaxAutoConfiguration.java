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

package org.axonframework.springboot.autoconfig.legacyjpa;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.legacyjpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.legacyjpa.SQLErrorCodesResolver;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.modelling.saga.repository.legacyjpa.JpaSagaStore;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.TokenStoreProperties;
import org.axonframework.springboot.autoconfig.JdbcAutoConfiguration;
import org.axonframework.springboot.autoconfig.JpaAutoConfiguration;
import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.axonframework.springboot.util.legacyjpa.ContainerManagedEntityManagerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 * @deprecated in favor of using {@link JpaAutoConfiguration} which moved to jakarta.
 */
@Deprecated
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@EnableConfigurationProperties(TokenStoreProperties.class)
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
@AutoConfigureBefore({JpaAutoConfiguration.class, JdbcAutoConfiguration.class})
@RegisterDefaultEntities(packages = {
        "org.axonframework.eventhandling.tokenstore.jpa",
        "org.axonframework.eventhandling.deadletter.jpa",
        "org.axonframework.modelling.saga.repository.jpa",
})
public class JpaJavaxAutoConfiguration {

    private final TokenStoreProperties tokenStoreProperties;

    public JpaJavaxAutoConfiguration(TokenStoreProperties tokenStoreProperties) {
        this.tokenStoreProperties = tokenStoreProperties;
    }

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
                            .claimTimeout(tokenStoreProperties.getClaimTimeout())
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
