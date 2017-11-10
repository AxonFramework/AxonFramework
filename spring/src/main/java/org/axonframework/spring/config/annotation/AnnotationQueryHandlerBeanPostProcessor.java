package org.axonframework.spring.config.annotation;

import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.queryhandling.AnnotationQueryHandlerAdapter;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.axonframework.spring.config.AbstractAnnotationHandlerBeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Author: marc
 */
public class AnnotationQueryHandlerBeanPostProcessor extends AbstractAnnotationHandlerBeanPostProcessor<QueryHandlerAdapter, AnnotationQueryHandlerAdapter> {
    @Override
    protected Class<?>[] getAdapterInterfaces() {
        return new Class[] {QueryHandlerAdapter.class};
    }

    @Override
    protected boolean isPostProcessingCandidate(Class<?> targetClass) {
        return this.hasQueryHandlerMethod(targetClass);
    }

    private boolean hasQueryHandlerMethod(Class<?> beanClass) {
        AtomicBoolean result = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(beanClass, new HasQueryHandlerAnnotationMethodCallback(result));
        return result.get();
    }

    @Override
    protected AnnotationQueryHandlerAdapter initializeAdapterFor(Object o, ParameterResolverFactory parameterResolverFactory) {
        return new AnnotationQueryHandlerAdapter( o, parameterResolverFactory);
    }

    private class HasQueryHandlerAnnotationMethodCallback implements ReflectionUtils.MethodCallback {
        private final AtomicBoolean result;

        public HasQueryHandlerAnnotationMethodCallback(AtomicBoolean result) {
            this.result = result;
        }

        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if( method.isAnnotationPresent(QueryHandler.class)) result.set(true);
        }
    }
}
