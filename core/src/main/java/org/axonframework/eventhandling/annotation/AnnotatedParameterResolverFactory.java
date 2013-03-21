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
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;

import java.lang.annotation.Annotation;

import static org.axonframework.common.CollectionUtils.getAnnotation;

/**
 * ParameterResolverFactory that will supply a parameter resolver when a matching parameterAnnotation is paired
 * with a suitable parameterType.
 *
 * @param <A> The type of annotation to check for
 * @param <P> The type the parameter needs to be assignable from
 * @author Mark Ingram
 * @since 2.1
 */
public class AnnotatedParameterResolverFactory<A,P> extends ParameterResolverFactory {
    private final Class<A> annotationClass;
    private final Class<P> parameterClass;
    private final ParameterResolver<P> resolver;

    public AnnotatedParameterResolverFactory(Class<A> annotationClass, Class<P> parameterClass, ParameterResolver<P> resolver) {
        Assert.notNull(annotationClass, "annotationClass may not be null");
        Assert.notNull(parameterClass, "parameterClass may not be null");
        Assert.notNull(resolver, "resolver may not be null");
        this.annotationClass = annotationClass;
        this.parameterClass = parameterClass;
        this.resolver = resolver;
    }


    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType, Annotation[] parameterAnnotations) {
        A annotation = getAnnotation(parameterAnnotations, annotationClass);
        if (parameterType.isAssignableFrom(parameterClass) && annotation != null) {
            return resolver;
        }
        return null;
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }
}
