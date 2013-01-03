/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * Special type of BeanDefinition that references can be use to explicitly define a property as being autowired.
 * Autowiring is only successful if exactly one bean is available of the given type.
 * <p/>
 * Internally, this BeanDefintion creates a FactoryBean definition that loads the class from the application context.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public final class AutowiredBean {

    /**
     * Prevent instantiation of this utility class
     */
    private AutowiredBean() {
    }

    /**
     * Creates an autowired dependency on the given <code>autowiredTypes</code>. A lookup is done on the first type,
     * if no beans of that type are found, the next is evaluated, and so on. If more than one bean of any type is found
     * when it is evaluated, an exception is thrown.
     * <p/>
     * Beans that are not eligible for autowiring (as defined in Spring configuration) are ignored altogether.
     *
     * @param autowiredTypes The types of bean to autowire.
     * @return the bean definition that will inject a bean of the required type
     */
    public static GenericBeanDefinition createAutowiredBean(Class<?>... autowiredTypes) {
        final GenericBeanDefinition autowiredBeanDef = new GenericBeanDefinition();
        autowiredBeanDef.setBeanClass(AutowiredDependencyFactoryBean.class);
        autowiredBeanDef.getConstructorArgumentValues().addGenericArgumentValue(autowiredTypes);
        return autowiredBeanDef;
    }

    /**
     * Creates an autowired dependency on the given <code>autowiredTypes</code>. A lookup is done on the first type,
     * if no beans of that type are found, the next is evaluated, and so on. If more than one bean of any type is found
     * when it is evaluated, an exception is thrown.
     * <p/>
     * If there are no autowire candidates, the given <code>fallback</code> is used to take its place. If
     * <code>fallback</code> is <code>null</code>, an exception will be thrown.
     * <p/>
     * Beans that are not eligible for autowiring (as defined in Spring configuration) are ignored altogether.
     *
     * @param fallback       The bean to select when no autowired dependencies can be found
     * @param autowiredTypes The types of bean to autowire.
     * @return the bean definition that will inject a bean of the required type
     */
    public static GenericBeanDefinition createAutowiredBeanWithFallback(Object fallback, Class<?>... autowiredTypes) {
        final GenericBeanDefinition autowiredBeanDef = new GenericBeanDefinition();
        autowiredBeanDef.setBeanClass(AutowiredDependencyFactoryBean.class);
        autowiredBeanDef.getConstructorArgumentValues().addIndexedArgumentValue(0, fallback);
        autowiredBeanDef.getConstructorArgumentValues().addIndexedArgumentValue(1, autowiredTypes);
        return autowiredBeanDef;
    }
}
