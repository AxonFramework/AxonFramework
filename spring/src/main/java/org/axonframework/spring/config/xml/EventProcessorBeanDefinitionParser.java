/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.config.xml;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.PublishingEventProcessor;
import org.axonframework.eventhandling.SpringAnnotationOrderResolver;
import org.axonframework.spring.config.eventhandling.*;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * BeanDefinitionParser implementation that parses "event-processor" elements. It creates the event processor as well
 * as a selector that uses the criteria defined in the "selectors" sub-element to decide when the event processor must be
 * selected for any given EventListener.
 * <p/>
 * The selector bean is defined using the name [event-processor-id] + "$selector" and implements the {@link Ordered} interface
 * to define it's order relative to other selectors defined in the Spring Context.
 * <p/>
 * If the event processor is defined as "default", another selector bean is defined under the name [event-processor-id] +
 * "$defaultSelector", which also implements the {@link Ordered} interface, forcing it to be evaluated last of all.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventProcessorBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String META_DATA_ELEMENT = "meta-data";

    private static final String SELECTORS_ELEMENT = "selectors";
    private static final String SELECTOR_CLASS_NAME_MATCHES_ELEMENT = "class-name-matches";
    private static final String SELECTOR_PACKAGE_ELEMENT = "package";

    private static final String SELECTOR_ANNOTATION_ELEMENT = "annotation";
    private static final String DEFAULT_SELECTOR_SUFFIX = "$defaultSelector";

    private static final String SELECTOR_SUFFIX = "$selector";
    private static final String PREFIX_ATTRIBUTE = "prefix";
    private static final String PATTERN_ATTRIBUTE = "pattern";
    private static final String DEFAULT_ATTRIBUTE = "default";
    private static final String ORDER_ATTRIBUTE = "order";
    private static final String TYPE_ATTRIBUTE = "type";
    private static final String CHECK_SUPERCLASS_ATTRIBUTE = "check-superclass";

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        Element innerBeanElement = DomUtils.getChildElementByTagName(element,
                                                                     BeanDefinitionParserDelegate.BEAN_ELEMENT);
        AbstractBeanDefinition innerProcessor;
        if (innerBeanElement != null) {
            innerProcessor = parserContext.getDelegate().parseBeanDefinitionElement(innerBeanElement, null, null);
        } else {
            innerProcessor = new GenericBeanDefinition();
            String processorType = element.getAttribute(TYPE_ATTRIBUTE);
            if (StringUtils.hasText(processorType)) {
                innerProcessor.setBeanClassName(processorType);
            } else {
                innerProcessor.setBeanClass(PublishingEventProcessor.class);
            }
            innerProcessor.getConstructorArgumentValues()
                        .addIndexedArgumentValue(0, resolveId(element, innerProcessor, parserContext));
            Element orderedElement = DomUtils.getChildElementByTagName(element, "ordered");
            if (orderedElement != null) {
                innerProcessor.getConstructorArgumentValues().addIndexedArgumentValue(1, parseOrderElement(orderedElement));
            }
        }
        Map metaData = parseMetaData(element, parserContext);

        AbstractBeanDefinition processorBean = new GenericBeanDefinition();
        processorBean.setBeanClass(MetaDataOverridingEventProcessor.class);
        processorBean.getConstructorArgumentValues().addIndexedArgumentValue(0, innerProcessor);
        processorBean.getConstructorArgumentValues().addIndexedArgumentValue(1, metaData);

        String processorId = resolveId(element, processorBean, parserContext);

        boolean hasSelectors = parseEventProcessorSelector(element, parserContext, processorId);
        boolean hasDefault = parseDefaultSelector(element, parserContext, processorId);

        if (!hasSelectors && !hasDefault) {
            throw new AxonConfigurationException(
                    "Event Processor with id '" + processorId
                            + "' is not a default event processor, nor defines any selectors");
        }
        return processorBean;
    }

    private Object parseOrderElement(Element orderedElement) {
        if (orderedElement.hasAttribute("order-resolver-ref")) {
            return new RuntimeBeanReference(orderedElement.getAttribute("order-resolver-ref"));
        } else {
            return BeanDefinitionBuilder.genericBeanDefinition(SpringAnnotationOrderResolver.class).getBeanDefinition();
        }
    }

    private boolean parseEventProcessorSelector(Element element, ParserContext parserContext, String processorId) {
        AbstractBeanDefinition selector = new GenericBeanDefinition();
        selector.setBeanClass(OrderedEventProcessorSelector.class);
        selector.getConstructorArgumentValues().addIndexedArgumentValue(0, element.getAttribute(ORDER_ATTRIBUTE));

        Element selectorsElement = DomUtils.getChildElementByTagName(element, SELECTORS_ELEMENT);
        List<BeanDefinition> selectors = new ManagedList<>();
        if (selectorsElement != null) {
            selectors.addAll(parseSelectors(selectorsElement, processorId));
            parserContext.getRegistry().registerBeanDefinition(processorId + SELECTOR_SUFFIX, selector);
            selector.getConstructorArgumentValues().addIndexedArgumentValue(1, selectors);
        }
        return !selectors.isEmpty();
    }

    private boolean parseDefaultSelector(Element element, ParserContext parserContext, String processorId) {
        final boolean isDefault = Boolean.parseBoolean(element.getAttribute(DEFAULT_ATTRIBUTE));
        if (isDefault) {
            AbstractBeanDefinition defaultSelector = new GenericBeanDefinition();
            defaultSelector.setBeanClass(OrderedEventProcessorSelector.class);
            defaultSelector.getConstructorArgumentValues().addIndexedArgumentValue(0, Ordered.LOWEST_PRECEDENCE);
            ManagedList<BeanDefinition> definitions = new ManagedList<>();
            definitions.add(BeanDefinitionBuilder.genericBeanDefinition(DefaultEventProcessorSelector.class)
                                                 .addConstructorArgReference(processorId)
                                                 .getBeanDefinition());
            defaultSelector.getConstructorArgumentValues().addIndexedArgumentValue(1, definitions);
            parserContext.getRegistry().registerBeanDefinition(processorId + DEFAULT_SELECTOR_SUFFIX, defaultSelector);
        }
        return isDefault;
    }

    private Map parseMetaData(Element element, ParserContext parserContext) {
        Element metaDataElement = DomUtils.getChildElementByTagName(element, META_DATA_ELEMENT);
        if (metaDataElement == null) {
            return new ManagedMap();
        }
        return parserContext.getDelegate().parseMapElement(metaDataElement, null);
    }

    private List<BeanDefinition> parseSelectors(Element selectorsElement, String processorId) {
        List<Element> selectors = DomUtils.getChildElements(selectorsElement);
        ManagedList<BeanDefinition> selectorsList = new ManagedList<>(selectors.size());
        for (Element child : selectors) {
            BeanDefinition definition = parseSelector(child, processorId);
            if (definition != null) {
                selectorsList.add(definition);
            }
        }
        return selectorsList;
    }

    private BeanDefinition parseSelector(Element item, String processorId) {
        String nodeName = item.getLocalName();
        if (SELECTOR_CLASS_NAME_MATCHES_ELEMENT.equals(nodeName)) {
            return BeanDefinitionBuilder.genericBeanDefinition(ClassNamePatternEventProcessorSelector.class)
                                        .addConstructorArgValue(item.getAttribute(PATTERN_ATTRIBUTE))
                                        .addConstructorArgReference(processorId)
                                        .getBeanDefinition();
        } else if (SELECTOR_PACKAGE_ELEMENT.equals(nodeName)) {
            return BeanDefinitionBuilder.genericBeanDefinition(ClassNamePrefixEventProcessorSelector.class)
                                        .addConstructorArgValue(item.getAttribute(PREFIX_ATTRIBUTE))
                                        .addConstructorArgReference(processorId)
                                        .getBeanDefinition();
        } else if (SELECTOR_ANNOTATION_ELEMENT.equals(nodeName)) {
            final BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(
                    AnnotationEventProcessorSelector.class)
                                                                       .addConstructorArgValue(item.getAttribute(
                                                                               TYPE_ATTRIBUTE))
                                                                       .addConstructorArgReference(
                                                                               processorId);
            if (item.hasAttribute(CHECK_SUPERCLASS_ATTRIBUTE)) {
                builder.addConstructorArgValue(item.getAttribute(CHECK_SUPERCLASS_ATTRIBUTE));
            }
            return builder.getBeanDefinition();
        }
        throw new AxonConfigurationException("No Event Processor Selector known for element '" + item.getLocalName() + "'.");
    }

    private static final class MetaDataOverridingEventProcessor implements FactoryBean<EventProcessor> {

        private final EventProcessor delegate;

        @SuppressWarnings("UnusedDeclaration")
        private MetaDataOverridingEventProcessor(EventProcessor delegate) {
            this.delegate = delegate;
        }

        @Override
        public EventProcessor getObject() throws Exception {
            return delegate;
        }

        @Override
        public Class<?> getObjectType() {
            return EventProcessor.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    private static final class OrderedEventProcessorSelector implements Ordered, EventProcessorSelector {

        private final int order;
        private final List<EventProcessorSelector> selectors;

        @SuppressWarnings("UnusedDeclaration")
        private OrderedEventProcessorSelector(int order, List<EventProcessorSelector> selectors) {
            this.order = order;
            this.selectors = new ArrayList<>(selectors);
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public EventProcessor selectEventProcessor(EventListener eventListener) {
            EventProcessor eventProcessor = null;
            Iterator<EventProcessorSelector> selectorIterator = selectors.iterator();
            while (eventProcessor == null && selectorIterator.hasNext()) {
                eventProcessor = selectorIterator.next().selectEventProcessor(eventListener);
            }
            return eventProcessor;
        }
    }

}
