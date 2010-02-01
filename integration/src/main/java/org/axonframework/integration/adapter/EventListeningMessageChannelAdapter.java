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

package org.axonframework.integration.adapter;

import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventListener;
import org.axonframework.core.eventhandler.EventSequencingPolicy;
import org.axonframework.core.eventhandler.FullConcurrencyPolicy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.integration.core.MessageChannel;
import org.springframework.integration.message.GenericMessage;

/**
 * @author Allard Buijze
 * @since 0.4
 */
public class EventListeningMessageChannelAdapter implements EventListener, InitializingBean {

    private final MessageChannel channel;
    private final EventFilter filter;
    private final EventBus eventBus;

    public EventListeningMessageChannelAdapter(EventBus eventBus, MessageChannel channel) {
        this.eventBus = eventBus;
        this.channel = channel;
        this.filter = new NoFilter();
    }

    public EventListeningMessageChannelAdapter(EventBus eventBus, MessageChannel channel, EventFilter filter) {
        this.channel = channel;
        this.eventBus = eventBus;
        this.filter = filter;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventBus.subscribe(this);
    }

    @Override
    public boolean canHandle(Class<? extends Event> eventType) {
        return filter.accept(eventType);
    }

    @Override
    public void handle(Event event) {
        if (filter.accept(event.getClass())) {
            channel.send(new GenericMessage<Event>(event));
        }
    }

    @Override
    public EventSequencingPolicy getEventSequencingPolicy() {
        return new FullConcurrencyPolicy();
    }

}
