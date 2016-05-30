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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.CachingEventSourcingRepository;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionValidationException;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

import static org.axonframework.spring.config.AutowiredBean.createAutowiredBean;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;
import static org.springframework.util.StringUtils.hasText;

/**
 * The RepositoryBeanDefinitionParser is responsible for parsing the <code>repository</code> element from the Axon
 * namespace. It creates a {@link org.springframework.beans.factory.config.BeanDefinition} based on the {@link
 * org.axonframework.eventsourcing.EventSourcingRepository}.
 *
 * @author Ben Z. Tels
 * @author Allard Buijze
 * @since 0.7
 */
public class RepositoryBeanDefinitionParser extends AbstractBeanDefinitionParser {

    /**
     * The conflict resolver attribute name.
     */
    private static final String CONFLICT_RESOLVER_ATTRIBUTE = "conflict-resolver";
    /**
     * The event store attribute name.
     */
    private static final String EVENT_STORE_ATTRIBUTE = "event-store";
    /**
     * The event bus attribute name.
     */
    private static final String EVENT_BUS_ATTRIBUTE = "event-bus";
    /**
     * The locking strategy attribute name.
     */
    private static final String LOCKING_STRATEGY_ATTRIBUTE = "locking-strategy";

    /**
     * The lock factory attribute name
     */
    private static final String LOCK_FACTORY_ATTRIBUTE = "lock-factory";
    /**
     * The aggregate root type attribute name.
     */
    private static final String AGGREGATE_ROOT_TYPE_ATTRIBUTE = "aggregate-type";
    /**
     * The aggregate factory attribute name.
     */
    private static final String AGGREGATE_FACTORY_ATTRIBUTE = "aggregate-factory";
    private static final String CACHE_ATTRIBUTE = "cache-ref";

    private static final String EVENT_PROCESSORS_ELEMENT = "event-processors";
    private static final String SNAPSHOT_TRIGGER_ELEMENT = "snapshotter-trigger";

    private static final String EVENT_STREAM_DECORATORS_PROPERTY = "eventStreamDecorators";
    private static final String SNAPSHOTTER_TRIGGER_PROPERTY = "snapshotterTrigger";

    private final SnapshotterTriggerBeanDefinitionParser snapshotterTriggerParser =
            new SnapshotterTriggerBeanDefinitionParser();

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition repositoryDefinition = new GenericBeanDefinition();
        if (element.hasAttribute(CACHE_ATTRIBUTE)) {
            repositoryDefinition.setBeanClass(CachingEventSourcingRepository.class);
        } else {
            repositoryDefinition.setBeanClass(EventSourcingRepository.class);
        }

