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

package org.axonframework.common.annotation;

import org.aopalliance.intercept.MethodInvocation;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Subscribable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.IntroductionInfo;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract bean post processor that finds candidates for proxying. Typically used to wrap annotated beans with their
 * respective interface implementations.
 *
 * @author Allard Buijze
 * @since 0.4
 */
public abstract class AbstractAnnotationHandlerBeanPostProcessor
        implements DestructionAwareBeanPostProcessor, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAnnotationHandlerBeanPostProcessor.class);

    private final Map<String, Subscribable> managedAdapters = new HashMap<String, Subscribable>();
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
            Subscribable adapter = initializeAdapterFor(bean);
            managedAdapters.put(beanName, adapter);
            return createAdapterProxy(targetClass, bean, adapter, getAdapterInterface(), true);
        } else if (isPostProcessingCandidate(AopProxyUtils.ultimateTargetClass(bean))) {
            // Java Proxy, find target and inspect that instance
            try {
                Object targetBean = ((Advised) bean).getTargetSource().getTarget();
                // we want to invoke the Java Proxy if possible, so we create a CGLib proxy that does that for us
                Object proxyInvokingBean = createJavaProxyInvoker(bean, targetBean);

                Subscribable adapter = initializeAdapterFor(proxyInvokingBean);
                managedAdapters.put(beanName, adapter);
                return createAdapterProxy(targetClass, proxyInvokingBean, adapter, getAdapterInterface(), false);
            } catch (Exception e) {
                throw new AxonConfigurationException("Unable to wrap annotated handler.", e);
            }
        }
        return bean;
    }

    /**
     * Returns a Proxy that will redirect calls to the <code>javaProxy</code>, if possible. Alternatively, the
     * <code>target</code> is invoked.
     *
     * @param javaProxy The java proxy to invoke, if possible.
     * @param target    The actual implementation to invoke if the javaProxy provides no method implementation
     * @return the proxy that will redirect the invocation to either the Java Proxy, or the target
     */
    private Object createJavaProxyInvoker(Object javaProxy, Object target) {
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(new ProxyOrImplementationInvocationInterceptor(javaProxy, target));
        pf.setProxyTargetClass(true);
        pf.setExposeProxy(true);
        return pf.getProxy(target.getClass().getClassLoader());
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
    protected abstract Subscribable initializeAdapterFor(Object bean);

    private Object createAdapterProxy(Class targetClass, Object annotatedHandler, final Object adapter,
                                      final Class<?> adapterInterface, boolean proxyTargetClass) {
        ProxyFactory pf = new ProxyFactory(annotatedHandler);
        pf.addAdvice(new AdapterIntroductionInterceptor(adapter, adapterInterface));
        pf.addInterface(adapterInterface);
        pf.addInterface(Subscribable.class);
        pf.setProxyTargetClass(proxyTargetClass);
        pf.setExposeProxy(true);
        return pf.getProxy(targetClass.getClassLoader());
    }

    /**
     * Returns the ApplicationContext this Bean Post Processor is registered in.
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

    private static final class ProxyOrImplementationInvocationInterceptor
            implements IntroductionInfo, IntroductionInterceptor {

        private final Object proxy;
        private final Method[] proxyMethods;
        private final Class[] interfaces;

        private ProxyOrImplementationInvocationInterceptor(Object proxy, Object implementation) {
            this.proxy = proxy;
            this.proxyMethods = proxy.getClass().getDeclaredMethods();
            this.interfaces = ClassUtils.getAllInterfaces(implementation);
        }

        @Override
        public boolean implementsInterface(Class<?> intf) {
            for (Class iFace : interfaces) {
                if (intf.equals(iFace)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Class[] getInterfaces() {
            return interfaces;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            try {
                // if the method is declared on the proxy, invoke it there
                for (Method proxyMethod : proxyMethods) {
                    if (proxyMethod.getName().equals(invocation.getMethod().getName())
                            && Arrays.equals(proxyMethod.getParameterTypes(),
                                             invocation.getMethod().getParameterTypes())) {
                        return proxyMethod.invoke(proxy, invocation.getArguments());
                    }
                }
                // otherwise, invoke it on the original object
                return invocation.proceed();
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static final class AdapterIntroductionInterceptor implements IntroductionInfo, IntroductionInterceptor {

        private final Object adapter;
        private Class<?> adapterInterface;

        private AdapterIntroductionInterceptor(Object adapter, Class<?> adapterInterface) {
            this.adapter = adapter;
            this.adapterInterface = adapterInterface;
        }

        @Override
        public boolean implementsInterface(Class<?> intf) {
            return intf.equals(adapterInterface) || Subscribable.class.equals(intf);
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Class<?> declaringClass = invocation.getMethod().getDeclaringClass();
            if (declaringClass.equals(adapterInterface) || Subscribable.class.equals(declaringClass)) {
                try {
                    return invocation.getMethod().invoke(adapter, invocation.getArguments());
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            return invocation.proceed();
        }

        @Override
        public Class[] getInterfaces() {
            return new Class[]{adapterInterface};
        }
    }
}
