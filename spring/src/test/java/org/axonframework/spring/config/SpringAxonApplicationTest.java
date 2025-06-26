/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.configuration.ApplicationConfigurerTestSuite;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * Test suite implementation validating the {@link SpringAxonApplication}.
 *
 * @author Steven van Beelen
 */
class SpringAxonApplicationTest extends ApplicationConfigurerTestSuite<SpringAxonApplication> {

    // TODO all lifecycle tests fail - check if we can make this work somehow.

    @Override
    public SpringAxonApplication createConfigurer() {
        ConfigurableListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        SpringComponentRegistry componentRegistry = new SpringComponentRegistry(beanFactory);
        componentRegistry.postProcessBeanFactory(beanFactory);
        SpringLifecycleRegistry lifecycleRegistry = new SpringLifecycleRegistry();
        lifecycleRegistry.setBeanFactory(beanFactory);
        return new SpringAxonApplication(componentRegistry, lifecycleRegistry);
    }

    @Override
    public boolean supportsOverriding() {
        return false;
    }

    @Override
    public boolean supportsComponentFactories() {
        return false;
    }
}