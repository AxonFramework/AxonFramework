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

package org.axonframework.core.util.annotation;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import org.axonframework.core.util.AxonNamingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Allard Buijze
 */
public abstract class AbstractAnnotationHandlerBeanPostProcessor
        implements DestructionAwareBeanPostProcessor, ApplicationContextAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAnnotationHandlerBeanPostProcessor.class);

    private final Map<String, AnnotatedHandlerAdapter> managedAdapters = new HashMap<String, AnnotatedHandlerAdapter>();
    private ApplicationContext applicationContext;

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
        if (isPostProcessingCandidate(targetClass)) {
            AnnotatedHandlerAdapter adapter = initializeAdapterFor(bean);
            managedAdapters.put(beanName, adapter);
            return createAdapterProxy(targetClass, bean, adapter, getAdapterInterface());
        }
        return bean;
    }

    /**
     * Returns the interface that the adapter implements to connect the annotated method to the actual interface
     * definition. Calls to methods declared on this interface are passed to the adapter, while other method calls will
     * be forwarded to the annotated object itself.
     * <p/>
     * Note: this *must* be an interface. It may not be an (abstract) class.
     *
     * @return the interface that the adapter implements to connect the annotated method to the actual interface
     *         definition
     */
    protected abstract Class<?> getAdapterInterface();

    /**
     * Indicates whether an object of the given <code>targetClass</code> should be post processed.
     *
     * @param targetClass The type of bean
     * @return true to post process bean of given type, false otherwise
     */
    protected abstract boolean isPostProcessingCandidate(Class<?> targetClass);

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
    protected abstract AnnotatedHandlerAdapter initializeAdapterFor(Object bean);

    private Object createAdapterProxy(Class targetClass, Object annotatedHandler, Object adapter,
                                      Class<?> adapterInterface) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setClassLoader(targetClass.getClassLoader());
        enhancer.setInterfaces(new Class[]{adapterInterface});
        enhancer.setCallback(new AdapterInvocationHandler(adapter, adapterInterface, annotatedHandler));
        enhancer.setNamingPolicy(new AxonNamingPolicy());
        return enhancer.create();
    }

    /**
     * Returns the ApplicationContext this Bean Post Processor is registered in
     *
     * @return the ApplicationContext this Bean Post Processor is registered in
     */
    protected ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private static final class AdapterInvocationHandler implements InvocationHandler {

        private final Object adapter;
        private final Object bean;
        private final Class<?> adapterInterface;

        private AdapterInvocationHandler(Object adapter, Class<?> adapterInterface, Object bean) {
            this.adapter = adapter;
            this.bean = bean;
            this.adapterInterface = adapterInterface;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            // this is where we test the method
            Class declaringClass = method.getDeclaringClass();
            if (declaringClass.equals(adapterInterface)) {
                return method.invoke(adapter, arguments);
            }
            return method.invoke(bean, arguments);
        }
    }
}
