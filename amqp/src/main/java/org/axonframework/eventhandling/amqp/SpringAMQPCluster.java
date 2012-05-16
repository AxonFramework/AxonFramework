package org.axonframework.eventhandling.amqp;

import org.aopalliance.aop.Advice;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusterMetaData;
import org.axonframework.eventhandling.DefaultClusterMetaData;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.io.EventMessageReader;
import org.axonframework.serializer.Serializer;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.ErrorHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/**
 * Cluster implementation that ignores internally dispatched events. This cluster implements {@link MessageListener},
 * which allows it to be connected with Spring's {@link
 * org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer}, for instance. The latter provides the
 * connectivity and resilience required to reliably listen to events.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SpringAMQPCluster implements Cluster, MessageListener,
        ApplicationContextAware, InitializingBean, BeanNameAware, DisposableBean, SmartLifecycle {

    private final Set<EventListener> eventListeners = new CopyOnWriteArraySet<EventListener>();
    private final Set<EventListener> immutableEventListeners = Collections.unmodifiableSet(eventListeners);
    private final ClusterMetaData clusterMetaData = new DefaultClusterMetaData();
    private final SimpleMessageListenerContainer messageListenerContainer = new SimpleMessageListenerContainer();
    private Serializer serializer;
    private ApplicationContext applicationContext;

    /**
     * This method does nothing. Internally dispatched messages are ignored.
     *
     * @param events the events to publish - ignored in this implementation
     */
    @Override
    public void publish(EventMessage... events) {
        // internally dispatched events are ignored
    }

    @Override
    public void onMessage(Message message) {
        EventMessage eventMessage = fromByteArray(message.getBody());
        for (EventListener listener : getMembers()) {
            listener.handle(eventMessage);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation returns a real-time view on the actual members, which changes when members join or leave the
     * cluster. Iterators created from the returned set are thread-safe and iterate over the members available at the
     * time the iterator was created. The iterator does not allow the {@link java.util.Iterator#remove()} method to be
     * invoked.
     */
    @Override
    public Set<EventListener> getMembers() {
        return immutableEventListeners;
    }

    @Override
    public void subscribe(EventListener eventListener) {
        eventListeners.add(eventListener);
    }

    @Override
    public void unsubscribe(EventListener eventListener) {
        eventListeners.remove(eventListener);
    }

    @Override
    public ClusterMetaData getMetaData() {
        return clusterMetaData;
    }

    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    private EventMessage fromByteArray(byte[] payload) {
        try {
            EventMessageReader in = new EventMessageReader(new DataInputStream(new ByteArrayInputStream(payload)),
                                                           serializer);
            return in.readEventMessage();
        } catch (IOException e) {
            throw new EventPublicationFailedException("Failed to serialize an EventMessage", e);
        }
    }

    public void afterPropertiesSet() {
        messageListenerContainer.setMessageListener(this);
        if (serializer == null) {
            serializer = applicationContext.getBean(Serializer.class);
        }
        messageListenerContainer.afterPropertiesSet();
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        // TODO: Make sure a Unit OfWork is started here as well
        messageListenerContainer.setTransactionManager(transactionManager);
    }

    /* Delegating methods */

    public SimpleMessageListenerContainer getMessageListenerContainer() {
        return messageListenerContainer;
    }

    public void setChannelTransacted(boolean transactional) {
        messageListenerContainer.setChannelTransacted(transactional);
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        messageListenerContainer.setConnectionFactory(connectionFactory);
    }

    public void setQueueNames(String... queueName) {
        messageListenerContainer.setQueueNames(queueName);
    }

    public void setQueues(Queue... queues) {
        messageListenerContainer.setQueues(queues);
    }

    public void setExposeListenerChannel(boolean exposeListenerChannel) {
        messageListenerContainer.setExposeListenerChannel(exposeListenerChannel);
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        messageListenerContainer.setErrorHandler(errorHandler);
    }

    public void setAutoStartup(boolean autoStartup) {
        messageListenerContainer.setAutoStartup(autoStartup);
    }

    public boolean isAutoStartup() {
        return messageListenerContainer.isAutoStartup();
    }

    public void setPhase(int phase) {
        messageListenerContainer.setPhase(phase);
    }

    public int getPhase() {
        return messageListenerContainer.getPhase();
    }

    public void setBeanName(String beanName) {
        messageListenerContainer.setBeanName(beanName);
    }

    public void destroy() {
        messageListenerContainer.destroy();
    }

    public void start() {
        messageListenerContainer.start();
    }

    public void stop() {
        messageListenerContainer.stop();
    }

    public void stop(Runnable callback) {
        messageListenerContainer.stop(callback);
    }

    public boolean isRunning() {
        return messageListenerContainer.isRunning();
    }

    public void setAdviceChain(Advice[] adviceChain) {
        messageListenerContainer.setAdviceChain(adviceChain);
    }

    public void setRecoveryInterval(long recoveryInterval) {
        messageListenerContainer.setRecoveryInterval(recoveryInterval);
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        messageListenerContainer.setConcurrentConsumers(concurrentConsumers);
    }

    public void setReceiveTimeout(long receiveTimeout) {
        messageListenerContainer.setReceiveTimeout(receiveTimeout);
    }

    public void setShutdownTimeout(long shutdownTimeout) {
        messageListenerContainer.setShutdownTimeout(shutdownTimeout);
    }

    public void setTaskExecutor(Executor taskExecutor) {
        messageListenerContainer.setTaskExecutor(taskExecutor);
    }

    public void setPrefetchCount(int prefetchCount) {
        messageListenerContainer.setPrefetchCount(prefetchCount);
    }

    public void setTxSize(int txSize) {
        messageListenerContainer.setTxSize(txSize);
    }

    public void setTransactionAttribute(TransactionAttribute transactionAttribute) {
        messageListenerContainer.setTransactionAttribute(transactionAttribute);
    }

    public void setMessagePropertiesConverter(MessagePropertiesConverter messagePropertiesConverter) {
        messageListenerContainer.setMessagePropertiesConverter(messagePropertiesConverter);
    }

    public void setAcknowledgeMode(AcknowledgeMode acknowledgeMode) {
        messageListenerContainer.setAcknowledgeMode(acknowledgeMode);
    }
}
