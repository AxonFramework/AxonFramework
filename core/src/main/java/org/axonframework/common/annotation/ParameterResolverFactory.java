/*
 * Copyright (c) 2010-2014. Axon Framework
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

import java.lang.annotation.Annotation;

/**
 * Interface for objects capable of creating Parameter Resolver instances for annotated handler methods. These
 * resolvers provide the parameter values to use, given an incoming {@link org.axonframework.domain.Message}.
 * <p/>
 * One of the implementations is the {@link ClasspathParameterResolverFactory}, which allows application developers to
 * provide custom ParameterResolverFactory implementations using the ServiceLoader mechanism. To do so, place a file
 * called <code>org.axonframework.common.annotation.ParameterResolverFactory</code> in the
 * <code>META-INF/services</code> folder. In this file, place the fully qualified class names of all available
 * implementations.
 * <p/>
 * The factory implementations must be public, non-abstract, have a default public constructor and implement the
 * ParameterResolverFactory interface.
 *
 * @author Allard Buijze
 * @see ClasspathParameterResolverFactory
 * @since 2.1
 */
public interface ParameterResolverFactory {

    /**
     * If available, creates a ParameterResolver instance that can provide a parameter of type
     * <code>parameterType</code> for a given message.
     * <p/>
     * If the ParameterResolverFactory cannot provide a suitable ParameterResolver, returns <code>null</code>.
     *
     * @param memberAnnotations    annotations placed on the member (e.g. method)
     * @param parameterType        the parameter type to find a resolver for
     * @param parameterAnnotations annotations placed on the parameter
     * @return a suitable ParameterResolver, or <code>null</code> if none is found
     */
    ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                     Annotation[] parameterAnnotations);
}
