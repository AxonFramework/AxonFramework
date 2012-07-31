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
public class AutowiredBean extends GenericBeanDefinition {

    private static final long serialVersionUID = 3453343985343784967L;

    /**
     * Creates an autowired dependency on the given <code>autowiredType</code>.
     *
     * @param autowiredType The type of bean to autowire.
     */
    public AutowiredBean(Class<?> autowiredType) {
        setBeanClass(AutowiredDependencyFactoryBean.class);
        getConstructorArgumentValues().addGenericArgumentValue(autowiredType);
    }
}
