package org.axonframework.boot;


import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.EnableAxon;
import org.axonframework.spring.config.SpringAxonAutoConfigurer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@EnableAxon
@ConditionalOnClass(SpringAxonAutoConfigurer.class)
@Configuration
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
public class AxonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        return new XStreamSerializer();
    }

    @ConditionalOnMissingClass("javax.persistence.EntityManager")
    @Configuration
    public static class InMemoryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }
    }

    @ConditionalOnMissingBean(EventStorageEngine.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @RegisterAxonDefaultEntities({"org.axonframework.eventsourcing.eventstore.jpa",
            "org.axonframework.eventhandling.tokenstore"})
    @Configuration
    public static class JpaConfiguration {

        @ConditionalOnMissingBean
        @Bean
        public EventStorageEngine eventStorageEngine(EntityManagerProvider entityManagerProvider) {
            return new JpaEventStorageEngine(entityManagerProvider);
        }

        @ConditionalOnBean(PlatformTransactionManager.class)
        @ConditionalOnMissingBean
        @Bean
        public CommandBus commandBus(TransactionManager txManager) {
            SimpleCommandBus commandBus = new SimpleCommandBus();
            commandBus.setTransactionManager(txManager);
            return commandBus;
        }

        @Bean
        @ConditionalOnMissingBean
        public TransactionManager getTransactionManager(PlatformTransactionManager txManager) {
            return new SpringTransactionManager(txManager);
        }

        @ConditionalOnMissingBean
        @Bean
        public EntityManagerProvider entityManagerProvider() {
            return new ContainerManagedEntityManagerProvider();
        }

        @ConditionalOnMissingBean
        @Bean
        public TokenStore tokenStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
            return new JpaTokenStore(entityManagerProvider, serializer);
        }
    }
}
