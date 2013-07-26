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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.SagaManager;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static org.axonframework.contextsupport.spring.AutowiredBean.createAutowiredBean;

/**
 * Parses the saga-manager element. If that element contains an async child element, processing is forwarded to the
 * AsyncSagaManagerBeanDefinitionParser. Otherwise, the SyncSagaManagerBeanDefinitionParser will process the element.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SagaManagerBeanDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String EVENT_BUS_ATTRIBUTE = "event-bus";

    private final AsyncSagaManagerBeanDefinitionParser async;
    private final SyncSagaManagerBeanDefinitionParser sync;

    private final Map<BeanDefinition, String> eventBusPerSagaManager = Collections.synchronizedMap(new WeakHashMap<BeanDefinition, String>());

    /**
     * Initializes a SagaManagerBeanDefinitionParser.
     */
    SagaManagerBeanDefinitionParser() {
        async = new AsyncSagaManagerBeanDefinitionParser();
        sync = new SyncSagaManagerBeanDefinitionParser();
    }

    @Override
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
        final AbstractBeanDefinition definition;
        if (DomUtils.getChildElementByTagName(element, "async") != null) {
            definition = async.parseInternal(element, parserContext);
        } else {
            definition = sync.parseInternal(element, parserContext);
        }
        if (element.hasAttribute(EVENT_BUS_ATTRIBUTE)) {
            String eventBus = element.getAttribute(EVENT_BUS_ATTRIBUTE);
            eventBusPerSagaManager.put(definition, eventBus);
        }
        return definition;
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {
        super.registerBeanDefinition(definition, registry);
        String eventBus = eventBusPerSagaManager.remove(definition.getBeanDefinition());
        registry.registerBeanDefinition(definition.getBeanName() + "_LifecycleManager",
                                        buildLifecycleManager(definition.getBeanName(), eventBus));
    }

    private BeanDefinition buildLifecycleManager(String sagaManager, String eventBus) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(
                SagaManagerLifecycleManager.class);
        if (eventBus != null) {
            builder.addPropertyReference("eventBus", eventBus);
        } else {
            builder = builder.addPropertyValue("eventBus", createAutowiredBean(EventBus.class));
        }
        return builder
                .addPropertyReference("sagaManager", sagaManager)
                .getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }

    public static class SagaManagerLifecycleManager implements SmartLifecycle {

        private volatile EventBus eventBus;
        private volatile SagaManager sagaManager;
        private volatile boolean started = false;

        @Override
        public void start() {
            eventBus.subscribe(sagaManager);
            started = true;
        }

        @Override
        public void stop() {
            eventBus.unsubscribe(sagaManager);
            started = false;
        }

        @Override
        public boolean isRunning() {
            return started;
        }

        public void setEventBus(EventBus eventBus) {
            this.eventBus = eventBus;
        }

        public void setSagaManager(SagaManager sagaManager) {
            this.sagaManager = sagaManager;
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public void stop(Runnable callback) {
            stop();
            callback.run();
        }

        @Override
        public int getPhase() {
            return 0;
        }
    }
}
