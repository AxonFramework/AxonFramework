/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.eventhandling.amqp.spring.SpringAMQPConsumerConfiguration;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * BeanDefinitionParser that parses AMQP Configuration elements into SpringAMQPConsumerConfiguration Bean Definitions.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AMQPConfigurationBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String ATTRIBUTE_ACKNOWLEDGE = "acknowledge";
    private static final String PROPERTY_ACKNOWLEDGE_MODE = "acknowledgeMode";

    private static final Map<String, String> VALUE_PROPERTY_MAPPING = new HashMap<String, String>();
    private static final Map<String, String> REF_PROPERTY_MAPPING = new HashMap<String, String>();

    static {
        REF_PROPERTY_MAPPING.put("connection-factory", "connectionFactory");
        REF_PROPERTY_MAPPING.put("executor", "taskExecutor");
        REF_PROPERTY_MAPPING.put("error-handler", "errorHandler");
        REF_PROPERTY_MAPPING.put("transaction-manager", "transactionManager");
        REF_PROPERTY_MAPPING.put("advice-chain", "adviceChain");

        VALUE_PROPERTY_MAPPING.put("concurrency", "concurrentConsumers");
        VALUE_PROPERTY_MAPPING.put("prefetch", "prefetchCount");
        VALUE_PROPERTY_MAPPING.put("transaction-size", "txSize");
        VALUE_PROPERTY_MAPPING.put("recovery-interval", "recoveryInterval");
        VALUE_PROPERTY_MAPPING.put("receive-timeout", "receiveTimeout");
        VALUE_PROPERTY_MAPPING.put("shutdown-timeout", "shutdownTimeout");
        VALUE_PROPERTY_MAPPING.put("exclusive", "exclusive");
        VALUE_PROPERTY_MAPPING.put("queue-name", "queueName");
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        parserContext.isNested();
        final BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(
                SpringAMQPConsumerConfiguration.class);
        for (Map.Entry<String, String> entry : VALUE_PROPERTY_MAPPING.entrySet()) {
            if (element.hasAttribute(entry.getKey())) {
                builder.addPropertyValue(entry.getValue(), element.getAttribute(entry.getKey()));
            }
        }
        for (Map.Entry<String, String> entry : REF_PROPERTY_MAPPING.entrySet()) {
            if (element.hasAttribute(entry.getKey())) {
                builder.addPropertyReference(entry.getValue(), element.getAttribute(entry.getKey()));
            }
        }

        // this one needs upper-casing for Spring to understand
        if (element.hasAttribute(ATTRIBUTE_ACKNOWLEDGE)) {
            builder.addPropertyValue(PROPERTY_ACKNOWLEDGE_MODE,
                                     element.getAttribute(ATTRIBUTE_ACKNOWLEDGE).toUpperCase(Locale.ROOT));
        }
        return builder.getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
