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
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * The RepositoryBeanDefinitionParser is responsible for parsing the
 * <code>repository</code> element from the Axon namespace. It creates a
 * {@link BeanDefinition} based on the {@link GenericEventSourcingRepository}.
 *
 * @author Ben Z. Tels
 */
public class RepositoryBeanDefinitionParser extends AbstractSingleBeanDefinitionParser implements BeanDefinitionParser {

    /** The conflict resolver attribute name. */
    private static final String CONFLICT_RESOLVER_ATTRIBUTE = "conflictResolver";
    /** The event store attribute name. */
    private static final String EVENT_STORE_ATTRIBUTE = "eventStore";
    /** The event bus attribute name. */
    private static final String EVENT_BUS_ATTRIBUTE = "eventBus";
    /** The locking strategy attribute name. */
    private static final String LOCKING_STRATEGY_ATTRIBUTE = "lockingStrategy";
    /** The aggregate root type attribute name. */
    private static final String AGGREGATE_ROOT_TYPE_ATTRIBUTE = "aggregateRootType";

    /** {@inheritDoc} */
    @Override
    protected Class<?> getBeanClass(Element element) {
        return GenericEventSourcingRepository.class;
    }

    /** {@inheritDoc} */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        parseAggregateRootType(element, builder);
        parseLockingStrategy(element, builder);
        parseReferenceAttribute(element, builder, EVENT_BUS_ATTRIBUTE);
        parseReferenceAttribute(element, builder, EVENT_STORE_ATTRIBUTE);
        parseReferenceAttribute(element, builder, CONFLICT_RESOLVER_ATTRIBUTE);
    }

    /**
     * Parse the named reference attribute and make it a property reference value.
     *
     * @param element       The {@link Element} being parsed.
     * @param builder       The {@link BeanDefinitionBuilder} being used to construct the {@link BeanDefinition}.
     * @param referenceName The name of the reference attribute.
     */
    private void parseReferenceAttribute(Element element, BeanDefinitionBuilder builder, String referenceName) {
        if (element.hasAttribute(referenceName)) {
            builder.addPropertyReference(referenceName, element.getAttribute(referenceName));
        }
    }

    /**
     * Parse the {@link LockingStrategy} selection and make it a constructor argument.
     *
     * @param element The {@link Element} being parsed.
     * @param builder The {@link BeanDefinitionBuilder} being used to construct the {@link BeanDefinition}.
     */
    private void parseLockingStrategy(Element element, BeanDefinitionBuilder builder) {
        if (element.hasAttribute(LOCKING_STRATEGY_ATTRIBUTE)) {
            LockingStrategy strategy = LockingStrategy.valueOf(element.getAttribute(LOCKING_STRATEGY_ATTRIBUTE));
            builder.addConstructorArgValue(strategy);
        }
    }

    /**
     * Parse the {@link AggregateRoot} type information and make it a constructor argument.
     *
     * @param element The {@link Element} being parsed.
     * @param builder The {@link BeanDefinitionBuilder} being used to construct the {@link BeanDefinition}.
     */
    private void parseAggregateRootType(Element element, BeanDefinitionBuilder builder) {
        // Mandatory in the XSD
        String aggregateRootTypeString = element.getAttribute(AGGREGATE_ROOT_TYPE_ATTRIBUTE);
        try {
            Class<?> aggregateRootType = Class.forName(aggregateRootTypeString);
            builder.addConstructorArgValue(aggregateRootType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("No class of name " + aggregateRootTypeString + " was found on the classpath");
		}
	}

	
}
