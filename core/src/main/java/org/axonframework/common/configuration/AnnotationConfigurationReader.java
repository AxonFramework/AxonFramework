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

package org.axonframework.common.configuration;

import org.axonframework.common.annotation.ParameterResolverFactory;

/**
 * Interface toward the AnnotationConfiguration that provides read-only methods.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface AnnotationConfigurationReader {

    /**
     * Returns the ParameterResolverFactory that has been configured. This factory takes factories configured for
     * parent classes of the one defined in this configuration into consideration.
     *
     * @return the ParameterResolverFactory configured
     */
    ParameterResolverFactory getParameterResolverFactory();
}
