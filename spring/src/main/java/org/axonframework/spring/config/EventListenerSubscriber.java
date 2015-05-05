package org.axonframework.spring.config;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.spring.config.eventhandling.ClusterSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;
import java.util.Map;

/**
 * @author Allard Buijze
 */
public class EventListenerSubscriber implements ApplicationContextAware, SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(EventListenerSubscriber.class);

    private ApplicationContext applicationContext;
    private boolean started;
    private Cluster defaultCluster = new SimpleCluster("defaultCluster");
    private Collection<EventListener> eventListeners;
    private EventBus eventBus;
    private boolean subscribeListenersToCluster = true;
    private boolean subscribeClustersToEventBus = true;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setDefaultCluster(Cluster defaultCluster) {
        this.defaultCluster = defaultCluster;
    }

    public void setEventListeners(Collection<EventListener> eventListeners) {
        this.eventListeners = eventListeners;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void setSubscribeListenersToCluster(boolean subscribeListenersToCluster) {
        this.subscribeListenersToCluster = subscribeListenersToCluster;
    }

    public void setSubscribeClustersToEventBus(boolean subscribeClustersToEventBus) {
        this.subscribeClustersToEventBus = subscribeClustersToEventBus;
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
    public void start() {
        if (eventBus == null && applicationContext.getBeansOfType(EventBus.class).isEmpty()) {
            logger.info("No EventBus present in application context. Not subscribing handlers.");
            return;
        }
        if (eventBus == null) {
            eventBus = applicationContext.getBean(EventBus.class);
        }

        if (subscribeListenersToCluster) {
            if (eventListeners == null) {
                eventListeners = applicationContext.getBeansOfType(EventListener.class).values();
            }
            eventListeners.forEach(listener -> selectCluster(listener).subscribe(listener));
        }

        if (subscribeClustersToEventBus) {
            applicationContext.getBeansOfType(Cluster.class).values().forEach(eventBus::subscribe);
        }
        this.started = true;
    }

    private Cluster selectCluster(EventListener bean) {
        Map<String, ClusterSelector> clusterSelectors = applicationContext.getBeansOfType(ClusterSelector.class);
        if (clusterSelectors.isEmpty()) {
            Map<String, Cluster> clusters = applicationContext.getBeansOfType(Cluster.class);
            if (clusters.isEmpty()) {
                eventBus.subscribe(defaultCluster);
                return defaultCluster;
            } else if (clusters.size() == 1) {
                return clusters.values().iterator().next();
            } else {
                throw new AxonConfigurationException(
                        "More than one cluster has been defined, but no selectors have been provided based on "
                                + "which Event Handler can be assigned to a cluster");
            }
        } else {
            // TODO: Order the selectors
            for (ClusterSelector selector : clusterSelectors.values()) {
                Cluster cluster = selector.selectCluster(bean);
                if (cluster != null) {
                    return cluster;
                }
            }
        }
        throw new AxonConfigurationException("No cluster could be selected for bean: " + bean.getClass().getName());
    }


    @Override
    public void stop() {
        this.started = false;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE / 2;
    }
}
