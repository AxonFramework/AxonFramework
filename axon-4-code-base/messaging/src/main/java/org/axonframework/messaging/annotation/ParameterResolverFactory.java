/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Message;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * Interface for objects capable of creating Parameter Resolver instances for annotated handler methods. These
 * resolvers provide the parameter values to use, given an incoming {@link Message}.
 * <p>
 * One of the implementations is the {@link ClasspathParameterResolverFactory}, which allows application developers to
 * provide custom ParameterResolverFactory implementations using the ServiceLoader mechanism. To do so, place a file
 * called {@code org.axonframework.messaging.annotation.ParameterResolverFactory} in the
 * {@code META-INF/services} folder. In this file, place the fully qualified class names of all available
 * implementations.
 * <p>
 * The factory implementations must be public, non-abstract, have a default public constructor and implement the
 * ParameterResolverFactory interface.
 *
 * @author Allard Buijze
 * @see ClasspathParameterResolverFactory
 * @since 2.1
 */
@FunctionalInterface
public interface ParameterResolverFactory {

    /**
     * If available, creates a ParameterResolver instance that can provide a parameter of type
     * {@code parameterType} for a given message.
     * <p>
     * If the ParameterResolverFactory cannot provide a suitable ParameterResolver, returns {@code null}.
     *
     * @param executable     The executable (constructor or method) to inspect
     * @param parameters     The parameters on the executable to inspect
     * @param parameterIndex The index of the parameter to return a ParameterResolver for
     * @return a suitable ParameterResolver, or {@code null} if none is found
     */
    ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex);
}
