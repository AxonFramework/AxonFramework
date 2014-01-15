/*
 * Copyright (c) 2010-2013. Axon Framework
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
import java.util.Arrays;
import java.util.List;

/**
 * ParameterResolverFactory instance that delegates to multiple other instances, in the order provided.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class MultiParameterResolverFactory implements ParameterResolverFactory {

    private final ParameterResolverFactory[] factories;

    /**
     * Initializes an instance that delegates to the given <code>delegates</code>, in the order provided. Changes in
     * the given array are not reflected in the created instance.
     *
     * @param delegates The factories providing the parameter values to use
     */
    public MultiParameterResolverFactory(ParameterResolverFactory... delegates) {
        this.factories = Arrays.copyOf(delegates, delegates.length);
    }

    /**
     * Initializes an instance that delegates to the given <code>delegates</code>, in the order provided. Changes in
     * the given List are not reflected in the created instance.
     *
     * @param delegates The list of factories providing the parameter values to use
     */
    public MultiParameterResolverFactory(List<ParameterResolverFactory> delegates) {
        this.factories = delegates.toArray(new ParameterResolverFactory[delegates.size()]);
    }

    @Override
    public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        for (ParameterResolverFactory factory : factories) {
            ParameterResolver resolver = factory.createInstance(memberAnnotations, parameterType, parameterAnnotations);
            if (resolver != null) {
                return resolver;
            }
        }
        return null;
    }
}
