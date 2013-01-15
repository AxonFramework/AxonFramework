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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.annotation.AbstractAnnotationHandlerBeanPostProcessor;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
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
public class AnnotationEventListenerBeanPostProcessor extends AbstractAnnotationHandlerBeanPostProcessor {

    private EventBus eventBus;

    @Override
    protected Class<?> getAdapterInterface() {
        return EventListener.class;
    }

    /**
     * Create an AnnotationEventListenerAdapter instance of the given {@code bean}. This adapter will receive all event
     * handler calls to be handled by this bean.
     *
     * @param bean The bean that the EventListenerAdapter has to adapt
     * @return an event handler adapter for the given {@code bean}
     */
    @Override
    protected AnnotationEventListenerAdapter initializeAdapterFor(Object bean) {
        ensureEventBusInitialized();
        return AnnotationEventListenerAdapter.subscribe(bean, eventBus);
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
