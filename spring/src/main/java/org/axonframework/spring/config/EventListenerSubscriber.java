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

package org.axonframework.spring.config;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.PublishingEventProcessor;
import org.axonframework.spring.config.eventhandling.EventProcessorSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;
import java.util.Map;

/**
 * @author Allard Buijze
 */
public class EventListenerSubscriber implements ApplicationContextAware, SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(EventListenerSubscriber.class);

    private ApplicationContext applicationContext;
    private boolean started;

    private EventProcessor defaultEventProcessor = new PublishingEventProcessor("defaultEventProcessor");
    private Collection<EventListener> eventListeners;
    private EventBus eventBus;
    private boolean subscribeListenersToEventProcessor = true;
    private boolean subscribeEventProcessorsToEventBus = true;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setDefaultEventProcessor(EventProcessor defaultEventProcessor) {
        this.defaultEventProcessor = defaultEventProcessor;
    }

    public void setEventListeners(Collection<EventListener> eventListeners) {
        this.eventListeners = eventListeners;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void setSubscribeListenersToEventProcessor(boolean subscribeListenersToEventProcessor) {
        this.subscribeListenersToEventProcessor = subscribeListenersToEventProcessor;
    }

    public void setSubscribeEventProcessorsToEventBus(boolean subscribeEventProcessorsToEventBus) {
        this.subscribeEventProcessorsToEventBus = subscribeEventProcessorsToEventBus;
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
        if (eventBus == null && applicationContext.getBeansOfType(EventBus.class).isEmpty()) {
            logger.info("No EventBus present in application context. Not subscribing handlers.");
            return;
        }
        if (eventBus == null) {
            eventBus = applicationContext.getBean(EventBus.class);
        }

        if (subscribeListenersToEventProcessor) {
            if (eventListeners == null) {
                eventListeners = applicationContext.getBeansOfType(EventListener.class).values();
            }
            eventListeners.forEach(listener -> selectEventProcessor(listener).subscribe(listener));
        }

        if (subscribeEventProcessorsToEventBus) {
            applicationContext.getBeansOfType(EventProcessor.class).values().forEach(
                    (eventProcessor) -> eventBus.subscribe(eventProcessor));
        }
        this.started = true;
    }

    private EventProcessor selectEventProcessor(EventListener bean) {
        Map<String, EventProcessorSelector> eventProcessorSelectors = applicationContext.getBeansOfType(EventProcessorSelector.class);
        if (eventProcessorSelectors.isEmpty()) {
            Map<String, EventProcessor> eventProcessors = applicationContext.getBeansOfType(EventProcessor.class);
            if (eventProcessors.isEmpty()) {
                eventBus.subscribe(defaultEventProcessor);
                return defaultEventProcessor;
            } else if (eventProcessors.size() == 1) {
                return eventProcessors.values().iterator().next();
            } else {
                throw new AxonConfigurationException(
                        "More than one event processor has been defined, but no selectors have been provided based on "
                                + "which Event Handler can be assigned to an event processor");
            }
        } else {
            // TODO: Order the selectors
            for (EventProcessorSelector selector : eventProcessorSelectors.values()) {
                EventProcessor eventProcessor = selector.selectEventProcessor(bean);
                if (eventProcessor != null) {
                    return eventProcessor;
                }
            }
        }
        throw new AxonConfigurationException("No event processor could be selected for bean: " + bean.getClass().getName());
    }


    @Override
    public void stop() {
        this.started = false;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE / 2;
    }
}
