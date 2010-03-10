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
import org.axonframework.core.eventhandler.TransactionManager;
import org.axonframework.core.eventhandler.annotation.AnnotationEventListenerAdapter;
import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link
 * org.axonframework.core.eventhandler.annotation.EventHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class AnnotationEventListenerBeanPostProcessor implements DestructionAwareBeanPostProcessor,
                                                                 ApplicationContextAware,
                                                                 InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationEventListenerBeanPostProcessor.class);

    private final Map<String, AnnotationEventListenerAdapter> managedAdapters = new HashMap<String, AnnotationEventListenerAdapter>();
    private ApplicationContext applicationContext;
    private Executor executor;
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
                managedAdapters.get(beanName).unsubscribe();
            } catch (Exception e) {
                logger.error("An exception occurred while unsubscribing an event listener", e);
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
        AnnotationEventListenerAdapter adapter = new AnnotationEventListenerAdapter(bean, executor, eventBus);

        adapter.subscribe();
        return adapter;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public void afterPropertiesSet() throws Exception {
        // if no EventBus is set, find one in the application context
        if (eventBus == null) {
            Map<String, EventBus> beans = applicationContext.getBeansOfType(EventBus.class);
            if (beans.size() != 1) {
                throw new IllegalStateException("If no specific EventBus is provided, the application context must "
                        + "contain exactly one bean of type EventBus. The current application context has: "
                        + beans.size());
            }
            this.eventBus = beans.entrySet().iterator().next().getValue();
        }
    }

    private Object createAdapterProxy(Class targetClass, Object eventHandler, EventListener adapter) {
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
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the Executor to use when the AnnotationEventListenerBeanPostProcessor encounters event listeners w
     *
     * @param executor the Executor to use for asynchronous event listeners
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
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

    private static final class AdapterInvocationHandler implements InvocationHandler {

        private final EventListener adapter;
        private final Object bean;

        private AdapterInvocationHandler(EventListener adapter, Object bean) {
            this.adapter = adapter;
            this.bean = bean;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            // this is where we test the method
            Class declaringClass = method.getDeclaringClass();
            if (declaringClass.equals(EventListener.class) || declaringClass.equals(TransactionManager.class)) {
                return method.invoke(adapter, arguments);
            }
            return method.invoke(bean, arguments);
        }
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
