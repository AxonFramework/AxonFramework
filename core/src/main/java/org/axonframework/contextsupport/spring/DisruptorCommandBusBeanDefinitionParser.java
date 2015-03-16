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

package org.axonframework.contextsupport.spring;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.disruptor.DisruptorConfiguration;
import org.axonframework.common.Assert;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
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
 * BeanDefinitionParser that parses <code>&lt;disruptor-command-bus&gt;</code> elements in the Spring context
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DisruptorCommandBusBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String ATTRIBUTE_EVENT_STORE = "event-store";
    private static final String ATTRIBUTE_EVENT_BUS = "event-bus";
    private static final String ELEMENT_REPOSITORY = "repository";
    private static final String ATTRIBUTE_ID = "id";

    private static final String PROPERTY_WAIT_STRATEGY = "waitStrategy";
    private static final String ATTRIBUTE_WAIT_STRATEGY = "wait-strategy";
    private static final String PROPERTY_PRODUCER_TYPE = "producerType";
    private static final String ATTRIBUTE_PRODUCER_TYPE = "producer-type";
    private static final String ELEMENT_REPOSITORIES = "repositories";

    private static final String ATTRIBUTE_TRANSACTION_MANAGER = "transaction-manager";
    private static final String PROPERTY_TRANSACTION_MANAGER = "transactionManager";

    private static final Map<String, String> VALUE_PROPERTY_MAPPING = new HashMap<>();
    private static final Map<String, String> REF_PROPERTY_MAPPING = new HashMap<>();
    private static final Map<String, String> LIST_PROPERTY_MAPPING = new HashMap<>();

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
        VALUE_PROPERTY_MAPPING.put("buffer-size", "bufferSize");

        LIST_PROPERTY_MAPPING.put("invoker-interceptors", "invokerInterceptors");
        LIST_PROPERTY_MAPPING.put("publisher-interceptors", "publisherInterceptors");
        LIST_PROPERTY_MAPPING.put("dispatcher-interceptors", "dispatchInterceptors");
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        final BeanDefinition configurationDefinition = createConfiguration(element, parserContext);
        final AbstractBeanDefinition definition =
                genericBeanDefinition(DisruptorCommandBus.class)
                        .addConstructorArgReference(element.getAttribute(ATTRIBUTE_EVENT_STORE))
                        .addConstructorArgReference(element.getAttribute(ATTRIBUTE_EVENT_BUS))
                        .addConstructorArgValue(configurationDefinition)
                        .getBeanDefinition();
        Element repoElement = DomUtils.getChildElementByTagName(element, ELEMENT_REPOSITORIES);
        if (repoElement != null) {
            List<Element> repositories = DomUtils.getChildElementsByTagName(repoElement, ELEMENT_REPOSITORY);
            String id = super.resolveId(element, definition, parserContext);
            for (Element repository : repositories) {
                parseRepository(repository, id, parserContext,
                                configurationDefinition.getPropertyValues().getPropertyValue("cache"));
            }
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
        parseProducerType(element, builder);
        parseWaitStrategy(element, builder);
        parseTransactionManager(element, builder);

        for (Map.Entry<String, String> entry : LIST_PROPERTY_MAPPING.entrySet()) {
            final Element interceptorsElement = DomUtils.getChildElementByTagName(element, entry.getKey());
            if (interceptorsElement != null) {
                builder.addPropertyValue(entry.getValue(),
                                         parserContext.getDelegate().parseListElement(interceptorsElement,
                                                                                      builder.getBeanDefinition())
                );
            }
        }
        return builder.getBeanDefinition();
    }

    private void parseTransactionManager(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(ATTRIBUTE_TRANSACTION_MANAGER)) {
            final String txManagerId = element.getAttribute(ATTRIBUTE_TRANSACTION_MANAGER);
            builder.addPropertyValue(PROPERTY_TRANSACTION_MANAGER,
                                     BeanDefinitionBuilder.genericBeanDefinition(TransactionManagerFactoryBean.class)
                                                          .addPropertyReference(PROPERTY_TRANSACTION_MANAGER,
                                                                                txManagerId)
                                                          .getBeanDefinition()
            );
        }
    }

    private void parseProducerType(Element element, BeanDefinitionBuilder builder) {
        final BeanDefinitionBuilder producerType = BeanDefinitionBuilder
                .genericBeanDefinition(ProducerTypeFactoryBean.class);
        if (element.hasAttribute(ATTRIBUTE_PRODUCER_TYPE)) {
            producerType.addPropertyValue("type", element.getAttribute(ATTRIBUTE_PRODUCER_TYPE));
        }
        builder.addPropertyValue(PROPERTY_PRODUCER_TYPE, producerType.getBeanDefinition());
    }

    private void parseWaitStrategy(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(ATTRIBUTE_WAIT_STRATEGY)) {
            String waitStrategy = element.getAttribute(ATTRIBUTE_WAIT_STRATEGY);
            builder.addPropertyValue(PROPERTY_WAIT_STRATEGY,
                                     BeanDefinitionBuilder.genericBeanDefinition(WaitStrategyFactoryBean.class)
                                                          .addConstructorArgValue(waitStrategy)
                                                          .getBeanDefinition()
            );
        }
    }

    private void parseRepository(Element repository, String commandBusId, ParserContext parserContext,
                                 PropertyValue aggregateCache) {
        String id = repository.getAttribute(ATTRIBUTE_ID);
        parserContext.getRegistry().registerBeanDefinition(
                id, new DisruptorRepositoryBeanDefinitionParser()
                        .createRepositoryDefinition(repository, commandBusId, parserContext, aggregateCache));
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }

    /**
     * Factory bean that creates a ProducerType instance.
     */
    private static final class ProducerTypeFactoryBean implements FactoryBean<ProducerType>, InitializingBean {

        private ProducerType producerType;
        private String type;

        @Override
        public ProducerType getObject() throws Exception {
            return producerType;
        }

        @Override
        public Class<?> getObjectType() {
            return ProducerType.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            if ("single-threaded".equals(type)) {
                producerType = ProducerType.SINGLE;
            } else {
                producerType = ProducerType.MULTI;
            }
        }

        /**
         * Sets the name of the producer type to use.
         *
         * @param type the name of the producer type to use
         */
        @SuppressWarnings("UnusedDeclaration")
        public void setType(String type) {
            Assert.isTrue("single-threaded".equals(type) || "multi-threaded".equals(type),
                          "The given value for producer type (" + type
                                  + ") is not valid. It must either be 'single-threaded' or 'multi-threaded'."
            );
            this.type = type;
        }
    }

    private static final class WaitStrategyFactoryBean implements FactoryBean<WaitStrategy> {

        private final WaitStrategy waitStrategy;

        @SuppressWarnings("UnusedDeclaration")
        private WaitStrategyFactoryBean(String strategyName) {
            if ("busy-spin".equals(strategyName)) {
                waitStrategy = new BusySpinWaitStrategy();
            } else if ("yield".equals(strategyName)) {
                waitStrategy = new YieldingWaitStrategy();
            } else if ("sleep".equals(strategyName)) {
                waitStrategy = new SleepingWaitStrategy();
            } else if ("block".equals(strategyName)) {
                waitStrategy = new BlockingWaitStrategy();
            } else {
                throw new IllegalArgumentException("WaitStrategy is not one of the allowed values: "
                                                           + "busy-spin, yield, sleep or block.");
            }
        }

        @Override
        public WaitStrategy getObject() throws Exception {
            return waitStrategy;
        }

        @Override
        public Class<?> getObjectType() {
            return WaitStrategy.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