        parseAggregateFactory(element, repositoryDefinition, parserContext.getRegistry());
        parseEventStore(element, repositoryDefinition);
        parseLockingStrategy(element, repositoryDefinition);
        parseReferenceAttribute(element, EVENT_BUS_ATTRIBUTE, "eventBus", repositoryDefinition.getPropertyValues(),
                                EventBus.class);
        parseReferenceAttribute(element, CONFLICT_RESOLVER_ATTRIBUTE, "conflictResolver",
                                repositoryDefinition.getPropertyValues(), null);
        parseReferenceAttribute(element, CACHE_ATTRIBUTE, "cache", repositoryDefinition.getPropertyValues(), null);
        parseProcessors(element, parserContext, repositoryDefinition);
        return repositoryDefinition;
    }

    private void parseEventStore(Element element, GenericBeanDefinition repositoryDefinition) {
        final Object eventStore;
        if (element.hasAttribute(EVENT_STORE_ATTRIBUTE)) {
            eventStore = new RuntimeBeanReference(element.getAttribute(EVENT_STORE_ATTRIBUTE));
        } else {
            eventStore = createAutowiredBean(EventStore.class);
        }
        repositoryDefinition.getConstructorArgumentValues().addIndexedArgumentValue(1, eventStore);
    }

    @SuppressWarnings({"unchecked"})
    private void parseProcessors(Element element, ParserContext parserContext, GenericBeanDefinition beanDefinition) {
        Element processorsElement = DomUtils.getChildElementByTagName(element, EVENT_PROCESSORS_ELEMENT);

        Element snapshotTriggerElement = DomUtils.getChildElementByTagName(element, SNAPSHOT_TRIGGER_ELEMENT);
        if (snapshotTriggerElement != null) {
            BeanDefinition triggerDefinition = snapshotterTriggerParser.parse(snapshotTriggerElement, parserContext);
            PropertyValue aggregateCache = beanDefinition.getPropertyValues().getPropertyValue("cache");
            if (aggregateCache != null) {
                triggerDefinition.getPropertyValues().add("aggregateCache", aggregateCache.getValue());
            }
            beanDefinition.getPropertyValues().add(SNAPSHOTTER_TRIGGER_PROPERTY, triggerDefinition);
        }

        if (processorsElement != null) {
            List<Object> processorsList = parserContext.getDelegate().parseListElement(processorsElement,
                                                                                       beanDefinition);
            if (!processorsList.isEmpty()) {
                beanDefinition.getPropertyValues().add(EVENT_STREAM_DECORATORS_PROPERTY, processorsList);
            }
        }
    }

    /**
     * Parse the named reference attribute and make it a property reference value.
     *
     * @param element       The {@link Element} being parsed.
     * @param referenceName The name of the reference attribute.
     * @param propertyName  The name of the property to set the references object to
     * @param properties    The properties of the bean definition
     * @param autowiredType An optional class defining the type to autowire. Use <code>null</code> to indicate that no
     *                      autowiring is required.
     */

    private void parseReferenceAttribute(Element element, String referenceName, String propertyName,
                                         MutablePropertyValues properties, Class<?> autowiredType) {
        if (element.hasAttribute(referenceName)) {
            properties.add(propertyName, new RuntimeBeanReference(element.getAttribute(referenceName)));
        } else if (autowiredType != null) {
            properties.add(propertyName, createAutowiredBean(autowiredType));
        }
    }

    /**
     * Parse the {@link LockingStrategy} selection and make it a constructor argument.
     *
     * @param element The {@link org.w3c.dom.Element} being parsed.
     * @param builder The {@link org.springframework.beans.factory.support.BeanDefinitionBuilder} being used to
     *                construct the {@link org.springframework.beans.factory.config.BeanDefinition}.
     */
    private void parseLockingStrategy(Element element, GenericBeanDefinition builder) {
        if (element.hasAttribute(LOCK_FACTORY_ATTRIBUTE)) {
            String LockFactory = element.getAttribute(LOCK_FACTORY_ATTRIBUTE);
            builder.getConstructorArgumentValues().addIndexedArgumentValue(2, new RuntimeBeanReference(LockFactory));
        } else if (element.hasAttribute(LOCKING_STRATEGY_ATTRIBUTE)) {
            LockingStrategy strategy = LockingStrategy.valueOf(element.getAttribute(LOCKING_STRATEGY_ATTRIBUTE));
            GenericBeanDefinition LockFactory = new GenericBeanDefinition();
            LockFactory.setBeanClass(strategy.getLockFactoryType());
            builder.getConstructorArgumentValues().addIndexedArgumentValue(2, LockFactory);
        }
    }

    /**
     * Parse the {@link org.axonframework.domain.AggregateRoot} type information and make it a constructor argument.
     *
     * @param element  The {@link org.w3c.dom.Element} being parsed.
     * @param builder  The {@link org.springframework.beans.factory.support.BeanDefinitionBuilder} being used to
     *                 construct the {@link org.springframework.beans.factory.config.BeanDefinition}.
     * @param registry The registry in which beans are registered
     */
    @SuppressWarnings({"unchecked"})
    private void parseAggregateFactory(Element element, GenericBeanDefinition builder,
                                       BeanDefinitionRegistry registry) {
        Object aggregateFactory;
        String aggregateFactoryRef = element.getAttribute(AGGREGATE_FACTORY_ATTRIBUTE);
        String aggregateType = element.getAttribute(AGGREGATE_ROOT_TYPE_ATTRIBUTE);
        if (hasText(aggregateFactoryRef)) {
            aggregateFactory = new RuntimeBeanReference(aggregateFactoryRef);
        } else if (hasText(aggregateType)) {
            aggregateFactory = genericBeanDefinition(GenericAggregateFactory.class)
                    .addConstructorArgValue(aggregateType)
                    .getBeanDefinition();
        } else {
            throw new BeanDefinitionValidationException(
                    "You must provide either an aggregate-type or an aggregate-factory in each "
                            + "event-sourcing-repository element.");
        }
        builder.getConstructorArgumentValues()
               .addIndexedArgumentValue(0, aggregateFactory);
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }
}
