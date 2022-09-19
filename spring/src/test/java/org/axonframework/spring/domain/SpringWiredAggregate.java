/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.domain;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.Nonnull;

/**
 * @author Allard Buijze
 */
public class SpringWiredAggregate implements ApplicationContextAware {

    private transient ApplicationContext context;
    private transient String springConfiguredName;
    private transient boolean initialized;

    public SpringWiredAggregate() {
    }

    public ApplicationContext getContext() {
        return context;
    }

    public String getSpringConfiguredName() {
        return springConfiguredName;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setSpringConfiguredName(String springConfiguredName) {
        this.springConfiguredName = springConfiguredName;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public void initialize() {
        this.initialized = true;
    }

}
