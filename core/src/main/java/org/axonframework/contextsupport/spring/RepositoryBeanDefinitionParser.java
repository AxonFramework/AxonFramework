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

import org.axonframework.domain.AggregateRoot;
import org.axonframework.eventsourcing.GenericEventSourcingRepository;
import org.axonframework.repository.LockingStrategy;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

/**
 * The RepositoryBeanDefinitionParser is responsible for parsing the <code>repository</code> element from the Axon
 * namespace. It creates a {@link BeanDefinition} based on the {@link GenericEventSourcingRepository}.
 *
 * @author Ben Z. Tels
 * @author Allard Buijze
 * @since 0.7
 */
public class RepositoryBeanDefinitionParser extends AbstractBeanDefinitionParser implements BeanDefinitionParser {

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
     * The aggregate root type attribute name.
     */
    private static final String AGGREGATE_ROOT_TYPE_ATTRIBUTE = "aggregate-type";
    /**
     * The aggregate root type attribute name.
     */
    private static final String EVENT_PROCESSORS_ELEMENT = "event-processors";
    private static final String SNAPSHOT_TRIGGER_ELEMENT = "snapshot-trigger";
    private static final String EVENT_PROCESSORS_PROPERTY = "eventProcessors";

    private final SnapshotTriggerBeanDefinitionParser snapshotTriggerParser = new SnapshotTriggerBeanDefinitionParser();

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        GenericBeanDefinition repositoryDefinition = new GenericBeanDefinition();
        repositoryDefinition.setBeanClass(GenericEventSourcingRepository.class);

        parseAggregateRootType(element, repositoryDefinition);
        parseLockingStrategy(element, repositoryDefinition);
        parseReferenceAttribute(element, EVENT_BUS_ATTRIBUTE, "eventBus", repositoryDefinition.getPropertyValues());
        parseReferenceAttribute(element, EVENT_STORE_ATTRIBUTE, "eventStore", repositoryDefinition.getPropertyValues());
        parseReferenceAttribute(element, CONFLICT_RESOLVER_ATTRIBUTE, "conflictResolver",
                                repositoryDefinition.getPropertyValues());
        parseProcessors(element, parserContext, repositoryDefinition);
        return repositoryDefinition;
    }

    @SuppressWarnings({"unchecked"})
    private void parseProcessors(Element element, ParserContext parserContext, GenericBeanDefinition beanDefinition) {
        Element processorsElement = DomUtils.getChildElementByTagName(element, EVENT_PROCESSORS_ELEMENT);
        List<Object> processorsList = new ManagedList<Object>();

        Element snapshotTriggerElement = DomUtils.getChildElementByTagName(element, SNAPSHOT_TRIGGER_ELEMENT);
        if (snapshotTriggerElement != null) {
            processorsList.add(snapshotTriggerParser.parse(snapshotTriggerElement, parserContext));
        }

        if (processorsElement != null) {
            processorsList.addAll(parserContext.getDelegate().parseListElement(processorsElement, beanDefinition));
            beanDefinition.getPropertyValues().add(EVENT_PROCESSORS_PROPERTY, processorsList);
        }
    }

    /**
     * Parse the named reference attribute and make it a property reference value.
     *
     * @param element       The {@link Element} being parsed.
     * @param referenceName The name of the reference attribute.
     * @param propertyName  The name of the property to set the references object to
     * @param properties    The properties of the bean definition
     */
    private void parseReferenceAttribute(Element element, String referenceName, String propertyName,
                                         MutablePropertyValues properties) {
        if (element.hasAttribute(referenceName)) {
            properties.add(propertyName, new RuntimeBeanReference(element.getAttribute(referenceName)));
        }
    }

    /**
     * Parse the {@link LockingStrategy} selection and make it a constructor argument.
     *
     * @param element The {@link Element} being parsed.
     * @param builder The {@link BeanDefinitionBuilder} being used to construct the {@link BeanDefinition}.
     */
    private void parseLockingStrategy(Element element, GenericBeanDefinition builder) {
        if (element.hasAttribute(LOCKING_STRATEGY_ATTRIBUTE)) {
            LockingStrategy strategy = LockingStrategy.valueOf(element.getAttribute(LOCKING_STRATEGY_ATTRIBUTE));
            builder.getConstructorArgumentValues().addGenericArgumentValue(strategy);
        }
    }

    /**
     * Parse the {@link AggregateRoot} type information and make it a constructor argument.
     *
     * @param element The {@link Element} being parsed.
     * @param builder The {@link BeanDefinitionBuilder} being used to construct the {@link BeanDefinition}.
     */
    private void parseAggregateRootType(Element element, GenericBeanDefinition builder) {
        // Mandatory in the XSD
        String aggregateRootTypeString = element.getAttribute(AGGREGATE_ROOT_TYPE_ATTRIBUTE);
        try {
            Class<?> aggregateRootType = Class.forName(aggregateRootTypeString);
            builder.getConstructorArgumentValues().addGenericArgumentValue(aggregateRootType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "No class of name " + aggregateRootTypeString + " was found on the classpath", e);
        }
    }

}
