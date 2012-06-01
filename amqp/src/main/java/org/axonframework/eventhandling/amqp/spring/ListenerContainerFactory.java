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

import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.Executor;

import static java.util.Arrays.copyOf;

/**
 * Factory for SimpleMessageListenerContainer beans. All properties available on the SimpleMessageListenerContainer are
 * available on the factory. When {@link #createContainer()} is invoked, a new instance of {@link
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
    private PlatformTransactionManager transactionManager;
    private ErrorHandler errorHandler;
    private Integer txSize;
    private Integer prefetchCount;
    private Advice[] adviceChain;
    private Long recoveryInterval;
    private Integer concurrentConsumers;
    private Long receiveTimeout;
    private Long shutdownTimeout;
    private Executor taskExecutor;
    private TransactionAttribute transactionAttribute;
    private MessagePropertiesConverter messagePropertiesConverter;
    private AcknowledgeMode acknowledgeMode;

    /**
     * Creates a new SimpleMessageListenerContainer, initialized with the properties set on this factory.
     *
     * @return a fully initialized (but not started!) SimpleMessageListenerContainer instance.
     */
    public SimpleMessageListenerContainer createContainer() {
        SimpleMessageListenerContainer newContainer = new SimpleMessageListenerContainer();
        newContainer.setConnectionFactory(connectionFactory);
        if (transactionManager != null) {
            newContainer.setChannelTransacted(true);
            newContainer.setTransactionManager(transactionManager);
        }
        if (errorHandler != null) {
            newContainer.setErrorHandler(errorHandler);
        }
        if (prefetchCount != null) {
            newContainer.setPrefetchCount(prefetchCount);
        }
        if (txSize != null) {
            newContainer.setTxSize(txSize);
        }
        if (adviceChain != null) {
            newContainer.setAdviceChain(adviceChain);
        }
        if (recoveryInterval != null) {
            newContainer.setRecoveryInterval(recoveryInterval);
        }
        if (concurrentConsumers != null) {
            newContainer.setConcurrentConsumers(concurrentConsumers);
        }
        if (receiveTimeout != null) {
            newContainer.setReceiveTimeout(receiveTimeout);
        }
        if (shutdownTimeout != null) {
            newContainer.setShutdownTimeout(shutdownTimeout);
        }
        if (taskExecutor != null) {
            newContainer.setTaskExecutor(taskExecutor);
        }
        if (transactionAttribute != null) {
            newContainer.setTransactionAttribute(transactionAttribute);
        }
        if (messagePropertiesConverter != null) {
            newContainer.setMessagePropertiesConverter(messagePropertiesConverter);
        }
        if (acknowledgeMode != null) {
            newContainer.setAcknowledgeMode(acknowledgeMode);
        }
        newContainer.afterPropertiesSet();
        return newContainer;
    }

    /**
     * Sets the connection factory to define on each created Listener Container
     *
     * @param connectionFactory the connection factory to set
     * @see SimpleMessageListenerContainer#setConnectionFactory(org.springframework.amqp.rabbit.connection.ConnectionFactory)
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Sets the PlatformTransactionManager to define on each created Listener Container. Setting the transaction
     * manager will also mark used channels as transacted (see
     * {@link SimpleMessageListenerContainer#setChannelTransacted(boolean)}).
     *
     * @param transactionManager The transaction manager to set
     * @see SimpleMessageListenerContainer#setTransactionManager(org.springframework.transaction.PlatformTransactionManager)
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the Error Handler to define on each created Listener Container.
     *
     * @param errorHandler the Error Handler to set
     * @see SimpleMessageListenerContainer#setErrorHandler(org.springframework.util.ErrorHandler)
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Sets the Transaction Size to define on each created Listener Container.
     *
     * @param txSize the transaction size to set
     * @see SimpleMessageListenerContainer#setTxSize(int)
     */
    public void setTxSize(int txSize) {
        this.txSize = txSize;
    }

    /**
     * Sets the PrefetchCount to define on each created Listener Container.
     *
     * @param prefetchCount the prefetch count to set
     * @see SimpleMessageListenerContainer#setPrefetchCount(int)
     */
    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * Sets the Advice Chain to define on each created Listener Container.
     *
     * @param adviceChain the advice chain to set
     * @see SimpleMessageListenerContainer#setAdviceChain(org.aopalliance.aop.Advice[])
     */
    public void setAdviceChain(Advice[] adviceChain) {
        this.adviceChain = copyOf(adviceChain, adviceChain.length);
    }

    /**
     * Sets the Recovery Interval to define on each created Listener Container.
     *
     * @param recoveryInterval the recovery interval to set
     * @see SimpleMessageListenerContainer#setRecoveryInterval(long)
     */
    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    /**
     * Sets the number of concurrent consumers to define on each created Listener Container
     *
     * @param concurrentConsumers The number of concurrent consumers
     * @see SimpleMessageListenerContainer#setConcurrentConsumers(int)
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    /**
     * Sets the receive timeout to define on each created Listener Container
     *
     * @param receiveTimeout The receive timeout to set
     * @see SimpleMessageListenerContainer#setReceiveTimeout(long)
     */
    public void setReceiveTimeout(long receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    /**
     * Sets the shutdown timeout to define on each created Listener Container
     *
     * @param shutdownTimeout The shutdown timeout to set
     * @see SimpleMessageListenerContainer#setShutdownTimeout(long)
     */
    public void setShutdownTimeout(long shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Sets the task executor to define on each created Listener Container
     *
     * @param taskExecutor The task executor to set
     * @see SimpleMessageListenerContainer#setTaskExecutor(java.util.concurrent.Executor)
     */
    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Sets the transaction attribute to define on each created Listener Container
     *
     * @param transactionAttribute The transaction attribute to set
     * @see SimpleMessageListenerContainer#setTransactionAttribute(org.springframework.transaction.interceptor.TransactionAttribute)
     */
    public void setTransactionAttribute(TransactionAttribute transactionAttribute) {
        this.transactionAttribute = transactionAttribute;
    }

    /**
     * Sets the message properties converter to define on each created Listener Container
     *
     * @param messagePropertiesConverter The message properties converter to set
     * @see SimpleMessageListenerContainer#setMessagePropertiesConverter(org.springframework.amqp.rabbit.support.MessagePropertiesConverter)
     */
    public void setMessagePropertiesConverter(MessagePropertiesConverter messagePropertiesConverter) {
        this.messagePropertiesConverter = messagePropertiesConverter;
    }

    /**
     * Sets the acknowledge mode to define on each created Listener Container
     *
     * @param acknowledgeMode The acknowledge mode to set
     * @see SimpleMessageListenerContainer#setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode)
     */
    public void setAcknowledgeMode(AcknowledgeMode acknowledgeMode) {
        this.acknowledgeMode = acknowledgeMode;
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
}