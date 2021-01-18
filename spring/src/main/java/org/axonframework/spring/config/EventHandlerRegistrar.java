/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ModuleConfiguration;
import org.axonframework.spring.config.event.EventHandlersSubscribedEvent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.List;

/**
 * Spring Bean that registers Event Handler beans with the {@link EventProcessingConfigurer}.
 * <p>
 * To customize this behavior, define a Bean of type {@link EventProcessingModule} in the application context:
 * <pre>
 *     &#64;Bean
 *     public EventProcessingModule eventProcessing() {
 *         return new EventProcessingModule();
 *     }
 * </pre>
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class EventHandlerRegistrar implements InitializingBean {

    private final AxonConfiguration axonConfiguration;
    private final EventProcessingConfigurer eventProcessingConfigurer;
    private final ModuleConfiguration eventProcessingConfiguration;
    private final ApplicationEventPublisher eventPublisher;
    private volatile boolean initialized;

    /**
     * Initialize the registrar to register beans discovered with the given {@code eventProcessingConfigurer}. The
     * registrar will also initialize the {@link EventProcessingConfigurer} using the given {@code axonConfiguration}
     * and start it.
     *
     * @param axonConfiguration         the main {@link AxonConfiguration} instance
     * @param eventProcessingConfigurer the {@link EventProcessingConfigurer} used to registered event handling beans
     * @param eventPublisher            a {@link ApplicationEventPublisher} used to signal when all event handlers have
     *                                  been registered
     */
    public EventHandlerRegistrar(AxonConfiguration axonConfiguration,
                                 ModuleConfiguration eventProcessingConfiguration,
                                 EventProcessingConfigurer eventProcessingConfigurer,
                                 ApplicationEventPublisher eventPublisher) {
        this.axonConfiguration = axonConfiguration;
        this.eventProcessingConfiguration = eventProcessingConfiguration;
        this.eventProcessingConfigurer = eventProcessingConfigurer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Registers the given {@code beans} as event handlers with the {@link EventProcessingConfigurer}. The beans are
     * sorted (see {@link AnnotationAwareOrderComparator}) before registering them to the configuration.
     *
     * @param beans the beans to register
     */
    public void setEventHandlers(List<Object> beans) {
        AnnotationAwareOrderComparator.sort(beans);
        beans.forEach(b -> eventProcessingConfigurer.registerEventHandler(c -> b));
        eventPublisher.publishEvent(new EventHandlersSubscribedEvent(this));
    }

    @Override
    public void afterPropertiesSet() {
        if (!initialized) {
            initialized = true;
            eventProcessingConfiguration.initialize(axonConfiguration);
        }
    }
}
