/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Factory for SimpleMessageListenerContainer beans. All properties available on the SimpleMessageListenerContainer are
 * available on the factory. When {@link #createContainer(SpringAMQPConsumerConfiguration)} is invoked, a new instance
 * of {@link
 * SimpleMessageListenerContainer} is created and configured with the properties set on this factory. For more
 * information about the setters in this class and their default values, revert to the documentation of the {@link
 * SimpleMessageListenerContainer}
 * <p/>
 * This class is not thread-safe and is meant to be used in a Spring Application Context.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ListenerContainerFactory implements InitializingBean, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private ConnectionFactory connectionFactory;

    /**
     * Creates a new SimpleMessageListenerContainer, initialized with the properties set on this factory.
     *
     * @param config The container-specific configuration for the new container
     * @return a fully initialized (but not started!) SimpleMessageListenerContainer instance.
     */
    public SimpleMessageListenerContainer createContainer(SpringAMQPConsumerConfiguration config) {
        SimpleMessageListenerContainer newContainer = new SimpleMessageListenerContainer();
        newContainer.setConnectionFactory(connectionFactory);
        if (config.getTransactionManager() != null) {
            newContainer.setChannelTransacted(true);
            newContainer.setTransactionManager(config.getTransactionManager());
        }
        if (config.getErrorHandler() != null) {
            newContainer.setErrorHandler(config.getErrorHandler());
        }
        if (config.getPrefetchCount() != null) {
            newContainer.setPrefetchCount(config.getPrefetchCount());
        }
        if (config.getTxSize() != null) {
            newContainer.setTxSize(config.getTxSize());
        }
        if (config.getAdviceChain() != null) {
            newContainer.setAdviceChain(config.getAdviceChain());
        }
        if (config.getRecoveryInterval() != null) {
            newContainer.setRecoveryInterval(config.getRecoveryInterval());
        }
        if (config.getConcurrentConsumers() != null) {
            newContainer.setConcurrentConsumers(config.getConcurrentConsumers());
        }
        if (config.getReceiveTimeout() != null) {
            newContainer.setReceiveTimeout(config.getReceiveTimeout());
        }
        if (config.getShutdownTimeout() != null) {
            newContainer.setShutdownTimeout(config.getShutdownTimeout());
        }
        if (config.getTaskExecutor() != null) {
            newContainer.setTaskExecutor(config.getTaskExecutor());
        }
        if (config.getTransactionAttribute() != null) {
            newContainer.setTransactionAttribute(config.getTransactionAttribute());
        }
        if (config.getMessagePropertiesConverter() != null) {
            newContainer.setMessagePropertiesConverter(config.getMessagePropertiesConverter());
        }
        if (config.getAcknowledgeMode() != null) {
            newContainer.setAcknowledgeMode(config.getAcknowledgeMode());
        }
        if (config.getExclusive() != null) {
            newContainer.setExclusive(config.getExclusive());
        }
        newContainer.afterPropertiesSet();
        return newContainer;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Returns the application context in which this bean is defined.
     *
     * @return the application context in which this bean is defined.
     */
    protected ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (connectionFactory == null) {
            connectionFactory = applicationContext.getBean(ConnectionFactory.class);
        }
    }

    /**
     * Sets the connection factory to use for the cluster
     *
     * @param connectionFactory the connection factory to set
     * @see SimpleMessageListenerContainer#setConnectionFactory(org.springframework.amqp.rabbit.connection.ConnectionFactory)
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}