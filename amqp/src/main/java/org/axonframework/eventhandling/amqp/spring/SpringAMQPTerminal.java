/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.amqp.spring;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusterMetaData;
import org.axonframework.eventhandling.EventBusTerminal;
import org.axonframework.eventhandling.amqp.AMQPConsumerConfiguration;
import org.axonframework.eventhandling.amqp.AMQPMessage;
import org.axonframework.eventhandling.amqp.AMQPMessageConverter;
import org.axonframework.eventhandling.amqp.DefaultAMQPConsumerConfiguration;
import org.axonframework.eventhandling.amqp.DefaultAMQPMessageConverter;
import org.axonframework.eventhandling.amqp.EventPublicationFailedException;
import org.axonframework.eventhandling.amqp.PackageRoutingKeyResolver;
import org.axonframework.eventhandling.amqp.RoutingKeyResolver;
import org.axonframework.serializer.Serializer;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.util.Map;

import static org.axonframework.eventhandling.amqp.AMQPConsumerConfiguration.AMQP_CONFIG_PROPERTY;

/**
 * EventBusTerminal implementation that uses an AMQP 0.9 compatible Message Broker to dispatch event messages. All
 * outgoing messages are sent to a configured Exchange, which defaults to {@value #DEFAULT_EXCHANGE_NAME}.
 * <p/>
 * This terminal does not dispatch Events internally, as it relies on each cluster to listen to it's own AMQP Queue.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SpringAMQPTerminal implements EventBusTerminal, InitializingBean, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringAMQPTerminal.class);
    private static final String DEFAULT_EXCHANGE_NAME = "Axon.EventBus";

    private ConnectionFactory connectionFactory;
    private String exchangeName = DEFAULT_EXCHANGE_NAME;
    private boolean isTransactional = false;
    private boolean isDurable = true;
    private ListenerContainerLifecycleManager listenerContainerLifecycleManager;
    private AMQPMessageConverter messageConverter;
    private ApplicationContext applicationContext;
    private Serializer serializer;
    private RoutingKeyResolver routingKeyResolver;

    @Override
    public void publish(EventMessage... events) {
        final Channel channel = connectionFactory.createConnection().createChannel(isTransactional);
        try {
            for (EventMessage event : events) {
                AMQPMessage amqpMessage = messageConverter.createAMQPMessage(event);
                doSendMessage(channel, amqpMessage);
            }
            if (CurrentUnitOfWork.isStarted()) {
                CurrentUnitOfWork.get().registerListener(new ChannelTransactionUnitOfWorkListener(channel));
            } else if (isTransactional) {
                channel.txCommit();
            }
        } catch (IOException e) {
            if (isTransactional) {
                tryRollback(channel);
            }
            throw new EventPublicationFailedException("Failed to dispatch Events to the Message Broker.", e);
        } catch (ShutdownSignalException e) {
            throw new EventPublicationFailedException("Failed to dispatch Events to the Message Broker.", e);
        } finally {
            if (!CurrentUnitOfWork.isStarted()) {
                tryClose(channel);
            }
        }
    }

    private void tryClose(Channel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            logger.info("Unable to close channel. It might already be closed.", e);
        }
    }

    /**
     * Does the actual publishing of the given <code>body</code> on the given <code>channel</code>. This method can be
     * overridden to change the properties used to send a message.
     *
     * @param channel     The channel to dispatch the message on
     * @param amqpMessage The AMQPMessage describing the characteristics of the message to publish
     * @throws java.io.IOException when an error occurs while writing the message
     */
    protected void doSendMessage(Channel channel, AMQPMessage amqpMessage)
            throws IOException {
        channel.basicPublish(exchangeName, amqpMessage.getRoutingKey(), amqpMessage.isMandatory(),
                             amqpMessage.isImmediate(), amqpMessage.getProperties(), amqpMessage.getBody());
    }

    private void tryRollback(Channel channel) {
        try {
            channel.txRollback();
        } catch (IOException e) {
            logger.debug("Unable to rollback. The underlying channel might already be closed.", e);
        }
    }

    @Override
    public void onClusterCreated(final Cluster cluster) {
        ClusterMetaData clusterMetaData = cluster.getMetaData();
        AMQPConsumerConfiguration config;
        if (clusterMetaData.getProperty(AMQP_CONFIG_PROPERTY) instanceof AMQPConsumerConfiguration) {
            config = (AMQPConsumerConfiguration) clusterMetaData.getProperty(AMQP_CONFIG_PROPERTY);
        } else {
            config = new DefaultAMQPConsumerConfiguration(cluster.getName());
        }
        getListenerContainerLifecycleManager().registerCluster(cluster, config, messageConverter);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (connectionFactory == null) {
            connectionFactory = applicationContext.getBean(ConnectionFactory.class);
        }
        if (messageConverter == null) {
            if (serializer == null) {
                serializer = applicationContext.getBean(Serializer.class);
            }
            if (routingKeyResolver == null) {
                Map<String, RoutingKeyResolver> routingKeyResolverCandidates = applicationContext.getBeansOfType(
                        RoutingKeyResolver.class);
                if (routingKeyResolverCandidates.size() > 1) {
                    throw new AxonConfigurationException("No MessageConverter was configured, but none can be created "
                                                                 + "using autowired properties, as more than 1 "
                                                                 + "RoutingKeyResolver is present in the "
                                                                 + "ApplicationContent");
                } else if (routingKeyResolverCandidates.size() == 1) {
                    routingKeyResolver = routingKeyResolverCandidates.values().iterator().next();
                } else {
                    routingKeyResolver = new PackageRoutingKeyResolver();
                }
            }
            messageConverter = new DefaultAMQPMessageConverter(serializer, routingKeyResolver, isDurable);
        }
    }

    private ListenerContainerLifecycleManager getListenerContainerLifecycleManager() {
        if (listenerContainerLifecycleManager == null) {
            listenerContainerLifecycleManager = applicationContext.getBean(ListenerContainerLifecycleManager.class);
        }
        return listenerContainerLifecycleManager;
    }

    /**
     * Whether this Terminal should dispatch its Events in a transaction or not. Defaults to <code>false</code>.
     * <p/>
     * If a delegate Terminal  is configured, the transaction will be committed <em>after</em> the delegate has
     * dispatched the events.
     *
     * @param transactional whether dispatching should be transactional or not
     */
    public void setTransactional(boolean transactional) {
        isTransactional = transactional;
    }

    /**
     * Sets the ConnectionFactory providing the Connections and Channels to send messages on. The SpringAMQPTerminal
     * does not cache or reuse connections. Providing a ConnectionFactory instance that caches connections will prevent
     * new connections to be opened for each invocation to {@link #publish(org.axonframework.domain.EventMessage[])}
     * <p/>
     * Defaults to an autowired Connection Factory.
     *
     * @param connectionFactory The connection factory to set
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Sets the Message Converter that creates AMQP Messages from Event Messages and vice versa. Setting this property
     * will ignore the "durable", "serializer" and "routingKeyResolver" properties, which just act as short hands to
     * create a DefaultAMQPMessageConverter instance.
     * <p/>
     * Defaults to a DefaultAMQPMessageConverter.
     *
     * @param messageConverter The message converter to convert AMQP Messages to Event Messages and vice versa.
     */
    public void setMessageConverter(AMQPMessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    /**
     * Whether or not messages should be marked as "durable" when sending them out. Durable messages suffer from a
     * performance penalty, but will survive a reboot of the Message broker that stores them.
     * <p/>
     * By default, messages are durable.
     * <p/>
     * Note that this setting is ignored if a {@link
     * #setMessageConverter(org.axonframework.eventhandling.amqp.AMQPMessageConverter) MessageConverter} is provided.
     * In that case, the message converter must add the properties to reflect the required durability setting.
     *
     * @param durable whether or not messages should be durable
     */
    public void setDurable(boolean durable) {
        isDurable = durable;
    }

    /**
     * Sets the serializer to serialize messages with when sending them to the Exchange.
     * <p/>
     * Defaults to an autowired serializer, which requires exactly 1 eligible serializer to be present in the
     * application context.
     * <p/>
     * This setting is ignored if a {@link
     * #setMessageConverter(org.axonframework.eventhandling.amqp.AMQPMessageConverter) MessageConverter} is configured.
     *
     * @param serializer the serializer to serialize message with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Sets the RoutingKeyResolver that provides the Routing Key for each message to dispatch. Defaults to a {@link
     * org.axonframework.eventhandling.amqp.PackageRoutingKeyResolver}, which uses the package name of the message's
     * payload as a Routing Key.
     * <p/>
     * This setting is ignored if a {@link
     * #setMessageConverter(org.axonframework.eventhandling.amqp.AMQPMessageConverter) MessageConverter} is configured.
     *
     * @param routingKeyResolver the RoutingKeyResolver to use
     */
    public void setRoutingKeyResolver(RoutingKeyResolver routingKeyResolver) {
        this.routingKeyResolver = routingKeyResolver;
    }

    /**
     * Sets the name of the exchange to dispatch published messages to. Defaults to "{@value #DEFAULT_EXCHANGE_NAME}".
     *
     * @param exchangeName the name of the exchange to dispatch messages to
     */
    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     * Sets the name of the exchange to dispatch published messages to. Defaults to the exchange named "{@value
     * #DEFAULT_EXCHANGE_NAME}".
     *
     * @param exchange the exchange to dispatch messages to
     */
    public void setExchange(Exchange exchange) {
        this.exchangeName = exchange.getName();
    }

    /**
     * Sets the ListenerContainerLifecycleManager that creates and manages the lifecycle of Listener Containers for the
     * clusters that are connected to this terminal.
     * <p/>
     * Defaults to an autowired ListenerContainerLifecycleManager
     *
     * @param listenerContainerLifecycleManager
     *         the listenerContainerLifecycleManager to set
     */
    public void setListenerContainerLifecycleManager(
            ListenerContainerLifecycleManager listenerContainerLifecycleManager) {
        this.listenerContainerLifecycleManager = listenerContainerLifecycleManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private class ChannelTransactionUnitOfWorkListener extends UnitOfWorkListenerAdapter {

        private boolean isOpen;
        private final Channel channel;

        public ChannelTransactionUnitOfWorkListener(Channel channel) {
            this.channel = channel;
            isOpen = true;
        }

        @Override
        public void onPrepareTransactionCommit(UnitOfWork unitOfWork, Object transaction) {
            if (isTransactional && isOpen && !channel.isOpen()) {
                throw new EventPublicationFailedException("Unable to Commit transaction to AMQP: Channel is closed.",
                                                          channel.getCloseReason());
            }
        }

        @Override
        public void afterCommit(UnitOfWork unitOfWork) {
            if (isOpen) {
                try {
                    if (isTransactional) {
                        channel.txCommit();
                    }
                } catch (IOException e) {
                    logger.warn("Unable to commit transaction on channel.", e);
                }
                tryClose(channel);
            }
        }

        @Override
        public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
            try {
                channel.txRollback();
            } catch (IOException e) {
                logger.warn("Unable to rollback transaction on channel.", e);
            }
            tryClose(channel);
            isOpen = false;
        }
    }
}
