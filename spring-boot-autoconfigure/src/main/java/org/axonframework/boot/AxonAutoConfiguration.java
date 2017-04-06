package org.axonframework.boot;

import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.amqp.eventhandling.PackageRoutingKeyResolver;
import org.axonframework.amqp.eventhandling.RoutingKeyResolver;
import org.axonframework.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.commandhandling.distributed.jgroups.JGroupsConnectorFactoryBean;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.config.EnableAxon;
import org.axonframework.spring.config.SpringAxonAutoConfigurer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.springcloud.commandhandling.SpringCloudCommandRouter;
import org.axonframework.springcloud.commandhandling.SpringHttpCommandBusConnector;
import org.jgroups.stack.GossipRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@EnableAxon
@ConditionalOnClass(SpringAxonAutoConfigurer.class)
@Configuration
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@EnableConfigurationProperties(value = {
        EventProcessorProperties.class,
        DistributedCommandBusProperties.class
})
public class AxonAutoConfiguration implements BeanClassLoaderAware {

    @Autowired
    private EventProcessorProperties eventProcessorProperties;

    private ClassLoader beanClassLoader;

    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        XStreamSerializer xStreamSerializer = new XStreamSerializer();
        xStreamSerializer.getXStream().setClassLoader(beanClassLoader);
        return xStreamSerializer;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationDataProvider messageOriginProvider() {
        return new MessageOriginProvider();
    }

