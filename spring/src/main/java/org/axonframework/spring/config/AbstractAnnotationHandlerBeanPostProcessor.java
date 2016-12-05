/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.config;

import org.aopalliance.intercept.MethodInvocation;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.springframework.aop.IntroductionInfo;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Abstract bean post processor that finds candidates for proxying. Typically used to wrap annotated beans with their
 * respective interface implementations.
 *
 * @param <I> The primary interface of the adapter being created
 * @param <T> The type of adapter created by this class
 * @author Allard Buijze
 * @since 0.4
 */
public abstract class AbstractAnnotationHandlerBeanPostProcessor<I, T extends I>
        implements BeanPostProcessor, BeanFactoryAware {

    private ParameterResolverFactory parameterResolverFactory;
    private BeanFactory beanFactory;

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
        if (beanFactory.containsBean(beanName) && !beanFactory.isSingleton(beanName)) {
            return bean;
        }

        Class<?> targetClass = bean.getClass();
        final ClassLoader classLoader = targetClass.getClassLoader();
        if (parameterResolverFactory == null) {
            parameterResolverFactory = ClasspathParameterResolverFactory.forClassLoader(classLoader);
        }
        if (isPostProcessingCandidate(targetClass)) {
            T adapter = initializeAdapterFor(bean, parameterResolverFactory);
            return createAdapterProxy(bean, adapter, getAdapterInterfaces(), true, classLoader);
        } else if (!isInstance(bean, getAdapterInterfaces())
                && isPostProcessingCandidate(AopProxyUtils.ultimateTargetClass(bean))) {
            // Java Proxy, find target and inspect that instance
            try {
                Object targetBean = ((Advised) bean).getTargetSource().getTarget();
                // we want to invoke the Java Proxy if possible, so we create a CGLib proxy that does that for us
                Object proxyInvokingBean = createJavaProxyInvoker(bean, targetBean);

                T adapter = initializeAdapterFor(proxyInvokingBean, parameterResolverFactory);
                return createAdapterProxy(proxyInvokingBean, adapter, getAdapterInterfaces(), false,
                                                   classLoader);
            } catch (Exception e) {
                throw new AxonConfigurationException("Unable to wrap annotated handler.", e);
            }
        }
        return bean;
    }

    private boolean isInstance(Object bean, Class<?>[] adapterInterfaces) {
        for (Class<?> adapterInterface : adapterInterfaces) {
            if (adapterInterface.isInstance(bean)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a Proxy that will redirect calls to the {@code javaProxy}, if possible. Alternatively, the
     * {@code target} is invoked.
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
     * definition
     */
    protected abstract Class<?>[] getAdapterInterfaces();

    /**
     * Indicates whether an object of the given {@code targetClass} should be post processed.
     *
     * @param targetClass The type of bean
     * @return true to post process bean of given type, false otherwise
     */
    protected abstract boolean isPostProcessingCandidate(Class<?> targetClass);

    /**
     * Create an AnnotationEventListenerAdapter instance of the given {@code bean}. This adapter will receive all event
     * handler calls to be handled by this bean.
     *
     * @param bean                     The bean that the EventListenerAdapter has to adapt
     * @param parameterResolverFactory The parameter resolver factory that provides the parameter resolvers for the
     *                                 annotated handlers
     * @return an event handler adapter for the given {@code bean}
     */
    protected abstract T initializeAdapterFor(Object bean, ParameterResolverFactory parameterResolverFactory);

    @SuppressWarnings("unchecked")
    private I createAdapterProxy(Object annotatedHandler, final T adapter, final Class<?>[] adapterInterface,
                                 boolean proxyTargetClass, ClassLoader classLoader) {
        ProxyFactory pf = new ProxyFactory(annotatedHandler);
        for (Class<?> iClass : adapterInterface) {
            pf.addAdvice(new AdapterIntroductionInterceptor(adapter, iClass));
            pf.addInterface(iClass);
        }
        pf.setProxyTargetClass(proxyTargetClass);
        pf.setExposeProxy(true);
        return (I) pf.getProxy(classLoader);
    }

    /**
     * Sets the ParameterResolverFactory to create the Parameter Resolvers with that provide the parameter values for
     * the handler methods.
     *
     * @param parameterResolverFactory The parameter resolver factory to resolve parameter values with
     */
    public void setParameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
        this.parameterResolverFactory = parameterResolverFactory;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
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
        public Object invoke(MethodInvocation invocation) throws Exception {
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
                throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
            } catch (Throwable e) {
                if (e instanceof Error) {
                    throw (Error) e;
                }
                throw new InvocationTargetException(e);
            }
        }
    }

    private static final class AdapterIntroductionInterceptor implements IntroductionInfo, IntroductionInterceptor {

        private final Object adapter;
        private final Class<?> adapterInterface;

        private AdapterIntroductionInterceptor(Object adapter, Class<?> adapterInterface) {
            this.adapter = adapter;
            this.adapterInterface = adapterInterface;
        }

        @Override
        public boolean implementsInterface(Class<?> intf) {
            return intf.equals(adapterInterface);
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Exception {
            Class<?> declaringClass = invocation.getMethod().getDeclaringClass();
            try {
                if (declaringClass.isAssignableFrom(adapterInterface)) {
                    return invocation.getMethod().invoke(adapter, invocation.getArguments());
                }
                return invocation.proceed();
            } catch (InvocationTargetException e) {
                throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
            } catch (Throwable e) {
                if (e instanceof Error) {
                    throw (Error) e;
                }
                throw new InvocationTargetException(e);
            }
        }

        @Override
        public Class[] getInterfaces() {
            return new Class[]{adapterInterface};
        }
    }
}
