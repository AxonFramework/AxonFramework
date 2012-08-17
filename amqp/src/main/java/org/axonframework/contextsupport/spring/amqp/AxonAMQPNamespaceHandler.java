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

package org.axonframework.contextsupport.spring.amqp;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * The Axon AMQP namespace handler is responsible for parsing the elements of the Axon AMQP namespace and adjusting
 * the Spring context configuration accordingly.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AxonAMQPNamespaceHandler extends NamespaceHandlerSupport {

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        registerBeanDefinitionParser("terminal", new TerminalBeanDefinitionParser());
        registerBeanDefinitionParser("configuration", new AMQPConfigurationBeanDefinitionParser());
    }
}
