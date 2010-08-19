/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.eventhandling.SimpleEventBus;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.w3c.dom.Element;

/**
 * The EventBusBeanDefinitionParser is responsible for parsing the <code>eventBus</code> element from the Axon
 * namespace. The paser will create a {@link BeanDefinition} based on a {@link SimpleEventBus}.
 *
 * @author Ben Z. Tels
 * @since 0.7
 */
public class EventBusBeanDefinitionParser extends AbstractSimpleBeanDefinitionParser implements BeanDefinitionParser {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> getBeanClass(Element element) {
        return SimpleEventBus.class;
    }

}
