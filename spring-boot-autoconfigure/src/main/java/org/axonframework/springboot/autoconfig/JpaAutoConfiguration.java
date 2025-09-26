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
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.json.JacksonSerializer;
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
     * @param entityManagerProvider An entity manager provider to retrieve connections.
     * @param tokenStoreProperties  A set of properties to configure the token store.
     * @param objectMapper An object mapper to use for token conversion to JSON.
     * @return instance of JPA token store.
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenStore tokenStore(EntityManagerProvider entityManagerProvider,
                                 TokenStoreProperties tokenStoreProperties,
                                 ObjectMapper objectMapper) {
        // FIXME -> replace with converter
        var serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();
        return JpaTokenStore.builder()
                            .entityManagerProvider(entityManagerProvider)
                            .serializer(serializer)
                            .claimTimeout(tokenStoreProperties.getClaimTimeout())
                            .build();
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

    /*
    TODO re-enable as part of #3097
    @Lazy
    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    public JpaSagaStore sagaStore(EntityManagerProvider entityManagerProvider) {
        return JpaSagaStore.builder()
                           .entityManagerProvider(entityManagerProvider)
                           .build();
    }
     */

    /*
    TODO re-enable as part of #3517
    // tag::JpaDeadLetterQueueProviderConfigurerModule[]
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterQueueProviderConfigurerModule deadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager,
            Serializer genericSerializer,
            @Qualifier("eventSerializer") Serializer eventSerializer
    ) {
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties,
                processingGroup -> config -> JpaSequencedDeadLetterQueue.builder()
                                                                        .processingGroup(processingGroup)
                                                                        .entityManagerProvider(entityManagerProvider)
                                                                        .transactionManager(transactionManager)
                                                                        .genericSerializer(genericSerializer)
                                                                        .eventSerializer(eventSerializer)
                                                                        .build()
        );
    }
    // end::JpaDeadLetterQueueProviderConfigurerModule[]
    */
}
