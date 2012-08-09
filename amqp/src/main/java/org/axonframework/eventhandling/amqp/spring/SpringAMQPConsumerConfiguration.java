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

import org.aopalliance.aop.Advice;
import org.axonframework.eventhandling.amqp.AMQPConsumerConfiguration;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.Executor;

import static java.util.Arrays.copyOf;

/**
 * AMQPConsumerConfiguration implementation that has additional support for all Spring-specific AMQP Configuration
 * properties. This bean allows configuration using setters, to make it easier to configure inside an application
 * context configuration file.
 * <p/>
 * Note that this class is not thread-safe and should not be used outside of the Spring Application context.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SpringAMQPConsumerConfiguration implements AMQPConsumerConfiguration {

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
    private Boolean exclusive;
    private String queueName;

    private SpringAMQPConsumerConfiguration defaults;

    /**
     * Creates a SpringAMQPConsumerConfiguration instance from the given <code>configuration</code>. If tha
     * configuration is already an instance of SpringAMQPConsumerConfiguration, it is returned as is. Otherwise, the
     * properties in the configuration are copied into a fresh instance of SpringAMQPConsumerConfiguration.
     *
     * @param configuration The configuration to wrap in a SpringAMQPConsumerConfiguration
     * @return a SpringAMQPConsumerConfiguration reflecting given <code>configuration</code>
     */
    public static SpringAMQPConsumerConfiguration wrap(AMQPConsumerConfiguration configuration) {
        if (configuration instanceof SpringAMQPConsumerConfiguration) {
            return (SpringAMQPConsumerConfiguration) configuration;
        } else {
            final SpringAMQPConsumerConfiguration springConfig = new SpringAMQPConsumerConfiguration();
            springConfig.setQueueName(configuration.getQueueName());
            springConfig.setExclusive(configuration.getExclusive());
            springConfig.setPrefetchCount(configuration.getPrefetchCount());
            return springConfig;
        }
    }

    /**
     * Sets the defaults to use when this instance does not explicitly provide a configuration value. If defaults were
     * already provided, they are all overwritten by the given instance, even if the new instance returns
     * <code>null</code> for some configuration options that the previous defaults <em>did</em> provide a value for.
     *
     * @param defaults The defaults to use
     */
    public void setDefaults(SpringAMQPConsumerConfiguration defaults) {
        this.defaults = defaults;
    }

    @Override
    public String getQueueName() {
        return queueName != null ? queueName : defaults == null ? null : defaults.getQueueName();
    }

    /**
     * Returns the TransactionManager configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the TransactionManager configured in this instance, or by the default configuration
     */
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager != null ? transactionManager
                : defaults == null ? null : defaults.getTransactionManager();
    }

    /**
     * Returns the ErrorHandler configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the ErrorHandler configured in this instance, or by the default configuration
     */
    public ErrorHandler getErrorHandler() {
        return errorHandler != null ? errorHandler : defaults == null ? null : defaults.getErrorHandler();
    }

    /**
     * Returns the Transaction Size configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Transaction Size configured in this instance, or by the default configuration
     */
    public Integer getTxSize() {
        return txSize != null ? txSize : defaults == null ? null : defaults.getTxSize();
    }

    /**
     * Returns the Prefetch Count configured in this instance, or the one provided by the {@link
     * #setDefaults(org.axonframework.eventhandling.amqp.spring.SpringAMQPConsumerConfiguration)
     * default configuration} if not explicitly provided.
     *
     * @return the Prefetch Count configured in this instance, or by the default configuration
     */
    @Override
    public Integer getPrefetchCount() {
        return prefetchCount != null ? prefetchCount : defaults == null ? null : defaults.getPrefetchCount();
    }

    /**
     * Returns the Advice Chain configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Advice Chain configured in this instance, or by the default configuration
     */
    public Advice[] getAdviceChain() {
        return adviceChain != null ? adviceChain : defaults == null ? null : defaults.getAdviceChain();
    }

    /**
     * Returns the Recovery Interval configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Recovery Interval configured in this instance, or by the default configuration
     */
    public Long getRecoveryInterval() {
        return recoveryInterval != null ? recoveryInterval : defaults == null ? null : defaults.getRecoveryInterval();
    }

    /**
     * Returns the Concurrent Consumers configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Concurrent Consumers configured in this instance, or by the default configuration
     */
    public Integer getConcurrentConsumers() {
        return concurrentConsumers != null ? concurrentConsumers
                : defaults == null ? null : defaults.getConcurrentConsumers();
    }

    /**
     * Returns the Receive Timeout configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Receive Timeout configured in this instance, or by the default configuration
     */
    public Long getReceiveTimeout() {
        return receiveTimeout != null ? receiveTimeout : defaults == null ? null : defaults.getReceiveTimeout();
    }

    /**
     * Returns the Shutdown Timeout configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Shutdown Timeout configured in this instance, or by the default configuration
     */
    public Long getShutdownTimeout() {
        return shutdownTimeout != null ? shutdownTimeout : defaults == null ? null : defaults.getShutdownTimeout();
    }

    /**
     * Returns the Task Executor configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the Task Executor configured in this instance, or by the default configuration
     */
    public Executor getTaskExecutor() {
        return taskExecutor != null ? taskExecutor : defaults == null ? null : defaults.getTaskExecutor();
    }

    /**
     * Returns the TransactionAttribute configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the TransactionAttribute configured in this instance, or by the default configuration
     */
    public TransactionAttribute getTransactionAttribute() {
        return transactionAttribute != null ? transactionAttribute
                : defaults == null ? null : defaults.getTransactionAttribute();
    }

    /**
     * Returns the MessagePropertiesConverter configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the MessagePropertiesConverter configured in this instance, or by the default configuration
     */
    public MessagePropertiesConverter getMessagePropertiesConverter() {
        return messagePropertiesConverter != null ? messagePropertiesConverter
                : defaults == null ? null : defaults.getMessagePropertiesConverter();
    }

    /**
     * Returns the AcknowledgeMode configured in this instance, or the one provided by the {@link
     * #setDefaults(SpringAMQPConsumerConfiguration) default configuration} if not explicitly provided.
     *
     * @return the AcknowledgeMode configured in this instance, or by the default configuration
     */
    public AcknowledgeMode getAcknowledgeMode() {
        return acknowledgeMode != null ? acknowledgeMode : defaults == null ? null : defaults.getAcknowledgeMode();
    }

    @Override
    public Boolean getExclusive() {
        return exclusive != null ? exclusive : defaults == null ? null : defaults.getExclusive();
    }

    /**
     * Sets the name of the Queue that a Cluster should be connected to. If it is <code>null</code>, a Queue Name is
     * expected to be provided by the default configuration.
     *
     * @param queueName The queue name to connect the Cluster to
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * Sets the PlatformTransactionManager to use for the cluster. Setting the transaction manager will also mark
     * used channels as transacted (see
     * {@link org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setChannelTransacted(boolean)
     * setChannelTransacted()}).
     *
     * @param transactionManager The transaction manager to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setTransactionManager(org.springframework.transaction.PlatformTransactionManager)
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the Error Handler to use for the cluster.
     *
     * @param errorHandler the Error Handler to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setErrorHandler(org.springframework.util.ErrorHandler)
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Sets the Transaction Size to use for the cluster.
     *
     * @param txSize the transaction size to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setTxSize(int)
     */
    public void setTxSize(Integer txSize) {
        this.txSize = txSize;
    }

    /**
     * Sets the PrefetchCount to use for the cluster.
     *
     * @param prefetchCount the prefetch count to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setPrefetchCount(int)
     */
    public void setPrefetchCount(Integer prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * Sets the Advice Chain to use for the cluster.
     *
     * @param adviceChain the advice chain to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setAdviceChain(org.aopalliance.aop.Advice[])
     */
    public void setAdviceChain(Advice[] adviceChain) {
        this.adviceChain = copyOf(adviceChain, adviceChain.length);
    }

    /**
     * Sets the Recovery Interval to use for the cluster.
     *
     * @param recoveryInterval the recovery interval to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setRecoveryInterval(long)
     */
    public void setRecoveryInterval(Long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    /**
     * Sets the number of concurrent consumers to use for the cluster
     *
     * @param concurrentConsumers The number of concurrent consumers
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setConcurrentConsumers(int)
     */
    public void setConcurrentConsumers(Integer concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    /**
     * Sets the receive timeout to use for the cluster
     *
     * @param receiveTimeout The receive timeout to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setReceiveTimeout(long)
     */
    public void setReceiveTimeout(Long receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    /**
     * Sets the shutdown timeout to use for the cluster
     *
     * @param shutdownTimeout The shutdown timeout to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setShutdownTimeout(long)
     */
    public void setShutdownTimeout(Long shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Sets the task executor to use for the cluster
     *
     * @param taskExecutor The task executor to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setTaskExecutor(java.util.concurrent.Executor)
     */
    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Sets the transaction attribute to use for the cluster
     *
     * @param transactionAttribute The transaction attribute to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setTransactionAttribute(org.springframework.transaction.interceptor.TransactionAttribute)
     */
    public void setTransactionAttribute(TransactionAttribute transactionAttribute) {
        this.transactionAttribute = transactionAttribute;
    }

    /**
     * Sets the message properties converter to use for the cluster
     *
     * @param messagePropertiesConverter The message properties converter to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setMessagePropertiesConverter(org.springframework.amqp.rabbit.support.MessagePropertiesConverter)
     */
    public void setMessagePropertiesConverter(MessagePropertiesConverter messagePropertiesConverter) {
        this.messagePropertiesConverter = messagePropertiesConverter;
    }

    /**
     * Sets the acknowledge mode to use for the cluster
     *
     * @param acknowledgeMode The acknowledge mode to set
     * @see org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer#setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode)
     */
    public void setAcknowledgeMode(AcknowledgeMode acknowledgeMode) {
        this.acknowledgeMode = acknowledgeMode;
    }

    /**
     * Sets whether the listener container created by this factory should be exclusive. That means it will not allow
     * other listeners to connect to the same queue. If a non-exclusive listener is already connected to the queue,
     * this listener is rejected.
     * <p/>
     * By default, listeners are exclusive.
     *
     * @param exclusive Whether the created container should be an exclusive listener
     */
    public void setExclusive(Boolean exclusive) {
        this.exclusive = exclusive;
    }
}