    @Qualifier("eventStore")
    @Bean(name = "eventBus")
    @ConditionalOnMissingBean({EventBus.class, EventStore.class})
    @ConditionalOnBean(EventStorageEngine.class)
    public EventStore eventStore(EventStorageEngine storageEngine, AxonConfiguration configuration) {
        return new EmbeddedEventStore(storageEngine, configuration.messageMonitor(EventStore.class, "eventStore"));
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class, EventStore.class})
    public EventBus eventBus(AxonConfiguration configuration) {
        return new SimpleEventBus(Integer.MAX_VALUE, configuration.messageMonitor(EventStore.class, "eventStore"));
    }

    @Autowired
    public void configureEventHandling(EventHandlingConfiguration eventHandlingConfiguration,
                                       ApplicationContext applicationContext) {
        eventProcessorProperties.getProcessors().forEach((k, v) -> {
            if (v.getMode() == EventProcessorProperties.Mode.TRACKING) {
                if (v.getSource() == null) {
                    eventHandlingConfiguration.registerTrackingProcessor(k);
                } else {
                    eventHandlingConfiguration.registerTrackingProcessor(k, c -> applicationContext
                            .getBean(v.getSource(), StreamableMessageSource.class));
                }
            } else {
                if (v.getSource() == null) {
                    eventHandlingConfiguration.registerSubscribingEventProcessor(k);
                } else {
                    eventHandlingConfiguration.registerSubscribingEventProcessor(k, c -> applicationContext
                            .getBean(v.getSource(), SubscribableMessageSource.class));
                }
            }
        });
    }

    @ConditionalOnMissingBean(ignored = {DistributedCommandBus.class})
    @Qualifier("localSegment")
    @Bean
    public CommandBus commandBus(TransactionManager txManager, AxonConfiguration axonConfiguration) {
        SimpleCommandBus commandBus = new SimpleCommandBus(txManager, axonConfiguration.messageMonitor(CommandBus.class, "commandBus"));
        commandBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));
        return commandBus;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnExpression("${axon.distributed.enabled:false} || ${axon.distributed.jgroups.enabled:false}")
    //@ConditionalOnExpression can be replaced for @ConditionalOnProperty once the deprecated jgroups.enabled is removed
    public DistributedCommandBus distributedCommandBus(CommandRouter router,
                                                       CommandBusConnector connector,
                                                       DistributedCommandBusProperties distributedCommandBusProperties) {
        DistributedCommandBus commandBus = new DistributedCommandBus(router, connector);
        commandBus.updateLoadFactor(distributedCommandBusProperties.getLoadFactor());
        return commandBus;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @AutoConfigureAfter(TransactionConfiguration.class)
    @Configuration
    public static class DefaultTransactionConfiguration {

        @Bean
        @ConditionalOnMissingBean(TransactionManager.class)
        public TransactionManager axonTransactionManager() {
            return NoTransactionManager.INSTANCE;
        }

    }

    @Configuration
    @ConditionalOnClass(PlatformTransactionManager.class)
    @AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
    public static class TransactionConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(PlatformTransactionManager.class)
        public TransactionManager axonTransactionManager(PlatformTransactionManager transactionManager) {
            return new SpringTransactionManager(transactionManager);
        }
    }

    @ConditionalOnBean(EntityManagerFactory.class)
    @RegisterDefaultEntities(packages = {"org.axonframework.eventsourcing.eventstore.jpa",
            "org.axonframework.eventhandling.tokenstore",
            "org.axonframework.eventhandling.saga.repository.jpa"})
    @Configuration
    public static class JpaConfiguration {

        @ConditionalOnMissingBean
        @Bean
        public EventStorageEngine eventStorageEngine(Serializer serializer,
                                                     PersistenceExceptionResolver persistenceExceptionResolver,
                                                     AxonConfiguration configuration,
                                                     EntityManagerProvider entityManagerProvider,
                                                     TransactionManager transactionManager) {
            return new JpaEventStorageEngine(serializer, configuration.getComponent(EventUpcaster.class),
                                             persistenceExceptionResolver, null, entityManagerProvider,
                                             transactionManager, null, null, true);
        }

        @ConditionalOnMissingBean
        @ConditionalOnBean(DataSource.class)
        @Bean
        public PersistenceExceptionResolver dataSourcePersistenceExceptionResolver(DataSource dataSource) throws SQLException {
            return new SQLErrorCodesResolver(dataSource);
        }

        @ConditionalOnMissingBean({DataSource.class, PersistenceExceptionResolver.class})
        @Bean
        public PersistenceExceptionResolver jdbcSQLErrorCodesResolver() {
            return new JdbcSQLErrorCodesResolver();
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

        @ConditionalOnMissingBean(SagaStore.class)
        @Bean
        public JpaSagaStore sagaStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
            return new JpaSagaStore(serializer, entityManagerProvider);
        }
    }

    @ConditionalOnClass(SpringAMQPPublisher.class)
    @ConditionalOnBean(ConnectionFactory.class)
    @EnableConfigurationProperties(AMQPProperties.class)
    @Configuration
    public static class AMQPConfiguration {

        @Autowired
        private AMQPProperties amqpProperties;

        @ConditionalOnMissingBean
        @Bean
        public RoutingKeyResolver routingKeyResolver() {
            return new PackageRoutingKeyResolver();
        }

        @ConditionalOnMissingBean
        @Bean
        public AMQPMessageConverter amqpMessageConverter(Serializer serializer, RoutingKeyResolver routingKeyResolver) {
            return new DefaultAMQPMessageConverter(serializer, routingKeyResolver, amqpProperties.isDurableMessages());
        }

        @ConditionalOnProperty("axon.amqp.exchange")
        @Bean(initMethod = "start", destroyMethod = "shutDown")
        public SpringAMQPPublisher amqpBridge(EventBus eventBus, ConnectionFactory connectionFactory,
                                              AMQPMessageConverter amqpMessageConverter) {
            SpringAMQPPublisher publisher = new SpringAMQPPublisher(eventBus);
            publisher.setExchangeName(amqpProperties.getExchange());
            publisher.setConnectionFactory(connectionFactory);
            publisher.setMessageConverter(amqpMessageConverter);
            switch (amqpProperties.getTransactionMode()) {

                case TRANSACTIONAL:
                    publisher.setTransactional(true);
                    break;
                case PUBLISHER_ACK:
                    publisher.setWaitForPublisherAck(true);
                    break;
                case NONE:
                    break;
                default:
                    throw new IllegalStateException("Unknown transaction mode: " + amqpProperties.getTransactionMode());
            }
            return publisher;
        }
    }

    @Configuration
    @AutoConfigureAfter(JpaConfiguration.class)
    @ConditionalOnProperty("axon.distributed.enabled")
    @ConditionalOnBean(value = { DiscoveryClient.class, RestTemplate.class })
    public static class SpringCloudConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CommandRouter springCloudCommandRouter(DiscoveryClient discoveryClient) {
            return new SpringCloudCommandRouter(discoveryClient, new AnnotationRoutingStrategy());
        }

        @Bean
        @ConditionalOnMissingBean
        public CommandBusConnector springHttpCommandBusConnector(@Qualifier("localSegment") CommandBus localSegment,
                                                                 RestTemplate restTemplate,
                                                                 Serializer serializer) {
            return new SpringHttpCommandBusConnector(localSegment, restTemplate, serializer);
        }

    }

    @ConditionalOnClass(name = { "org.axonframework.jgroups.commandhandling.JGroupsConnector", "org.jgroups.JChannel" })
    @ConditionalOnProperty("axon.distributed.jgroups.enabled")
    @AutoConfigureAfter(SpringCloudConfiguration.class)
    @Configuration
    public static class JGroupsConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(JGroupsConfiguration.class);

        @Autowired
        private DistributedCommandBusProperties properties;

        @ConditionalOnProperty("axon.distributed.jgroups.gossip.autoStart")
        @Bean(destroyMethod = "stop")
        public GossipRouter gossipRouter() {
            Matcher matcher =
                    Pattern.compile("([^[\\[]]*)\\[(\\d*)\\]").matcher(properties.getJgroups().getGossip().getHosts());
            if (matcher.find()) {

                GossipRouter gossipRouter = new GossipRouter(matcher.group(1), Integer.parseInt(matcher.group(2)));
                try {
                    gossipRouter.start();
                } catch (Exception e) {
                    logger.warn("Unable to autostart start embedded Gossip server: {}", e.getMessage());
                }
                return gossipRouter;
            } else {
                logger.error("Wrong hosts pattern, cannot start embedded Gossip Router: " +
                                     properties.getJgroups().getGossip().getHosts());
            }
            return null;
        }


        @ConditionalOnMissingBean({CommandRouter.class, CommandBusConnector.class})
        @Bean
        public JGroupsConnectorFactoryBean jgroupsConnectorFactoryBean(Serializer serializer,
                                                                       @Qualifier("localSegment") CommandBus
                                                                               localSegment) {
            System.setProperty("jgroups.tunnel.gossip_router_hosts", properties.getJgroups().getGossip().getHosts());
            System.setProperty("jgroups.bind_addr", String.valueOf(properties.getJgroups().getBindAddr()));
            System.setProperty("jgroups.bind_port", String.valueOf(properties.getJgroups().getBindPort()));

            JGroupsConnectorFactoryBean jGroupsConnectorFactoryBean = new JGroupsConnectorFactoryBean();
            jGroupsConnectorFactoryBean.setClusterName(properties.getJgroups().getClusterName());
            jGroupsConnectorFactoryBean.setLocalSegment(localSegment);
            jGroupsConnectorFactoryBean.setSerializer(serializer);
            jGroupsConnectorFactoryBean.setConfiguration(properties.getJgroups().getConfigurationFile());
            return jGroupsConnectorFactoryBean;
        }

    }

}
