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

package org.axonframework.spring.eventhandling.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.AbstractAnnotationHandlerBeanPostProcessor;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.spring.eventhandling.ClusterSelector;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link EventHandler}
 * annotated methods.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class AnnotationEventListenerBeanPostProcessor
        extends AbstractAnnotationHandlerBeanPostProcessor<EventListener, AnnotationEventListenerAdapter>
        implements SmartLifecycle {

    private final SimpleCluster defaultCluster = new SimpleCluster("defaultCluster");
    private EventBus eventBus;

    @Override
    protected Class<EventListener> getAdapterInterface() {
        return EventListener.class;
    }

    @Override
    protected AnnotationEventListenerAdapter initializeAdapterFor(Object bean,
                                                                  ParameterResolverFactory parameterResolverFactory) {
        return new AnnotationEventListenerAdapter(bean, parameterResolverFactory);
    }

    @Override
    protected boolean isPostProcessingCandidate(Class<?> targetClass) {
        return isNotAggregateRoot(targetClass)
                && isNotEventHandlerSubclass(targetClass)
                && hasEventHandlerMethod(targetClass);
    }

    @SuppressWarnings({"unchecked"})
    private void ensureEventBusInitialized() {
        // if no EventBus is set, find one in the application context
        if (eventBus == null) {
            Map<String, EventBus> beans = getApplicationContext().getBeansOfType(EventBus.class);
            if (beans.size() != 1) {
                throw new IllegalStateException(
                        "If no specific EventBus is provided, the application context must "
                                + "contain exactly one bean of type EventBus. The current application context has: "
                                + beans.size());
            } else {
                this.eventBus = beans.entrySet().iterator().next().getValue();
            }
        }
    }

    @Override
    protected void subscribe(EventListener bean, AnnotationEventListenerAdapter adapter) {
        ensureEventBusInitialized();
        Cluster cluster = selectCluster(bean);
        cluster.subscribe(bean);
        eventBus.subscribe(cluster);
    }

    private Cluster selectCluster(EventListener bean) {
        Map<String, ClusterSelector> clusterSelectors = getApplicationContext().getBeansOfType(ClusterSelector.class);
        if (clusterSelectors.isEmpty()) {
            Map<String, Cluster> clusters = getApplicationContext().getBeansOfType(Cluster.class);
            if (clusters.isEmpty()) {
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
        throw new AxonConfigurationException("No cluster could be selected for bean: "+ bean.getClass().getName());
    }

    @Override
    protected void unsubscribe(EventListener bean, AnnotationEventListenerAdapter adapter) {
        if (eventBus != null) {
            selectCluster(bean).unsubscribe(bean);
        }
    }

    private boolean isNotAggregateRoot(Class<?> targetClass) {
        return !AggregateRoot.class.isAssignableFrom(targetClass);
    }

    private boolean isNotEventHandlerSubclass(Class<?> beanClass) {
        return !EventListener.class.isAssignableFrom(beanClass);
    }

    private boolean hasEventHandlerMethod(Class<?> beanClass) {
        final AtomicBoolean result = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(beanClass, new HasEventHandlerAnnotationMethodCallback(result));
        return result.get();
    }

    /**
     * Sets the event bus to which detected event listeners should be subscribed. If none is provided, the event bus
     * will be automatically detected in the application context.
     *
     * @param eventBus the event bus to subscribe detected event listeners to
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    private static final class HasEventHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {

        private final AtomicBoolean result;

        private HasEventHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if (method.isAnnotationPresent(EventHandler.class)) {
                result.set(true);
            }
        }
    }
}
