/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.amqp.eventhandling.spring;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import org.axonframework.amqp.eventhandling.*;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * EventBusTerminal implementation that uses an AMQP 0.9 compatible Message Broker to dispatch event messages. All
 * outgoing messages are sent to a configured Exchange, which defaults to "{@code Axon.EventBus}".
 * <p>
 * This terminal does not dispatch Events internally, as it relies on each event processor to listen to it's own AMQP Queue.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class SpringAMQPPublisher implements InitializingBean, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringAMQPPublisher.class);
    private static final String DEFAULT_EXCHANGE_NAME = "Axon.EventBus";

    private final SubscribableMessageSource<EventMessage<?>> messageSource;

    private ConnectionFactory connectionFactory;
    private String exchangeName = DEFAULT_EXCHANGE_NAME;
    private boolean isTransactional = false;
    private boolean isDurable = true;
    private AMQPMessageConverter messageConverter;
    private ApplicationContext applicationContext;
    private Serializer serializer;
    private RoutingKeyResolver routingKeyResolver;
    private boolean waitForAck;
    private long publisherAckTimeout;
    private Registration eventBusRegistration;

    /**
     * Initialize this instance to publish message as they are published on the given {@code messageSource}.
     *
     * @param messageSource The component providing messages to be publishes
     */
    public SpringAMQPPublisher(SubscribableMessageSource<EventMessage<?>> messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Subscribes this publisher to the messageSource provided during initialization.
     */
    public void start() {
        eventBusRegistration = messageSource.subscribe(this::send);
    }

    /**
     * Shuts down this component and unsubscribes it from its messageSource.
     */
    public void shutDown() {
        if (eventBusRegistration != null) {
            eventBusRegistration.cancel();
        }
    }

    /**
     * Sends the given {@code events} to the configured AMQP Exchange. It takes the current Unit of Work into account
     * when available. Otherwise, it simply publishes directly.
     *
     * @param events the events to publish on the AMQP Message Broker
     */
    protected void send(List<? extends EventMessage<?>> events) {
        Channel channel = connectionFactory.createConnection().createChannel(isTransactional);
        try {
            if (isTransactional) {
                channel.txSelect();
            } else if (waitForAck) {
                channel.confirmSelect();
            }
            for (EventMessage event : events) {
                AMQPMessage amqpMessage = messageConverter.createAMQPMessage(event);
                doSendMessage(channel, amqpMessage);
            }
            if (CurrentUnitOfWork.isStarted()) {
                UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
                unitOfWork.onCommit(u -> {
                    if ((isTransactional || waitForAck) && !channel.isOpen()) {
                        throw new EventPublicationFailedException(
                                "Unable to Commit UnitOfWork changes to AMQP: Channel is closed.",
                                channel.getCloseReason());
                    }
                });
                unitOfWork.afterCommit(u -> {
                    try {
                        if (isTransactional) {
                            channel.txCommit();
                        } else if (waitForAck) {
                            try {
                                channel.waitForConfirmsOrDie(publisherAckTimeout);
                            } catch (IOException ex) {
                                throw new EventPublicationFailedException(
                                        "Failed to receive acknowledgements for all events",
                                        ex);
                            } catch (TimeoutException ex) {
                                throw new EventPublicationFailedException(
                                        "Timeout while waiting for publisher acknowledgements",
                                        ex);
                            }
                        }
                    } catch (IOException e) {
                        logger.warn("Unable to commit transaction on channel.", e);
                    } catch (InterruptedException e) {
                        logger.warn("Interrupt received when waiting for message confirms.");
                        Thread.currentThread().interrupt();
                    }
                    tryClose(channel);
                });
                unitOfWork.onRollback(u -> {
                    try {
                        if (isTransactional) {
                            channel.txRollback();
                        }
                    } catch (IOException ex) {
                        logger.warn("Unable to rollback transaction on channel.", ex);
                    }
                    tryClose(channel);
                });
            } else if (isTransactional) {
                channel.txCommit();
            } else if (waitForAck) {
                channel.waitForConfirmsOrDie();
            }
        } catch (IOException e) {
            if (isTransactional) {
                tryRollback(channel);
            }
            throw new EventPublicationFailedException("Failed to dispatch Events to the Message Broker.", e);
        } catch (ShutdownSignalException e) {
            throw new EventPublicationFailedException("Failed to dispatch Events to the Message Broker.", e);
        } catch (InterruptedException e) {
            logger.warn("Interrupt received when waiting for message confirms.");
            Thread.currentThread().interrupt();
        } finally {
            if (!CurrentUnitOfWork.isStarted()) {
                tryClose(channel);
            }
        }
    }

    private void tryClose(Channel channel) {
        try {
            channel.close();
        } catch (IOException | TimeoutException e) {
            logger.info("Unable to close channel. It might already be closed.", e);
        }
    }

    /**
     * Does the actual publishing of the given {@code body} on the given {@code channel}. This method can be
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

    /**
     * Whether this Terminal should dispatch its Events in a transaction or not. Defaults to {@code false}.
     * <p>
     * If a delegate Terminal  is configured, the transaction will be committed <em>after</em> the delegate has
     * dispatched the events.
     * <p>
     * Transactional behavior cannot be enabled if {@link #setWaitForPublisherAck(boolean)} has been set to
     * {@code true}.
     *
     * @param transactional whether dispatching should be transactional or not
     */
    public void setTransactional(boolean transactional) {
        Assert.isTrue(!waitForAck || !transactional,
                      () -> "Cannot set transactional behavior when 'waitForServerAck' is enabled.");
        isTransactional = transactional;
    }

    /**
     * Enables or diables the RabbitMQ specific publisher acknowledgements (confirms). When confirms are enabled, the
     * terminal will wait until the server has acknowledged the reception (or fsync to disk on persistent messages) of
     * all published messages.
     * <p>
     * Server ACKS cannot be enabled when transactions are enabled.
     * <p>
     * See <a href="http://www.rabbitmq.com/confirms.html">RabbitMQ Documentation</a> for more information about
     * publisher acknowledgements.
     *
     * @param waitForPublisherAck whether or not to enab;e server acknowledgements (confirms)
     */
    public void setWaitForPublisherAck(boolean waitForPublisherAck) {
        Assert.isTrue(!waitForPublisherAck || !isTransactional,
                      () -> "Cannot set 'waitForPublisherAck' when using transactions.");
        this.waitForAck = waitForPublisherAck;
    }

    /**
     * Sets the maximum amount of time (in milliseconds) the publisher may wait for the acknowledgement of published
     * messages. If not all messages have been acknowledged withing this time, the publication will throw an
     * EventPublicationFailedException.
     * <p>
     * This setting is only used when {@link #setWaitForPublisherAck(boolean)} is set to {@code true}.
     *
     * @param publisherAckTimeout The number of milliseconds to wait for confirms, or 0 to wait indefinitely.
     */
    public void setPublisherAckTimeout(long publisherAckTimeout) {
        this.publisherAckTimeout = publisherAckTimeout;
    }

    /**
     * Sets the ConnectionFactory providing the Connections and Channels to send messages on. The SpringAMQPPublisher
     * does not cache or reuse connections. Providing a ConnectionFactory instance that caches connections will prevent
     * new connections to be opened for each invocation to {@link #send(List)}
     * <p>
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
     * <p>
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
     * <p>
     * By default, messages are durable.
     * <p>
     * Note that this setting is ignored if a {@link
     * #setMessageConverter(AMQPMessageConverter) MessageConverter} is provided.
     * In that case, the message converter must add the properties to reflect the required durability setting.
     *
     * @param durable whether or not messages should be durable
     */
    public void setDurable(boolean durable) {
        isDurable = durable;
    }

    /**
     * Sets the serializer to serialize messages with when sending them to the Exchange.
     * <p>
     * Defaults to an autowired serializer, which requires exactly 1 eligible serializer to be present in the
     * application context.
     * <p>
     * This setting is ignored if a {@link
     * #setMessageConverter(AMQPMessageConverter) MessageConverter} is configured.
     *
     * @param serializer the serializer to serialize message with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Sets the RoutingKeyResolver that provides the Routing Key for each message to dispatch. Defaults to a {@link
     * PackageRoutingKeyResolver}, which uses the package name of the message's
     * payload as a Routing Key.
     * <p>
     * This setting is ignored if a {@link
     * #setMessageConverter(AMQPMessageConverter) MessageConverter} is configured.
     *
     * @param routingKeyResolver the RoutingKeyResolver to use
     */
    public void setRoutingKeyResolver(RoutingKeyResolver routingKeyResolver) {
        this.routingKeyResolver = routingKeyResolver;
    }

    /**
     * Sets the name of the exchange to dispatch published messages to. Defaults to "{@code Axon.EventBus}".
     *
     * @param exchangeName the name of the exchange to dispatch messages to
     */
    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    /**
     * Sets the name of the exchange to dispatch published messages to. Defaults to the exchange named
     * "{@code Axon.EventBus}".
     *
     * @param exchange the exchange to dispatch messages to
     */
    public void setExchange(Exchange exchange) {
        this.exchangeName = exchange.getName();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
