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

import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.amqp.AMQPMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, SimpleMessageListenerContainer> containerPerQueue = new HashMap<String, SimpleMessageListenerContainer>();
    // guarded by "this"
    private boolean started = false;

    private int phase = Integer.MAX_VALUE;

    /**
     * Registers the given <code>cluster</code>, assigning it to a listener that listens to the given
     * <code>queueName</code>. If no listener is present for the given <code>queueName</code>, it is created. If one
     * already exists, it is assigned to the existing listener. Clusters that have been registered with the same
     * <code>queueName</code> will each receive a copy of all message on that queue
     *
     * @param queueName        The name of the queue the cluster should receive messages from
     * @param cluster          The cluster to forward messages to
     * @param messageConverter The message converter to use to convert the AMQP Message to an Event Message
     */
    public synchronized void registerCluster(String queueName, Cluster cluster, AMQPMessageConverter messageConverter) {
        if (containerPerQueue.containsKey(queueName)) {
            ClusterMessageListener existingListener = (ClusterMessageListener) containerPerQueue.get(queueName)
                                                                                                .getMessageListener();
            existingListener.addCluster(cluster);
            if (started && logger.isWarnEnabled()) {
                logger.warn("A cluster was configured on queue [{}], "
                                    + "while the Container for that queue was already processing events."
                                    + "This may lead to Events not being published to all Clusters");
            }
        } else {
            SimpleMessageListenerContainer newContainer = createContainer();
            newContainer.setQueueNames(queueName);
            newContainer.setMessageListener(new ClusterMessageListener(cluster, messageConverter));
            containerPerQueue.put(queueName, newContainer);
            if (started) {
                newContainer.start();
            }
        }
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public synchronized void stop(Runnable callback) {
        for (SimpleMessageListenerContainer container : containerPerQueue.values()) {
            container.stop();
        }
        started = false;
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
}
