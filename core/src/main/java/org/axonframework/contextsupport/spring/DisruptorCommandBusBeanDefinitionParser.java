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

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.MultiThreadedClaimStrategy;
import com.lmax.disruptor.SingleThreadedClaimStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.disruptor.DisruptorConfiguration;
import org.axonframework.common.Assert;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

/**
 * @author Allard Buijze
 */
public class DisruptorCommandBusBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String ATTRIBUTE_EVENT_STORE = "event-store";
    private static final String ATTRIBUTE_EVENT_BUS = "event-bus";
    private static final String ELEMENT_REPOSITORY = "repository";
    private static final String ATTRIBUTE_ID = "id";
    private static final String ATTRIBUTE_AGGREGATE_FACTORY = "aggregate-factory";
    private static final String ATTRIBUTE_AGGREGATE_TYPE = "aggregate-type";
    private static final String METHOD_CREATE_REPOSITORY = "createRepository";

    private static final String PROPERTY_WAIT_STRATEGY = "waitStrategy";
    private static final String ATTRIBUTE_WAIT_STRATEGY = "wait-strategy";
    private static final String PROPERTY_CLAIM_STRATEGY = "claimStrategy";
    private static final String ATTRIBUTE_CLAIM_STRATEGY = "claim-strategy";
    private static final String ATTRIBUTE_BUFFER_SIZE = "buffer-size";
    private static final String ELEMENT_REPOSITORIES = "repositories";

    private static final Map<String, String> VALUE_PROPERTY_MAPPING = new HashMap<String, String>();
    private static final Map<String, String> REF_PROPERTY_MAPPING = new HashMap<String, String>();
    private static final Map<String, String> LIST_PROPERTY_MAPPING = new HashMap<String, String>();

    static {
        REF_PROPERTY_MAPPING.put("cache", "cache");
        REF_PROPERTY_MAPPING.put("executor", "executor");
        REF_PROPERTY_MAPPING.put("rollback-configuration", "rollbackConfiguration");
        REF_PROPERTY_MAPPING.put("serializer", "serializer");
        REF_PROPERTY_MAPPING.put("command-target-resolver", "commandTargetResolver");

        VALUE_PROPERTY_MAPPING.put("cooling-down-period", "coolingDownPeriod");
        VALUE_PROPERTY_MAPPING.put("invoker-threads", "invokerThreadCount");
        VALUE_PROPERTY_MAPPING.put("serializer-threads", "serializerThreadCount");
        VALUE_PROPERTY_MAPPING.put("publisher-threads", "publisherThreadCount");
        VALUE_PROPERTY_MAPPING.put("reschedule-commands-on-corrupt-state", "rescheduleCommandsOnCorruptState");
        VALUE_PROPERTY_MAPPING.put("serialized-representation", "serializedRepresentation");

        LIST_PROPERTY_MAPPING.put("invoker-interceptors", "invokerInterceptors");
        LIST_PROPERTY_MAPPING.put("publisher-interceptors", "publisherInterceptors");
        LIST_PROPERTY_MAPPING.put("dispatcher-interceptors", "dispatchInterceptors");
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        final AbstractBeanDefinition definition =
                genericBeanDefinition(DisruptorCommandBus.class)
                        .addConstructorArgReference(element.getAttribute(ATTRIBUTE_EVENT_STORE))
                        .addConstructorArgReference(element.getAttribute(ATTRIBUTE_EVENT_BUS))
                        .addConstructorArgValue(createConfiguration(element, parserContext))
                        .getBeanDefinition();
        Element reposElement = DomUtils.getChildElementByTagName(element, ELEMENT_REPOSITORIES);
        List<Element> repositories = DomUtils.getChildElementsByTagName(reposElement, ELEMENT_REPOSITORY);
        String id = super.resolveId(element, definition, parserContext);
        for (Element repository : repositories) {
            parseRepository(repository, id, parserContext);
        }
        definition.setDestroyMethodName("stop");
        return definition;
    }

    private BeanDefinition createConfiguration(Element element, ParserContext parserContext) {
        final BeanDefinitionBuilder builder = genericBeanDefinition(DisruptorConfiguration.class);
        for (Map.Entry<String, String> entry : REF_PROPERTY_MAPPING.entrySet()) {
            if (element.hasAttribute(entry.getKey())) {
                builder.addPropertyReference(entry.getValue(), element.getAttribute(entry.getKey()));
            }
        }
        for (Map.Entry<String, String> entry : VALUE_PROPERTY_MAPPING.entrySet()) {
            if (element.hasAttribute(entry.getKey())) {
                builder.addPropertyValue(entry.getValue(), element.getAttribute(entry.getKey()));
            }
        }
        parseClaimStrategy(element, builder);
        parseWaitStrategy(element, builder);

        for (Map.Entry<String, String> entry : LIST_PROPERTY_MAPPING.entrySet()) {
            final Element interceptorsElement = DomUtils.getChildElementByTagName(element, entry.getKey());
            if (interceptorsElement != null) {
                builder.addPropertyValue(entry.getValue(),
                                         parserContext.getDelegate().parseListElement(interceptorsElement,
                                                                                      builder.getBeanDefinition()));
            }

        }
        return builder.getBeanDefinition();
    }

    private void parseClaimStrategy(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(ATTRIBUTE_BUFFER_SIZE) || element.hasAttribute(ATTRIBUTE_CLAIM_STRATEGY)) {
            int bufferSize = DisruptorConfiguration.DEFAULT_BUFFER_SIZE;
            if (element.hasAttribute(ATTRIBUTE_BUFFER_SIZE)) {
                bufferSize = Integer.parseInt(element.getAttribute(ATTRIBUTE_BUFFER_SIZE));
            }
            if (element.hasAttribute(ATTRIBUTE_CLAIM_STRATEGY)
                    && "single-threaded".equals(element.getAttribute(ATTRIBUTE_CLAIM_STRATEGY))) {
                builder.addPropertyValue(PROPERTY_CLAIM_STRATEGY,
                                         new SingleThreadedClaimStrategy(bufferSize));
            } else {
                builder.addPropertyValue(PROPERTY_CLAIM_STRATEGY,
                                         new MultiThreadedClaimStrategy(bufferSize));
            }
        }
    }

    private void parseWaitStrategy(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(ATTRIBUTE_WAIT_STRATEGY)) {
            String waitStrategy = element.getAttribute(ATTRIBUTE_WAIT_STRATEGY);
            if ("busy-spin".equals(waitStrategy)) {
                builder.addPropertyValue(PROPERTY_WAIT_STRATEGY, new BusySpinWaitStrategy());
            } else if ("yield".equals(waitStrategy)) {
                builder.addPropertyValue(PROPERTY_WAIT_STRATEGY, new YieldingWaitStrategy());
            } else if ("sleep".equals(waitStrategy)) {
                builder.addPropertyValue(PROPERTY_WAIT_STRATEGY, new SleepingWaitStrategy());
            } else if ("block".equals(waitStrategy)) {
                builder.addPropertyValue(PROPERTY_WAIT_STRATEGY, new BlockingWaitStrategy());
            } else {
                throw new IllegalArgumentException("WaitStrategy is not one of the allowed values: "
                                                           + "busy-spin, yield, sleep or block.");
            }
        }
    }

    private void parseRepository(Element repository, String commandBusId, ParserContext parserContext) {
        String id = repository.getAttribute(ATTRIBUTE_ID);
        BeanDefinition definition;
        if (repository.hasAttribute(ATTRIBUTE_AGGREGATE_FACTORY)) {
            definition = genericBeanDefinition()
                    .addConstructorArgReference(repository.getAttribute(ATTRIBUTE_AGGREGATE_FACTORY))
                    .getBeanDefinition();
        } else {
            final String aggregateType = repository.getAttribute(ATTRIBUTE_AGGREGATE_TYPE);
            Assert.notNull(aggregateType, "Either one of 'aggregate-type' or 'aggregate-factory' attributes must be "
                    + "set on repository elements in <disruptor-command-bus>");
            definition = genericBeanDefinition()
                    .addConstructorArgValue(genericBeanDefinition(GenericAggregateFactory.class)
                                                    .addConstructorArgValue(aggregateType)
                                                    .getBeanDefinition()
                    ).getBeanDefinition();
        }
        definition.setFactoryBeanName(commandBusId);
        definition.setFactoryMethodName(METHOD_CREATE_REPOSITORY);
        parserContext.getRegistry().registerBeanDefinition(id, definition);
    }
}
