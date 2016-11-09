package org.axonframework.boot;


import org.axonframework.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.config.EnableAxon;
import org.axonframework.spring.config.EventHandlingConfigurer;
import org.axonframework.spring.config.SpringAxonAutoConfigurer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
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
@EnableConfigurationProperties(EventProcessorProperties.class)
public class AxonAutoConfiguration {

    @Autowired
    private EventProcessorProperties eventProcessorProperties;

    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        return new XStreamSerializer();
    }

    @Bean
    public EventHandlingConfigurer eventHandlingConfigurer(ApplicationContext applicationContext) {
        return new EventHandlingConfigurer() {
            @Override
            protected void configure(EventHandlingConfiguration eventHandlingConfiguration) {
                eventProcessorProperties.getProcessors().forEach((k, v) -> {
                    if (v.getMode() == EventProcessorProperties.Mode.TRACKING) {
                        eventHandlingConfiguration.registerTrackingProcessor(k);
                    } else {
                        eventHandlingConfiguration.registerSubscribingEventProcessor(k, applicationContext.getBean(v.getSource(), SubscribableMessageSource.class));
                    }
                });
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PlatformTransactionManager.class)
    public TransactionManager getTransactionManager(PlatformTransactionManager txManager) {
        return new SpringTransactionManager(txManager);
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

    @ConditionalOnBean(EntityManagerFactory.class)
    @RegisterDefaultEntities(packages = {"org.axonframework.eventsourcing.eventstore.jpa",
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
        public CommandBus commandBus(TransactionManager txManager, AxonConfiguration axonConfiguration) {
            return new SimpleCommandBus(txManager, axonConfiguration.messageMonitor(CommandBus.class, "commandBus"));
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

    @ConditionalOnClass({SpringAMQPPublisher.class, ConnectionFactory.class})
    @EnableConfigurationProperties(AMQPProperties.class)
    @Configuration
    public static class AMQPConfiguration {

        @Autowired
        private AMQPProperties amqpProperties;

        @ConditionalOnProperty("axon.amqp.exchange")
        @ConditionalOnMissingBean
        @Bean(initMethod = "start", destroyMethod = "shutDown")
        public SpringAMQPPublisher amqpBridge(EventBus eventBus, ConnectionFactory connectionFactory) {
            SpringAMQPPublisher publisher = new SpringAMQPPublisher(eventBus);
            publisher.setExchangeName(amqpProperties.getExchange());
            publisher.setConnectionFactory(connectionFactory);
            return publisher;
        }
    }
}
