/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.amqp.spring;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBusTerminal;
import org.axonframework.eventhandling.amqp.EventPublicationFailedException;
import org.axonframework.eventhandling.amqp.MetaDataPropertyQueueNameResolver;
import org.axonframework.eventhandling.amqp.PackageRougingKeyResolver;
import org.axonframework.eventhandling.amqp.QueueNameResolver;
import org.axonframework.eventhandling.amqp.RoutingKeyResolver;
import org.axonframework.io.EventMessageWriter;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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
    private static final String DEFAULT_QUEUE_NAME = DEFAULT_EXCHANGE_NAME + ".Default";
    private static final AMQP.BasicProperties DURABLE = new AMQP.BasicProperties.Builder().deliveryMode(2).build();

    private ConnectionFactory connectionFactory;
    private Serializer serializer;
    private String exchangeName = DEFAULT_EXCHANGE_NAME;
    private boolean isTransactional = false;
    private boolean isDurable = false;
    private ListenerContainerLifecycleManager listenerContainerLifecycleManager;
    private QueueNameResolver queueNameResolver = new MetaDataPropertyQueueNameResolver(DEFAULT_QUEUE_NAME);
    private RoutingKeyResolver routingKeyResolver = new PackageRougingKeyResolver();
    private ApplicationContext applicationContext;

    @Override
    public void publish(EventMessage... events) {
        Channel channel = connectionFactory.createConnection().createChannel(isTransactional);
        try {
            for (EventMessage event : events) {
                doSendMessage(channel,
                              routingKeyResolver.resolveRoutingKey(event),
                              asByteArray(event),
                              isDurable ? DURABLE : null);
            }
            if (isTransactional) {
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
            try {
                channel.close();
            } catch (IOException e) {
                logger.debug("Unable to close channel. It might already be closed.", e);
            }
        }
    }

    /**
     * Does the actual publishing of the given <code>body</code> on the given <code>channel</code>. This method can be
     * overridden to change the properties used to send a message.
     *
     * @param channel    The channel to dispatch the message on
     * @param routingKey The routing key for the message
     * @param body       The body of the message to dispatch
     * @param props      The default properties for the message   @throws IOException any exception that occurs while
     *                   dispatching the message
     * @throws java.io.IOException when an error occurs while writing the message
     */
    protected void doSendMessage(Channel channel, String routingKey, byte[] body, AMQP.BasicProperties props)
            throws IOException {
        channel.basicPublish(exchangeName, routingKey, true, false, props, body);
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
        String queueName = queueNameResolver.resolveQueueName(cluster);
        getListenerContainerLifecycleManager().registerCluster(queueName, cluster);
    }

    private byte[] asByteArray(EventMessage event) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EventMessageWriter outputStream = new EventMessageWriter(new DataOutputStream(baos), serializer);
            outputStream.writeEventMessage(event);
            return baos.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream doesn't throw IOException... anyway...
            throw new EventPublicationFailedException("Failed to serialize an EventMessage", e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (connectionFactory == null) {
            connectionFactory = applicationContext.getBean(ConnectionFactory.class);
        }
        if (serializer == null) {
            serializer = applicationContext.getBean(Serializer.class);
        }
    }

    private synchronized ListenerContainerLifecycleManager getListenerContainerLifecycleManager() {
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
     * @param transactional whteher dispatching should be transactional or not
     */
    public void setTransactional(boolean transactional) {
        isTransactional = transactional;
    }

    /**
     * Whether or not messages should be marked as "durable" when sending them out. Durable messages suffer from a
     * performance penalty, but will survive a reboot of the Message broker that stores them.
     *
     * @param durable whether or not messages should be durable
     */
    public void setDurable(boolean durable) {
        isDurable = durable;
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
     * Sets the serializer to serialize messages with when sending them to the Exchange.
     * <p/>
     * Defaults to an autowired serializer, which requires exactly 1 eligible serializer to be present in the
     * application context.
     *
     * @param serializer the serializer to serialize message with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
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
     * Sets the QueueNameResolver that provides the name of the Queue for each of the Clusters connected to this
     * terminal. Defaults to a {@link MetaDataPropertyQueueNameResolver}.
     *
     * @param queueNameResolver the Queue Name Resolver to set
     */
    public void setQueueNameResolver(QueueNameResolver queueNameResolver) {
        this.queueNameResolver = queueNameResolver;
    }

    /**
     * Sets the RoutingKeyResolver that provides the Routing Key for each message to dispatch. Defaults to a {@link
     * PackageRougingKeyResolver}, which uses the package name of the message's payload as a Routing Key.
     *
     * @param routingKeyResolver the RoutingKeyResolver to use
     */
    public void setRoutingKeyResolver(RoutingKeyResolver routingKeyResolver) {
        this.routingKeyResolver = routingKeyResolver;
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
}
