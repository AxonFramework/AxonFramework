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

import org.axonframework.common.Assert;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;

import java.lang.annotation.Annotation;

import static org.axonframework.common.CollectionUtils.getAnnotation;

/**
 * ParameterResolverFactory that will supply a parameter resolver when a matching parameter annotation is paired
 * with a suitable type of parameter.
 *
 * Handling is in place to ensure that primitive parameter types will be resolved correctly from their respective
 * wrapper types.
 *
 * @param <A> The type of annotation to check for
 * @param <P> The type the parameter needs to be assignable from.
 * @author Mark Ingram
 * @since 2.1
 */
public abstract class AbstractAnnotatedParameterResolverFactory<A,P> extends ParameterResolverFactory {
    private final Class<A> annotationType;
    private final Class<P> parameterType;

    /**
     * Initialize a ParameterResolverFactory instance that resolves parameters of type <code>parameterType</code>
     * annotated with the given <code>annotationType</code>.
     *
     * @param annotationType the type of annotation that a prospective parameter should declare
     * @param parameterType the type that the parameter value should be assignable from
     */
    protected AbstractAnnotatedParameterResolverFactory(Class<A> annotationType, Class<P> parameterType) {
        Assert.notNull(annotationType, "annotationType may not be null");
        Assert.notNull(parameterType, "parameterType may not be null");
        this.annotationType = annotationType;
        this.parameterType = parameterType;
    }

    /**
     * @return the parameter resolver that is supplied when a matching parameter is located
     */
    protected abstract ParameterResolver<P> getResolver();

    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType, Annotation[] parameterAnnotations) {
        A annotation = getAnnotation(parameterAnnotations, annotationType);
        if (annotation != null) {
            if (parameterType.isAssignableFrom(this.parameterType)) {
                return getResolver();
            }

            //a 2nd chance to resolve if the parameter is primitive but its boxed wrapper type is assignable
            if (parameterType.isPrimitive() &&
                    ReflectionUtils.resolvePrimitiveWrapperType(parameterType).isAssignableFrom(this.parameterType)) {
                return getResolver();
            }
        }

        return null;
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }
}
