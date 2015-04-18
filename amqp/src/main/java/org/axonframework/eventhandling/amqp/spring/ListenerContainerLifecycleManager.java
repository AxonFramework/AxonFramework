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
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.amqp.AMQPConsumerConfiguration;
import org.axonframework.eventhandling.amqp.AMQPMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages the lifecycle of the SimpleMessageListenerContainers that have been created to receive messages for
 * Clusters. The ListenerContainerLifecycleManager starts each of the Listener Containers when the context is started
 * and will stop each of them when the context is being shut down.
 * <p/>
 * This class must be defined as a top-level Spring bean.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ListenerContainerLifecycleManager extends ListenerContainerFactory
        implements SmartLifecycle, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(ListenerContainerLifecycleManager.class);

    // guarded by "this"
    private final Map<String, SimpleMessageListenerContainer> containerPerQueue = new HashMap<>();
    private final ConcurrentMap<Cluster, SimpleMessageListenerContainer> containerPerCluster = new ConcurrentHashMap<>();
    // guarded by "this"
    private boolean started = false;
    private SpringAMQPConsumerConfiguration defaultConfiguration;
    private int phase = Integer.MAX_VALUE;

    /**
     * Registers the given <code>cluster</code>, assigning it to a listener that listens to the given
     * <code>queueName</code>. If no listener is present for the given <code>queueName</code>, it is created. If one
     * already exists, it is assigned to the existing listener. Clusters that have been registered with the same
     * <code>queueName</code> will each receive a copy of all message on that queue
     *
     * @param cluster          The cluster to forward messages to
     * @param config           The configuration object for the cluster
     * @param messageConverter The message converter to use to convert the AMQP Message to an Event Message
     */
    public synchronized void registerCluster(Cluster cluster, AMQPConsumerConfiguration config,
                                             AMQPMessageConverter messageConverter) {
        SpringAMQPConsumerConfiguration amqpConfig = SpringAMQPConsumerConfiguration.wrap(config);
        amqpConfig.setDefaults(defaultConfiguration);
        String queueName = amqpConfig.getQueueName();
        if (queueName == null) {
            throw new AxonConfigurationException("The Cluster does not define a Queue Name, "
                                                         + "nor is there a default Queue Name configured in the "
                                                         + "ListenerContainerLifeCycleManager");
        }
        if (containerPerQueue.containsKey(queueName)) {
            final SimpleMessageListenerContainer container = containerPerQueue.get(queueName);
            ClusterMessageListener existingListener = (ClusterMessageListener) container.getMessageListener();
            existingListener.addCluster(cluster);
            containerPerCluster.put(cluster, container);
            if (started && logger.isWarnEnabled()) {
                logger.warn("A cluster was configured on queue [{}], "
                                    + "while the Container for that queue was already processing events. "
                                    + "This may lead to Events not being published to all Clusters",
                            queueName);
            }
        } else {
            SimpleMessageListenerContainer newContainer = createContainer(amqpConfig);
            newContainer.setQueueNames(queueName);
            newContainer.setMessageListener(new ClusterMessageListener(cluster, messageConverter));
            containerPerQueue.put(queueName, newContainer);
            containerPerCluster.put(cluster, newContainer);
            if (started) {
                newContainer.start();
            }
        }
    }

    public void unregisterCluster(Cluster cluster) {
        SimpleMessageListenerContainer container = containerPerCluster.get(cluster);
        final ClusterMessageListener listener = (ClusterMessageListener) container.getMessageListener();
        listener.removeCluster(cluster);
        if (listener.isEmpty()) {
            container.stop();
        }
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
     * Sets the configuration with the entries to use as defaults in case a registered cluster does not provide
     * explicit values.
     *
     * @param defaultConfiguration The configuration instance containing defaults for each registered cluster
     */
    public synchronized void setDefaultConfiguration(SpringAMQPConsumerConfiguration defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
    }
}
