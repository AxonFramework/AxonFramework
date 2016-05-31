package org.axonframework.spring.config.xml;

import org.axonframework.commandhandling.disruptor.DisruptorCommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.Assert;
import org.axonframework.common.caching.WeakReferenceCache;
import org.axonframework.eventsourcing.*;
import org.axonframework.spring.config.annotation.SpringContextParameterResolverFactoryBuilder;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.spring.config.AutowiredBean.createAutowiredBean;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

/**
 * Bean Definition parsers that parses disruptor-repository elements. These are configurations of repositories to be
 * used by the disruptor command bus.
 *
 * @author Allard Buijze
 */
public class DisruptorRepositoryBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String ATTRIBUTE_AGGREGATE_FACTORY = "aggregate-factory";
    private static final String ATTRIBUTE_AGGREGATE_TYPE = "aggregate-type";
    private static final String EVENT_PROCESSORS_ELEMENT = "event-processors";
    private static final String SNAPSHOT_TRIGGER_ELEMENT = "snapshotter-trigger";
    private static final String EVENT_STREAM_DECORATORS_PROPERTY = "eventStreamDecorators";
    private static final String SNAPSHOTTER_TRIGGER_PROPERTY = "snapshotterTrigger";
    private final SnapshotterTriggerBeanDefinitionParser snapshotterTriggerParser =
            new SnapshotterTriggerBeanDefinitionParser();

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        String commandBusId = null;
        if (element.hasAttribute("command-bus")) {
            commandBusId = element.getAttribute("command-bus");
        }

        PropertyValue aggregateCache = null;
        if (element.hasAttribute("cache")) {
            aggregateCache = new PropertyValue("cache", new RuntimeBeanReference(element.getAttribute("cache")));
        }

        return createRepositoryDefinition(element, commandBusId, parserContext, aggregateCache);
    }


    public AbstractBeanDefinition createRepositoryDefinition(Element repositoryElement, String commandBusId,
                                                             ParserContext parserContext,
                                                             PropertyValue aggregateCache) {

        BeanDefinitionBuilder definitionBuilder =
                genericBeanDefinition(RepositoryFactoryBean.class);

        if (commandBusId == null) {
            definitionBuilder = definitionBuilder.addPropertyValue("commandBus",
                                                                   createAutowiredBean(DisruptorCommandBus.class));
        } else {
            definitionBuilder = definitionBuilder.addPropertyReference("commandBus", commandBusId);
        }
        if (repositoryElement.hasAttribute(ATTRIBUTE_AGGREGATE_FACTORY)) {
            definitionBuilder = definitionBuilder
                    .addPropertyReference("aggregateFactory",
                                          repositoryElement.getAttribute(ATTRIBUTE_AGGREGATE_FACTORY));
        } else {
            final String aggregateType = repositoryElement.getAttribute(ATTRIBUTE_AGGREGATE_TYPE);
            Assert.notEmpty(aggregateType, "Either one of 'aggregate-type' or 'aggregate-factory' attributes must be "
                    + "set on repository elements in <disruptor-command-bus>");
            definitionBuilder = definitionBuilder
                    .addPropertyValue("aggregateFactory", genericBeanDefinition(GenericAggregateFactory.class)
                            .addConstructorArgValue(aggregateType)
                            .addConstructorArgValue(SpringContextParameterResolverFactoryBuilder
                                                            .getBeanReference(parserContext.getRegistry()))
                            .getBeanDefinition());
        }

        Element processorsElement = DomUtils.getChildElementByTagName(repositoryElement, EVENT_PROCESSORS_ELEMENT);

        Element snapshotTriggerElement = DomUtils.getChildElementByTagName(repositoryElement, SNAPSHOT_TRIGGER_ELEMENT);
        if (snapshotTriggerElement != null) {
            BeanDefinition triggerDefinition = snapshotterTriggerParser.parse(snapshotTriggerElement, parserContext);
            if (aggregateCache != null && aggregateCache.getValue() != null) {
                triggerDefinition.getPropertyValues().add("aggregateCache", aggregateCache.getValue());
            } else {
                // the DisruptorCommandBus uses an internal cache. Not defining any cache on the snapshotter trigger
                // would lead to undesired side-effects (mainly missing triggers).
                triggerDefinition.getPropertyValues().add("aggregateCache", BeanDefinitionBuilder
                        .genericBeanDefinition(WeakReferenceCache.class).getBeanDefinition());
            }
            definitionBuilder = definitionBuilder.addPropertyValue(SNAPSHOTTER_TRIGGER_PROPERTY, triggerDefinition);
        }

        final AbstractBeanDefinition definition = definitionBuilder.getBeanDefinition();
        if (processorsElement != null) {
            //noinspection unchecked
            List<Object> processorsList = parserContext.getDelegate().parseListElement(processorsElement,
                                                                                       definition);
            if (!processorsList.isEmpty()) {
                definition.getPropertyValues().add(EVENT_STREAM_DECORATORS_PROPERTY, processorsList);
            }
        }
        return definition;
    }


    /**
     * FactoryBean to create repository instances for use with the DisruptorCommandBus
     */
    public static class RepositoryFactoryBean implements FactoryBean<Repository> {

        private DisruptorCommandBus commandBus;
        private List<EventStreamDecorator> eventStreamDecorators = new ArrayList<>();
        private AggregateFactory<?> factory;

        @Override
        public Repository getObject() throws Exception {
            if (eventStreamDecorators.isEmpty()) {
                return commandBus.createRepository(factory);
            }
            return commandBus.createRepository(factory, new CompositeEventStreamDecorator(eventStreamDecorators));
        }

        @Override
        public Class<?> getObjectType() {
            return Repository.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        /**
         * The DisruptorCommandBus instance to create the repository from. This is a required property, but cannot be
         * set using constructor injection, as Spring will see it as a circular dependency.
         *
         * @param commandBus DisruptorCommandBus instance to create the repository from
         */
        @Required
        public void setCommandBus(DisruptorCommandBus commandBus) {
            this.commandBus = commandBus;
        }

        /**
         * Sets the aggregate factory used to create instances for the repository to create. This is a required
         * property, but cannot be set using constructor injection, as Spring will see it as a circular dependency.
         *
         * @param factory the aggregate factory used to create instances for the repository to create
         */
        @Required
        public void setAggregateFactory(AggregateFactory<?> factory) {
            this.factory = factory;
        }

        /**
         * Sets the (additional) decorators to use when loading and storing events from/to the Event Store.
         *
         * @param decorators the decorators to decorate event streams with
         */
        @SuppressWarnings("UnusedDeclaration")
        public void setEventStreamDecorators(List<EventStreamDecorator> decorators) {
            eventStreamDecorators.addAll(decorators);
        }

        /**
         * The snapshotter trigger instance that will decide when to trigger a snapshot
         *
         * @param snapshotterTrigger The trigger to configure on the DisruptorCommandBus
         */
        @SuppressWarnings("UnusedDeclaration")
        public void setSnapshotterTrigger(SnapshotterTrigger snapshotterTrigger) {
            eventStreamDecorators.add(snapshotterTrigger);
        }
    }
}
