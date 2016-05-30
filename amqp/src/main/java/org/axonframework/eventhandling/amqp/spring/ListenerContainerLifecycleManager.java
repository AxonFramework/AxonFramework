/*
 * Copyright (c) 2010-2015. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.amqp.AMQPConsumerConfiguration;
import org.axonframework.eventhandling.amqp.AMQPMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of the SimpleMessageListenerContainers that have been created to receive messages for Event
 * Processors. The ListenerContainerLifecycleManager starts each of the Listener Containers when the context is started
 * and will stop each of them when the context is being shut down.
 * <p/>
 * This class must be defined as a top-level Spring bean.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ListenerContainerLifecycleManager extends ListenerContainerFactory implements SmartLifecycle, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(ListenerContainerLifecycleManager.class);

    // guarded by "this"
    private final Map<String, SimpleMessageListenerContainer> containerPerQueue = new HashMap<>();
    private final ConcurrentMap<Consumer<List<? extends EventMessage<?>>>, SimpleMessageListenerContainer>
            containerPerProcessor = new ConcurrentHashMap<>();
    // guarded by "this"
    private boolean started = false;
    private SpringAMQPConsumerConfiguration defaultConfiguration;
    private int phase = Integer.MAX_VALUE;

    /**
     * Registers the given <code>eventProcessor</code>, assigning it to a listener that listens to the given
     * <code>queueName</code>. If no listener is present for the given <code>queueName</code>, it is created. If one
     * already exists, it is assigned to the existing listener. Event processors that have been registered with the same
     * <code>queueName</code> will each receive a copy of all message on that queue
     *
     * @param eventProcessor   The event processor to forward messages to
     * @param config           The configuration object for the event processor
     * @param messageConverter The message converter to use to convert the AMQP Message to an Event Message
     * @return a handle to unsubscribe the <code>eventProcessor</code>. When unsubscribed it will no longer receive
     * messages.
     */
    public synchronized Registration registerEventProcessor(Consumer<List<? extends EventMessage<?>>> eventProcessor,
                                                            AMQPConsumerConfiguration config,
                                                            AMQPMessageConverter messageConverter) {
        SpringAMQPConsumerConfiguration amqpConfig = SpringAMQPConsumerConfiguration.wrap(config);
        amqpConfig.setDefaults(defaultConfiguration);
        String queueName = amqpConfig.getQueueName();
        if (queueName == null) {
            throw new AxonConfigurationException("The EventProcessor does not define a Queue Name, " +
                                                         "nor is there a default Queue Name configured in the " +
                                                         "ListenerContainerLifeCycleManager");
        }
        Registration registration;
        if (containerPerQueue.containsKey(queueName)) {
            final SimpleMessageListenerContainer container = containerPerQueue.get(queueName);
            EventProcessorMessageListener existingListener =
                    (EventProcessorMessageListener) container.getMessageListener();
            registration = existingListener.addEventProcessor(eventProcessor);
            containerPerProcessor.put(eventProcessor, container);
            if (started && logger.isWarnEnabled()) {
                logger.warn("An EventProcessor was configured on queue [{}], " +
                                    "while the Container for that queue was already processing events. " +
                                    "This may lead to Events not being published to all EventProcessors", queueName);
            }
        } else {
            SimpleMessageListenerContainer newContainer = createContainer(amqpConfig);
            newContainer.setQueueNames(queueName);
            EventProcessorMessageListener newListener = new EventProcessorMessageListener(messageConverter);
            registration = newListener.addEventProcessor(eventProcessor);
            newContainer.setMessageListener(newListener);
            containerPerQueue.put(queueName, newContainer);
            containerPerProcessor.put(eventProcessor, newContainer);
            if (started) {
                newContainer.start();
            }
        }
        return () -> {
            if (registration.cancel()) {
                SimpleMessageListenerContainer container = containerPerProcessor.get(eventProcessor);
                final EventProcessorMessageListener listener =
                        (EventProcessorMessageListener) container.getMessageListener();
                if (listener.isEmpty()) {
                    container.stop();
                }
                return true;
            }
            return false;
        };
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public synchronized void start() {
        for (SimpleMessageListenerContainer container : containerPerQueue.values()) {
            if (!container.isRunning()) {
                container.start();
            }
        }
        started = true;
    }

    @Override
    public synchronized void stop() {
        for (SimpleMessageListenerContainer container : containerPerQueue.values()) {
            if (container.isRunning()) {
                container.stop();
            }
        }
        started = false;
    }

    @Override
    public synchronized boolean isRunning() {
        for (SimpleMessageListenerContainer container : containerPerQueue.values()) {
            if (container.isRunning()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void destroy() throws Exception {
        for (SimpleMessageListenerContainer container : containerPerQueue.values()) {
            container.destroy();
        }
    }

    @Override
    public int getPhase() {
        return phase;
    }

    /**
     * Defines the phase in which Spring should manage this beans lifecycle. Defaults to <code>Integer.MAX_VALUE</code>
     * ({@value Integer#MAX_VALUE}), which ensures the containers are started when the rest of the application context
     * has started, and are the first to shut down.
     *
     * @param phase The phase for the lifecycle
     * @see org.springframework.context.SmartLifecycle
     */
    public void setPhase(int phase) {
        this.phase = phase;
    }

    /**
     * Sets the configuration with the entries to use as defaults in case a registered event processor does not provide
     * explicit values.
     *
     * @param defaultConfiguration The configuration instance containing defaults for each registered event processor
     */
    public synchronized void setDefaultConfiguration(SpringAMQPConsumerConfiguration defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
    }
}
