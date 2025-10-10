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

package org.axonframework.springboot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.jpa.JpaTokenStore;
import org.axonframework.eventhandling.processors.streaming.token.store.jpa.JpaTokenStoreConfiguration;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.json.JacksonConverter;
import org.axonframework.springboot.TokenStoreProperties;
import org.axonframework.springboot.util.RegisterDefaultEntities;
import org.axonframework.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Autoconfiguration class for Axon's JPA specific infrastructure components.
 *
 * @author Allard Buijze
 * @author Simon Zambrovski
 * @since 3.0.3
 */
@AutoConfiguration
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnBean(EntityManagerFactory.class)
@EnableConfigurationProperties(TokenStoreProperties.class)
@RegisterDefaultEntities(packages = {
        "org.axonframework.eventhandling.tokenstore",
        //  "org.axonframework.eventhandling.deadletter.jpa", // TODO re-enable as part of #3097
        // "org.axonframework.modelling.saga.repository.jpa", // TODO re-enable as part of #3517
})
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
public class JpaAutoConfiguration {


    /**
     * Retrieves an entity manager provider.
     *
     * @return an entity manager provider.
     */
    @Bean
    @ConditionalOnMissingBean
    public EntityManagerProvider entityManagerProvider() {
        return new ContainerManagedEntityManagerProvider();
    }

    /**
     * Builds a JPA Token Store.
     *
     * @param entityManagerProvider   An entity manager provider to retrieve connections.
     * @param tokenStoreProperties    A set of properties to configure the token store.
     * @param defaultAxonObjectMapper An object mapper to use for token conversion to JSON.
     * @return instance of JPA token store.
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenStore tokenStore(EntityManagerProvider entityManagerProvider,
                                 TokenStoreProperties tokenStoreProperties,
                                 ObjectMapper defaultAxonObjectMapper) {
        var config = JpaTokenStoreConfiguration.DEFAULT.claimTimeout(tokenStoreProperties.getClaimTimeout());
        var converter = new JacksonConverter(defaultAxonObjectMapper);
        return new JpaTokenStore(entityManagerProvider, converter, config);
    }

    /**
     * Provides a persistence exception resolver for a data source.
     *
     * @param dataSource A data source configured to resolve exception for.
     * @return A working copy of Persistence Exception Resolver.
     * @throws SQLException on any construction errors.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource) throws SQLException {
        return new SQLErrorCodesResolver(dataSource);
    }
}