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

package org.axonframework.spring.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.config.ModuleConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.List;

/**
 * Spring Bean that registers Event Handler beans with the EventHandlingConfiguration.
 * <p>
 * To customize this behavior, define a Bean of type {@link EventProcessingModule} in the application context:
 * <pre>
 *     &#64;Bean
 *     public EventProcessingModule eventProcessing() {
 *         return new EventProcessingModule();
 *     }
 * </pre>
 */
public class EventHandlerRegistrar implements InitializingBean, SmartLifecycle {

    private static final int EARLY_PHASE = Integer.MIN_VALUE + 1000;
    private final AxonConfiguration axonConfiguration;
    private final EventProcessingConfigurer eventProcessingConfigurer;
    private final ModuleConfiguration eventProcessingConfiguration;
    private volatile boolean running = false;
    private volatile boolean initialized;

    /**
     * Initialize the registrar to register beans discovered with the given {@code eventProcessing}.
     * The registrar will also initialize the EventHandlerConfiguration using the given {@code axonConfiguration}
     * and start it.
     *
     * @param axonConfiguration          The main Axon Configuration instance
     * @param eventProcessingConfigurer The main Axon Configuration
     */
    public EventHandlerRegistrar(AxonConfiguration axonConfiguration,
                                 ModuleConfiguration eventProcessingConfiguration,
                                 EventProcessingConfigurer eventProcessingConfigurer) {
        this.axonConfiguration = axonConfiguration;
        this.eventProcessingConfiguration = eventProcessingConfiguration;
        this.eventProcessingConfigurer = eventProcessingConfigurer;
    }

    /**
     * Registers the given {@code beans} as event handlers with the Event Handler Configuration. The beans are sorted
     * (see {@link AnnotationAwareOrderComparator}) before registering them to the configuration.
     *
     * @param beans the beans to register
     */
    public void setEventHandlers(List<Object> beans) {
        AnnotationAwareOrderComparator.sort(beans);
        beans.forEach(b -> eventProcessingConfigurer.registerEventHandler(c -> b));
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
    public void start() {
        eventProcessingConfiguration.start();
        running = true;
    }

    @Override
    public void stop() {
        eventProcessingConfiguration.shutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return EARLY_PHASE;
    }

    @Override
    public void afterPropertiesSet() {
        if (!initialized) {
            initialized = true;
            eventProcessingConfiguration.initialize(axonConfiguration);
        }
    }
}
