/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler.annotation.postprocessor;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventListener;
import org.axonframework.core.eventhandler.annotation.AnnotationEventListenerAdapter;
import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Allard Buijze
 * @since 0.3
 */
public abstract class BaseAnnotationEventListenerBeanPostProcessor implements DestructionAwareBeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BaseAnnotationEventListenerBeanPostProcessor.class);

    private final Map<String, AnnotationEventListenerAdapter> managedAdapters = new HashMap<String, AnnotationEventListenerAdapter>();
    private EventBus eventBus;

    /**
     * {@inheritDoc}
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        if (isNotEventHandlerSubclass(targetClass) && hasEventHandlerMethod(targetClass)) {
            AnnotationEventListenerAdapter adapter = initializeAdapterFor(bean);
            managedAdapters.put(beanName, adapter);
            return createAdapterProxy(targetClass, bean, adapter);
        }
        return bean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (managedAdapters.containsKey(beanName)) {
            try {
                managedAdapters.get(beanName).shutdown();
            } catch (Exception e) {
                logger.error("An exception occurred while shutting down an AnnotationAdapter.", e);
            } finally {
                managedAdapters.remove(beanName);
            }
        }
    }

    /**
     * Create an AnnotationEventListenerAdapter instance of the given {@code bean}. This adapter will receive all event
     * handler calls to be handled by this bean.
     *
     * @param bean The bean that the EventListenerAdapter has to adapt
     * @return an event handler adapter for the given {@code bean}
     */
    private AnnotationEventListenerAdapter initializeAdapterFor(Object bean) {
        AnnotationEventListenerAdapter adapter = adapt(bean);
        adapter.setEventBus(eventBus);
        try {
            adapter.initialize();
        } catch (Exception e) {
            String message = String.format("An error occurred while wrapping an event listener: [%s]",
                                           bean.getClass().getSimpleName());
            logger.error(message, e);
            throw new EventListenerAdapterException(message, e);
        }
        return adapter;
    }

    /**
     * Instantiate and perform implementation specific initialisation an event listener adapter for the given bean
     *
     * @param bean The bean for which to create an adapter
     * @return the adapter for the given bean
     */
    protected abstract AnnotationEventListenerAdapter adapt(Object bean);

    private Object createAdapterProxy(Class targetClass, Object eventHandler, AnnotationEventListenerAdapter adapter) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setClassLoader(targetClass.getClassLoader());
        enhancer.setInterfaces(new Class[]{EventListener.class});
        enhancer.setCallback(new AdapterInvocationHandler(adapter, eventHandler));
        enhancer.setNamingPolicy(new AxonNamingPolicy());
        return enhancer.create();
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
     * Sets the event bus to which the event listeners should be registered to upon creation. If none is explicitly
     * provided, the event bus will be injected from the application context. This will only work if a single {@link
     * org.axonframework.core.eventhandler.EventBus} instance is registered in the application context.
     *
     * @param eventBus the event bus to register event listeners to
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    private static final class AdapterInvocationHandler implements InvocationHandler {

        private final AnnotationEventListenerAdapter adapter;
        private final Object bean;

        private AdapterInvocationHandler(AnnotationEventListenerAdapter adapter, Object bean) {
            this.adapter = adapter;
            this.bean = bean;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            // this is where we test the method
            Class declaringClass = method.getDeclaringClass();
            if (declaringClass.equals(EventListener.class)) {
                return method.invoke(adapter, arguments);
            }
            return method.invoke(bean, arguments);
        }
    }

    private static class HasEventHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {

        private final AtomicBoolean result;

        public HasEventHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if (method.isAnnotationPresent(EventHandler.class)) {
                result.set(true);
            }
        }
    }
}
