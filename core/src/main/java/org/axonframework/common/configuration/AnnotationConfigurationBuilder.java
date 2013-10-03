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
 * Interface towards the AnnotationConfiguration that provides method to change the configuration. Instances of this
 * configuration can be obtained using {@link AnnotationConfiguration#configure(Class)} and {@link
 * org.axonframework.common.configuration.AnnotationConfiguration#defaultConfiguration()}.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface AnnotationConfigurationBuilder {

    /**
     * Configures the given <code>additionalFactory</code> to be used to resolve parameters. This factory is only used
     * by annotated types that extend from the type provided when loading this configuration.
     *
     * @param additionalFactory The ParameterResolverFactory to use in this configuration
     * @return the builder for method chaining
     */
    AnnotationConfigurationBuilder useParameterResolverFactory(ParameterResolverFactory additionalFactory);

    /**
     * Resets any customizations made to this configuration.
     *
     * @return the builder for method chaining
     */
    AnnotationConfigurationBuilder reset();
}
