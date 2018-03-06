/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.eventhandling.saga.metamodel;

import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

/**
 * Default implementation of a {@link SagaMetaModelFactory}.
 *
 * @deprecated in favor of {@link AnnotationSagaMetaModelFactory}.
 */
@Deprecated
public class DefaultSagaMetaModelFactory extends AnnotationSagaMetaModelFactory {

    /**
     * Initializes a {@link DefaultSagaMetaModelFactory} with {@link ClasspathParameterResolverFactory}.
     */
    public DefaultSagaMetaModelFactory() {
    }

    /**
     * Initializes a {@link DefaultSagaMetaModelFactory} with given {@code parameterResolverFactory}.
     *
     * @param parameterResolverFactory factory for event handler parameter resolvers
     */
    public DefaultSagaMetaModelFactory(ParameterResolverFactory parameterResolverFactory) {
        super(parameterResolverFactory);
    }
}
