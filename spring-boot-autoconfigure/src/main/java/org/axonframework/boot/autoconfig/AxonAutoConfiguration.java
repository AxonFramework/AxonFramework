/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot.autoconfig;

import org.axonframework.boot.DistributedCommandBusProperties;
import org.axonframework.boot.EventProcessorProperties;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventHandlingConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@Configuration
@AutoConfigureAfter(name = {"org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.axonframework.boot.autoconfig.JpaAutoConfiguration"})
@EnableConfigurationProperties(value = {
        EventProcessorProperties.class,
        DistributedCommandBusProperties.class
})
public class AxonAutoConfiguration implements BeanClassLoaderAware {

    private final EventProcessorProperties eventProcessorProperties;

    private ClassLoader beanClassLoader;

    public AxonAutoConfiguration(EventProcessorProperties eventProcessorProperties) {
        this.eventProcessorProperties = eventProcessorProperties;
    }

    @Bean
    @ConditionalOnMissingBean(Serializer.class)
    public XStreamSerializer serializer() {
        XStreamSerializer xStreamSerializer = new XStreamSerializer();
        xStreamSerializer.getXStream().setClassLoader(beanClassLoader);
        return xStreamSerializer;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationDataProvider messageOriginProvider() {
        return new MessageOriginProvider();
    }

    @Qualifier("eventStore")
    @Bean(name = "eventBus")
    @ConditionalOnMissingBean(EventBus.class)
    @ConditionalOnBean(EventStorageEngine.class)
    public EmbeddedEventStore eventStore(EventStorageEngine storageEngine, AxonConfiguration configuration) {
        return new EmbeddedEventStore(storageEngine, configuration.messageMonitor(EventStore.class, "eventStore"));
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class})
    public SimpleEventBus eventBus(AxonConfiguration configuration) {
        return new SimpleEventBus(Integer.MAX_VALUE, configuration.messageMonitor(EventStore.class, "eventStore"));
    }

    @Autowired
    public void configureEventHandling(EventHandlingConfiguration eventHandlingConfiguration,
                                       ApplicationContext applicationContext) {
        eventProcessorProperties.getProcessors().forEach((k, v) -> {
            if (v.getMode() == EventProcessorProperties.Mode.TRACKING) {
                if (v.getSource() == null) {
                    eventHandlingConfiguration.registerTrackingProcessor(k);
                } else {
                    eventHandlingConfiguration.registerTrackingProcessor(k, c -> applicationContext
                            .getBean(v.getSource(), StreamableMessageSource.class));
                }
            } else {
                if (v.getSource() == null) {
                    eventHandlingConfiguration.registerSubscribingEventProcessor(k);
                } else {
                    eventHandlingConfiguration.registerSubscribingEventProcessor(k, c -> applicationContext
                            .getBean(v.getSource(), SubscribableMessageSource.class));
                }
            }
        });
    }

    @ConditionalOnMissingBean(ignored = {DistributedCommandBus.class}, value = CommandBus.class)
    @Qualifier("localSegment")
    @Bean
    public SimpleCommandBus commandBus(TransactionManager txManager, AxonConfiguration axonConfiguration) {
        SimpleCommandBus commandBus = new SimpleCommandBus(txManager, axonConfiguration.messageMonitor(CommandBus.class, "commandBus"));
        commandBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));
        return commandBus;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

}
