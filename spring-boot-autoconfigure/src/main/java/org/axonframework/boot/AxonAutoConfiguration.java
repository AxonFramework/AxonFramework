package org.axonframework.boot;


import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.amqp.eventhandling.PackageRoutingKeyResolver;
import org.axonframework.amqp.eventhandling.RoutingKeyResolver;
import org.axonframework.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
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
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.commandhandling.distributed.jgroups.JGroupsConnectorFactoryBean;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.spring.config.EnableAxon;
import org.axonframework.spring.config.SpringAxonAutoConfigurer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.jgroups.stack.GossipRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
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

    @Autowired(required = false)
    // required set to false for live reload support (spring boot devtools). It has trouble starting with this
    // dependency.
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
        public EventStorageEngine eventStorageEngine(EntityManagerProvider entityManagerProvider,
                                                     TransactionManager transactionManager) {
            return new JpaEventStorageEngine(entityManagerProvider, transactionManager);
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

    @ConditionalOnClass({SpringAMQPPublisher.class, ConnectionFactory.class})
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

    @ConditionalOnClass(name = {"org.axonframework.jgroups.commandhandling.JGroupsConnector", "org.jgroups.JChannel"})
    @EnableConfigurationProperties(JGroupsConfiguration.JGroupsProperties.class)
    @ConditionalOnProperty("axon.distributed.jgroups.enabled")
    @AutoConfigureAfter(JpaConfiguration.class)
    @Configuration
    public static class JGroupsConfiguration {

        private static final Logger logger = LoggerFactory.getLogger(JGroupsConfiguration.class);
        @Autowired
        private JGroupsProperties jGroupsProperties;

        @ConditionalOnProperty("axon.distributed.jgroups.gossip.autoStart")
        @Bean(destroyMethod = "stop")
        public GossipRouter gossipRouter() {
            Matcher matcher =
                    Pattern.compile("([^[\\[]]*)\\[(\\d*)\\]").matcher(jGroupsProperties.getGossip().getHosts());
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
                                     jGroupsProperties.getGossip().getHosts());
            }
            return null;
        }

        @ConditionalOnMissingBean
        @Primary
        @Bean
        public DistributedCommandBus distributedCommandBus(CommandRouter router, CommandBusConnector connector) {
            DistributedCommandBus commandBus = new DistributedCommandBus(router, connector);
            commandBus.updateLoadFactor(jGroupsProperties.getLoadFactor());
            return commandBus;
        }

        @ConditionalOnMissingBean({CommandRouter.class, CommandBusConnector.class})
        @Bean
        public JGroupsConnectorFactoryBean jgroupsConnectorFactoryBean(Serializer serializer,
                                                                       @Qualifier("localSegment") CommandBus
                                                                               localSegment) {

            System.setProperty("jgroups.tunnel.gossip_router_hosts", jGroupsProperties.getGossip().getHosts());
            System.setProperty("jgroups.bind_addr", String.valueOf(jGroupsProperties.getBindAddr()));
            System.setProperty("jgroups.bind_port", String.valueOf(jGroupsProperties.getBindPort()));

            JGroupsConnectorFactoryBean jGroupsConnectorFactoryBean = new JGroupsConnectorFactoryBean();
            jGroupsConnectorFactoryBean.setClusterName(jGroupsProperties.getClusterName());
            jGroupsConnectorFactoryBean.setLocalSegment(localSegment);
            jGroupsConnectorFactoryBean.setSerializer(serializer);
            jGroupsConnectorFactoryBean.setConfiguration(jGroupsProperties.getConfigurationFile());
            return jGroupsConnectorFactoryBean;
        }

        @ConfigurationProperties(prefix = "axon.distributed.jgroups")
        public static class JGroupsProperties {

            private Gossip gossip;

            /**
             * Enables JGroups configuration for this application
             */
            private boolean enabled = false;

            /**
             * The name of the JGroups cluster to connect to. Defaults to "Axon".
             */
            private String clusterName = "Axon";

            /**
             * The JGroups configuration file to use. Defaults to a TCP Gossip based configuration
             */
            private String configurationFile = "default_tcp_gossip.xml";

            /**
             * The address of the network interface to bind JGroups to. Defaults to a global IP address of this node.
             */
            private String bindAddr = "GLOBAL";

            /**
             * Sets the initial port to bind the JGroups connection to. If this port is taken, JGroups will find the
             * next available port.
             */
            private String bindPort = "7800";

            /**
             * Sets the loadFactor for this node to join with. The loadFactor sets the relative load this node will
             * receive compared to other nodes in the cluster. Defaults to 100.
             */
            private int loadFactor = 100;

            public Gossip getGossip() {
                return gossip;
            }

            public void setGossip(Gossip gossip) {
                this.gossip = gossip;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getClusterName() {
                return clusterName;
            }

            public void setClusterName(String clusterName) {
                this.clusterName = clusterName;
            }

            public String getConfigurationFile() {
                return configurationFile;
            }

            public void setConfigurationFile(String configurationFile) {
                this.configurationFile = configurationFile;
            }

            public String getBindAddr() {
                return bindAddr;
            }

            public void setBindAddr(String bindAddr) {
                this.bindAddr = bindAddr;
            }

            public String getBindPort() {
                return bindPort;
            }

            public void setBindPort(String bindPort) {
                this.bindPort = bindPort;
            }

            public int getLoadFactor() {
                return loadFactor;
            }

            public void setLoadFactor(int loadFactor) {
                this.loadFactor = loadFactor;
            }

            public static class Gossip {

                /**
                 * Whether to automatically attempt to start a Gossip Routers. The host and port of the Gossip server
                 * are taken from the first define host in 'hosts'.
                 */
                private boolean autoStart = false;

                /**
                 * Defines the hosts of the Gossip Routers to connect to, in the form of host[port],...
                 * <p>
                 * If autoStart is set to {@code true}, the first host and port are used as bind address and bind port
                 * of the Gossip server to start.
                 * <p>
                 * Defaults to localhost[12001].
                 */
                private String hosts = "localhost[12001]";

                public boolean isAutoStart() {
                    return autoStart;
                }

                public void setAutoStart(boolean autoStart) {
                    this.autoStart = autoStart;
                }

                public String getHosts() {
                    return hosts;
                }

                public void setHosts(String hosts) {
                    this.hosts = hosts;
                }
            }
        }

    }

}
