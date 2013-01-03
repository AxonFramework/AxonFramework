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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.AnnotationClusterSelector;
import org.axonframework.eventhandling.ClassNamePatternClusterSelector;
import org.axonframework.eventhandling.ClassNamePrefixClusterSelector;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.ClusterSelector;
import org.axonframework.eventhandling.DefaultClusterSelector;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.replay.BackloggingIncomingMessageHandler;
import org.axonframework.eventhandling.replay.DiscardingIncomingMessageHandler;
import org.axonframework.eventhandling.replay.IncomingMessageHandler;
import org.axonframework.eventhandling.replay.ReplayingCluster;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.unitofwork.NoTransactionManager;
import org.axonframework.unitofwork.TransactionManager;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBeanWithFallback;

/**
 * BeanDefinitionParser implementation that parses "cluster" elements. It creates the cluster as well as a selector
 * that uses the criteria defined in the "selectors" sub-element to decide when the cluster must be selected for any
 * given EventListener.
 * <p/>
 * The selector bean is defined using the name [cluster-id] + "$selector" and implements the {@link Ordered} interface
 * to define it's order relative to other selectors defined in the Spring Context.
 * <p/>
 * If the cluster is defined as "default", another selector bean is defined under the name [cluster-id] +
 * "$defaultSelector", which also implements the {@link Ordered} interface, forcing it to be evaluated last of all.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ClusterBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String META_DATA_ELEMENT = "meta-data";

    private static final String REPLAY_ELEMENT = "replay-config";
    private static final String TRANSACTION_MANAGER_ATTRIBUTE = "transaction-manager";
    private static final String EVENT_STORE_ATTRIBUTE = "event-store";
    private static final String COMMIT_THRESHOLD_ATTRIBUTE = "commit-threshold";
    private static final String INCOMING_MESSAGE_HANDLER_REF = "incoming-message-handler-ref";
    private static final String INCOMING_MESSAGES_ATTRIBUTE = "incoming-messages";

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

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        Element innerBeanElement = DomUtils.getChildElementByTagName(element,
                                                                     BeanDefinitionParserDelegate.BEAN_ELEMENT);
        AbstractBeanDefinition innerCluster;
        if (innerBeanElement != null) {
            innerCluster = parserContext.getDelegate().parseBeanDefinitionElement(innerBeanElement, null, null);
        } else {
            innerCluster = new GenericBeanDefinition();
            String clusterType = element.getAttribute(TYPE_ATTRIBUTE);
            if (StringUtils.hasText(clusterType)) {
                innerCluster.setBeanClassName(clusterType);
            } else {
                innerCluster.setBeanClass(SimpleCluster.class);
            }
            innerCluster.getConstructorArgumentValues()
                        .addIndexedArgumentValue(0, resolveId(element, innerCluster, parserContext));
        }
        Map metaData = parseMetaData(element, parserContext, null);

        AbstractBeanDefinition clusterBean = new GenericBeanDefinition();
        clusterBean.setBeanClass(MetaDataOverridingCluster.class);
        clusterBean.getConstructorArgumentValues().addIndexedArgumentValue(0, innerCluster);
        clusterBean.getConstructorArgumentValues().addIndexedArgumentValue(1, metaData);

        String clusterId = resolveId(element, clusterBean, parserContext);

        boolean hasSelectors = parseClusterSelector(element, parserContext, clusterId);
        boolean hasDefault = parseDefaultSelector(element, parserContext, clusterId);

        if (!hasSelectors && !hasDefault) {
            throw new AxonConfigurationException(
                    "Cluster with id '" + clusterId + "' is not a default cluster, nor defines any selectors");
        }
        if (DomUtils.getChildElementByTagName(element, REPLAY_ELEMENT) != null) {
            return wrapInReplayingCluster(DomUtils.getChildElementByTagName(element, REPLAY_ELEMENT), clusterBean);
        }
        return clusterBean;
    }

    private AbstractBeanDefinition wrapInReplayingCluster(Element replayElement,
                                                          AbstractBeanDefinition targetClusterDefinition) {
        GenericBeanDefinition replayingCluster = new GenericBeanDefinition();
        replayingCluster.setBeanClass(ReplayingCluster.class);
        final ConstructorArgumentValues constructor = replayingCluster.getConstructorArgumentValues();
        constructor.addIndexedArgumentValue(0, targetClusterDefinition);
        if (replayElement.hasAttribute(EVENT_STORE_ATTRIBUTE)) {
            constructor.addIndexedArgumentValue(1, new RuntimeBeanReference(
                    replayElement.getAttribute(EVENT_STORE_ATTRIBUTE)));
        } else {
            constructor.addIndexedArgumentValue(1, AutowiredBean.createAutowiredBean(EventStoreManagement.class));
        }
        if (replayElement.hasAttribute(TRANSACTION_MANAGER_ATTRIBUTE)) {
            constructor.addIndexedArgumentValue(2, BeanDefinitionBuilder
                    .genericBeanDefinition(TransactionManagerFactoryBean.class)
                    .addPropertyReference("transactionManager",
                                          replayElement.getAttribute(TRANSACTION_MANAGER_ATTRIBUTE))
                    .getBeanDefinition());
        } else {
            constructor.addIndexedArgumentValue(2, BeanDefinitionBuilder
                    .genericBeanDefinition(TransactionManagerFactoryBean.class)
                    .addPropertyValue("transactionManager",
                                      createAutowiredBeanWithFallback(new NoTransactionManager(),
                                                                      TransactionManager.class,
                                                                      PlatformTransactionManager.class))
                    .getBeanDefinition());
        }
        constructor.addIndexedArgumentValue(3, replayElement.getAttribute(COMMIT_THRESHOLD_ATTRIBUTE));
        Object incomingMessageHandlerDefinition;
        if (replayElement.hasAttribute(INCOMING_MESSAGE_HANDLER_REF)) {
            incomingMessageHandlerDefinition = new RuntimeBeanReference(
                    replayElement.getAttribute(INCOMING_MESSAGE_HANDLER_REF));
        } else {
            incomingMessageHandlerDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(IncomingMessageHandlerFactoryBean.class)
                    .addPropertyValue("policy", replayElement.getAttribute(INCOMING_MESSAGES_ATTRIBUTE))
                    .getBeanDefinition();
        }
        constructor.addIndexedArgumentValue(4, incomingMessageHandlerDefinition);
        return replayingCluster;
    }

    private boolean parseClusterSelector(Element element, ParserContext parserContext, String clusterId) {
        AbstractBeanDefinition selector = new GenericBeanDefinition();
        selector.setBeanClass(OrderedClusterSelector.class);
        selector.getConstructorArgumentValues().addIndexedArgumentValue(0, element.getAttribute(ORDER_ATTRIBUTE));

        Element selectorsElement = DomUtils.getChildElementByTagName(element, SELECTORS_ELEMENT);
        List<BeanDefinition> selectors = new ManagedList<BeanDefinition>();
        if (selectorsElement != null) {
            selectors.addAll(parseSelectors(selectorsElement, clusterId));
            parserContext.getRegistry().registerBeanDefinition(clusterId + SELECTOR_SUFFIX, selector);
            selector.getConstructorArgumentValues().addIndexedArgumentValue(1, selectors);
        }
        return !selectors.isEmpty();
    }

    private boolean parseDefaultSelector(Element element, ParserContext parserContext, String clusterId) {
        final boolean isDefault = Boolean.parseBoolean(element.getAttribute(DEFAULT_ATTRIBUTE));
        if (isDefault) {
            AbstractBeanDefinition defaultSelector = new GenericBeanDefinition();
            defaultSelector.setBeanClass(OrderedClusterSelector.class);
            defaultSelector.getConstructorArgumentValues().addIndexedArgumentValue(0, Ordered.LOWEST_PRECEDENCE);
            ManagedList<BeanDefinition> definitions = new ManagedList<BeanDefinition>();
            definitions.add(BeanDefinitionBuilder.genericBeanDefinition(DefaultClusterSelector.class)
                                                 .addConstructorArgReference(clusterId)
                                                 .getBeanDefinition());
            defaultSelector.getConstructorArgumentValues().addIndexedArgumentValue(1, definitions);
            parserContext.getRegistry().registerBeanDefinition(clusterId + DEFAULT_SELECTOR_SUFFIX, defaultSelector);
        }
        return isDefault;
    }

    private Map parseMetaData(Element element, ParserContext parserContext, AbstractBeanDefinition beanDefinition) {
        Element metaDataElement = DomUtils.getChildElementByTagName(element, META_DATA_ELEMENT);
        if (metaDataElement == null) {
            return new ManagedMap();
        }
        return parserContext.getDelegate().parseMapElement(metaDataElement, beanDefinition);
    }

    private List<BeanDefinition> parseSelectors(Element selectorsElement, String clusterId) {
        List<Element> selectors = DomUtils.getChildElements(selectorsElement);
        ManagedList<BeanDefinition> selectorsList = new ManagedList<BeanDefinition>(selectors.size());
        for (Element child : selectors) {
            BeanDefinition definition = parseSelector(child, clusterId);
            if (definition != null) {
                selectorsList.add(definition);
            }
        }
        return selectorsList;
    }

    private BeanDefinition parseSelector(Element item, String clusterId) {
        String nodeName = item.getLocalName();
        if (SELECTOR_CLASS_NAME_MATCHES_ELEMENT.equals(nodeName)) {
            return BeanDefinitionBuilder.genericBeanDefinition(ClassNamePatternClusterSelector.class)
                                        .addConstructorArgValue(item.getAttribute(PATTERN_ATTRIBUTE))
                                        .addConstructorArgReference(clusterId)
                                        .getBeanDefinition();
        } else if (SELECTOR_PACKAGE_ELEMENT.equals(nodeName)) {
            return BeanDefinitionBuilder.genericBeanDefinition(ClassNamePrefixClusterSelector.class)
                                        .addConstructorArgValue(item.getAttribute(PREFIX_ATTRIBUTE))
                                        .addConstructorArgReference(clusterId)
                                        .getBeanDefinition();
        } else if (SELECTOR_ANNOTATION_ELEMENT.equals(nodeName)) {
            return BeanDefinitionBuilder.genericBeanDefinition(AnnotationClusterSelector.class)
                                        .addConstructorArgValue(item.getAttribute(TYPE_ATTRIBUTE))
                                        .addConstructorArgReference(clusterId)
                                        .getBeanDefinition();
        }
        throw new AxonConfigurationException("No Cluster Selector known for element '" + item.getLocalName() + "'.");
    }

    private static final class MetaDataOverridingCluster implements FactoryBean<Cluster> {

        private Cluster delegate;

        @SuppressWarnings("UnusedDeclaration")
        private MetaDataOverridingCluster(Cluster delegate, Map<String, Object> metaData) {
            this.delegate = delegate;
            for (Map.Entry<String, Object> entry : metaData.entrySet()) {
                delegate.getMetaData().setProperty(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public Cluster getObject() throws Exception {
            return delegate;
        }

        @Override
        public Class<?> getObjectType() {
            return Cluster.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    private static final class OrderedClusterSelector implements Ordered, ClusterSelector {

        private int order;
        private List<ClusterSelector> selectors;

        @SuppressWarnings("UnusedDeclaration")
        private OrderedClusterSelector(int order, List<ClusterSelector> selectors) {
            this.order = order;
            this.selectors = new ArrayList<ClusterSelector>(selectors);
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public Cluster selectCluster(EventListener eventListener) {
            Cluster cluster = null;
            Iterator<ClusterSelector> selectorIterator = selectors.iterator();
            while (cluster == null && selectorIterator.hasNext()) {
                cluster = selectorIterator.next().selectCluster(eventListener);
            }
            return cluster;
        }
    }

    private static class IncomingMessageHandlerFactoryBean implements FactoryBean<IncomingMessageHandler>,
            InitializingBean {

        private IncomingMessageHandler messageHandler;
        private String policy = "backlog";

        @Override
        public IncomingMessageHandler getObject() throws Exception {
            return messageHandler;
        }

        @Override
        public Class<?> getObjectType() {
            return IncomingMessageHandler.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            if ("discard".equals(policy)) {
                messageHandler = new DiscardingIncomingMessageHandler();
            } else {
                messageHandler = new BackloggingIncomingMessageHandler();
            }
        }

        public void setPolicy(String policy) {
            this.policy = policy;
        }
    }
}
