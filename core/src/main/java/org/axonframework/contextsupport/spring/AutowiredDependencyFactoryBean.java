/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Convenience factory bean that locates a bean of the given type. This factory is used to explicitly defined autowired
 * dependencies on a per-property basis.
 * <p/>
 * This factory does not create beans. It merely returns references to beans already existing.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class AutowiredDependencyFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;
    private final Class<T> beanType;
    private T resolvedDependency;

    /**
     * Creates a factory bean that automatically resolved to a bean of the give <code>beanType</code>.
     *
     * @param beanType The type of bean to return a reference for
     */
    public AutowiredDependencyFactoryBean(Class<T> beanType) {
        this.beanType = beanType;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public T getObject() throws Exception {
        return resolvedDependency;
    }

    @Override
    public Class<?> getObjectType() {
        return beanType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        resolvedDependency = applicationContext.getBean(beanType);
    }
}
