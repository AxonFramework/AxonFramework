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

package org.axonframework.contextsupport.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Arrays;

import static java.lang.String.format;

/**
 * Convenience factory bean that locates a bean of a number of given types. This factory is used to explicitly defined
 * autowired dependencies on a per-property basis.
 * <p/>
 * This factory does not create beans. It merely returns references to beans already existing.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class AutowiredDependencyFactoryBean implements FactoryBean, ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;
    private final Class[] beanTypes;
    private final Object defaultBean;
    private Class<?> actualBeanType;
    private Object actualBean;

    /**
     * Creates a factory bean that automatically resolved to a bean of one of the given <code>beanTypes</code>.
     *
     * @param beanTypes The types of bean to return a reference for
     */
    public AutowiredDependencyFactoryBean(Class<?>... beanTypes) {
        this(null, beanTypes);
    }

    /**
     * Creates a factory bean that automatically resolved to a bean of one of the given <code>beanTypes</code>,
     * reverting to the given <code>defaultBean</code> if no candidates are found.
     *
     * @param defaultBean The bean to return when no autowire candidates are found
     * @param beanTypes   The types of bean to return a reference for
     */
    public AutowiredDependencyFactoryBean(Object defaultBean, Class<?>... beanTypes) {
        this.beanTypes = beanTypes;
        this.defaultBean = defaultBean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object getObject() throws Exception {
        return actualBean;
    }

    @Override
    public Class<?> getObjectType() {
        return actualBeanType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void afterPropertiesSet() throws Exception {
        for (int i = 0; i < beanTypes.length && actualBean == null; i++) {
            actualBean = findAutowireCandidateOfType(beanTypes[i]);
        }
        if (actualBean == null) {
            actualBean = defaultBean;
        }
        if (actualBean == null) {
            throw new IllegalStateException("No autowire candidates have been found. Make sure at least one bean of "
                                                    + "one of the following types is available: "
                                                    + Arrays.toString(beanTypes));
        }
        actualBeanType = actualBean.getClass();
    }

    private Object findAutowireCandidateOfType(Class<?> beanType) {
        String[] candidates = applicationContext.getBeanNamesForType(beanType);
        String primary = null;
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            for (String bean : candidates) {
                final ConfigurableListableBeanFactory clBeanFactory = (ConfigurableListableBeanFactory) beanFactory;
                if (clBeanFactory.containsBeanDefinition(bean) && clBeanFactory.getBeanDefinition(bean).isPrimary()) {
                    if (primary != null) {
                        throw new IllegalStateException(format("More than one bean of type [%s] marked as primary. "
                                                                       + "Found '%s' and '%s'.",
                                                               beanType.getName(), primary, bean));
                    }
                    primary = bean;
                }
            }
        }
        if (primary == null && candidates.length == 1) {
            primary = candidates[0];
        } else if (primary == null && candidates.length > 1) {
            throw new IllegalStateException("More than one bean of type [" + beanType.getName()
                                                    + "] is eligible for autowiring, "
                                                    + "and none of them is marked as primary.");
        }
        return primary == null ? null : applicationContext.getBean(primary);
    }
}
